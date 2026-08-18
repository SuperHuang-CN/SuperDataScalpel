package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;
import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.JdbcUpsertColumn;
import cn.superhuang.data.scalpel.dialect.model.SpatialColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseAccessException;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseInspector;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasPreparedOutput;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDatabaseType;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeJdbcConnection;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.api.java.function.ForeachPartitionFunction;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_constructors;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Controlled PostgreSQL/PostGIS and MySQL 8 Geometry bridge.
 *
 * <p>Native JDBC geometry objects never cross into Spark. Reads use WKB and writes bind WKB to
 * database spatial constructors. All SQL identifiers originate from inspected metadata and are
 * rendered by the shared dialect module.</p>
 */
final class SpatialJdbcRuntimeSupport {
    private static final String INPUT_ALIAS = "__datascalpel_spatial_input";
    private static final int WRITE_BATCH_SIZE = 500;
    private static final DialectRegistry DIALECTS = BuiltInDialects.registry();
    private static final DatabaseInspector INSPECTOR =
            new DatabaseInspector(DIALECTS, new JdbcConnectionFactory());

    private SpatialJdbcRuntimeSupport() {
    }

    static TableIdentifier tableIdentifier(RuntimeDataSource source, String tableName) {
        RuntimeJdbcConnection connection = source.connection();
        return new TableIdentifier(
                connection.catalogName(),
                connection.schemaName(),
                tableName
        );
    }

    static String qualifiedTable(RuntimeDataSource source, TableIdentifier table) {
        return dialect(source).qualifiedName(table);
    }

    static Dataset<Row> readTable(
            SparkSession spark,
            RuntimeDataSource source,
            TableIdentifier table,
            CanvasTableSchema logicalSchema,
            String nodeId
    ) {
        validateSourceTableBoundary(source, table, nodeId);
        boolean containsGeometry = containsGeometry(logicalSchema);
        if (!containsGeometry) {
            return CanvasTaskExecutor.reader(spark, source)
                    .option("dbtable", qualifiedTable(source, table))
                    .load();
        }

        DatabaseDialect dialect = dialect(source);
        List<String> selectExpressions = new ArrayList<>();
        Map<String, String> wkbAliases = new LinkedHashMap<>();
        int geometryIndex = 0;
        for (CanvasColumnSchema column : logicalSchema.columns()) {
            String quotedColumn = dialect.quoteIdentifier(column.name());
            if (column.fieldType() == PlatformDataType.GEOMETRY) {
                String alias = "__datascalpel_wkb_" + geometryIndex++;
                wkbAliases.put(column.name(), alias);
                selectExpressions.add("ST_AsBinary(" + quotedColumn + ") AS "
                        + dialect.quoteIdentifier(alias));
            } else {
                selectExpressions.add(quotedColumn);
            }
        }
        String query = "(SELECT " + String.join(", ", selectExpressions)
                + " FROM " + dialect.qualifiedName(table) + ") AS "
                + dialect.quoteIdentifier(INPUT_ALIAS);
        Dataset<Row> raw = CanvasTaskExecutor.reader(spark, source)
                .option("dbtable", query)
                .load();
        Column[] projected = logicalSchema.columns().stream().map(column -> {
            if (column.fieldType() != PlatformDataType.GEOMETRY) {
                return sparkColumn(column.name());
            }
            Column geometry = st_constructors.ST_GeomFromWKB(
                    sparkColumn(wkbAliases.get(column.name()))
            );
            return st_functions.ST_SetSRID(
                    geometry,
                    functions.lit(column.geometry().crs().code())
            ).as(column.name(), SparkTypeMapper.metadata(column));
        }).toArray(Column[]::new);
        return raw.select(projected);
    }

    private static void validateSourceTableBoundary(
            RuntimeDataSource source,
            TableIdentifier table,
            String nodeId
    ) {
        if (source.databaseType() != RuntimeDatabaseType.TDENGINE_WEBSOCKET
                && source.databaseType() != RuntimeDatabaseType.TDENGINE_RESTFUL) {
            return;
        }
        try {
            TableMetadata metadata = INSPECTOR.readTable(
                    source.databaseType().name(),
                    jdbcSpec(source.connection()),
                    table
            );
            if (!"SUPERTABLE".equalsIgnoreCase(metadata.table().type())) {
                throw new RunnerExecutionException(
                        "TDENGINE_SUPERTABLE_REQUIRED",
                        "TDengine 输入对象不是超级表；子表不在 DataScalpel 支持范围内",
                        nodeId
                );
            }
        } catch (DatabaseAccessException exception) {
            throw new RunnerExecutionException(
                    "TDENGINE_SUPERTABLE_METADATA_UNAVAILABLE",
                    "无法确认 TDengine 输入对象为超级表：" + exception.getMessage(),
                    nodeId,
                    exception
            );
        }
    }

    static Map<String, Integer> resolveGeometryLocalSrids(
            RuntimeDataSource source,
            TableIdentifier table,
            CanvasTableSchema logicalSchema,
            String[] requiredColumnNames,
            String nodeId
    ) {
        Set<String> requiredColumns = new HashSet<>(List.of(requiredColumnNames));
        List<CanvasColumnSchema> geometryColumns = logicalSchema.columns().stream()
                .filter(column -> column.fieldType() == PlatformDataType.GEOMETRY)
                .filter(column -> requiredColumns.contains(column.name()))
                .toList();
        if (geometryColumns.isEmpty()) {
            return Map.of();
        }
        TableMetadata actual;
        try {
            actual = INSPECTOR.readTable(
                    source.databaseType().name(),
                    jdbcSpec(source.connection()),
                    table
            );
        } catch (DatabaseAccessException exception) {
            throw new RunnerExecutionException(
                    "SPATIAL_TARGET_METADATA_UNAVAILABLE",
                    "无法读取目标表 Geometry 元数据",
                    nodeId,
                    exception
            );
        }
        Map<String, ColumnMetadata> physicalColumns = new LinkedHashMap<>();
        actual.columns().forEach(column -> physicalColumns.put(column.name(), column));
        Map<String, Integer> localSrids = new LinkedHashMap<>();
        for (CanvasColumnSchema geometryColumn : geometryColumns) {
            ColumnMetadata physical = physicalColumns.get(geometryColumn.name());
            SpatialColumnMetadata spatial = physical == null ? null : physical.spatial();
            Integer localSrid = spatial == null ? null : spatial.spatialReferenceId();
            if (localSrid == null || localSrid < 1) {
                throw new RunnerExecutionException(
                        "SPATIAL_TARGET_METADATA_UNAVAILABLE",
                        "目标 Geometry 字段缺少数据库本地 SRID：" + geometryColumn.name(),
                        nodeId
                );
            }
            localSrids.put(geometryColumn.name(), localSrid);
        }
        return Map.copyOf(localSrids);
    }

    static boolean requiresSpatialWriter(CanvasPreparedOutput output) {
        return requiresSpatialWriter(output.targetSchema(), output.dataset().schema());
    }

    static boolean requiresSpatialWriter(
            CanvasTableSchema targetSchema,
            StructType projectedSchema
    ) {
        Map<String, CanvasColumnSchema> targetColumns = columnsByName(targetSchema);
        for (StructField field : projectedSchema.fields()) {
            CanvasColumnSchema target = targetColumns.get(field.name());
            if (target != null && target.fieldType() == PlatformDataType.GEOMETRY) {
                return true;
            }
        }
        return false;
    }

    static void writeSpatial(CanvasPreparedOutput output, Dataset<Row> dataset) {
        RuntimeDataSource source = output.runtimeDataSource();
        if (source.databaseType() != RuntimeDatabaseType.POSTGRESQL
                && source.databaseType() != RuntimeDatabaseType.MYSQL) {
            throw new RunnerExecutionException(
                    "SPATIAL_JDBC_UNSUPPORTED",
                    "Geometry 写入只支持 PostgreSQL/PostGIS 和 MySQL 8",
                    output.node().id()
            );
        }
        DatabaseDialect dialect = dialect(source);
        Map<String, CanvasColumnSchema> targetSchemaColumns = columnsByName(output.targetSchema());
        List<JdbcBinding> bindings = new ArrayList<>();
        List<String> targetColumns = new ArrayList<>();
        List<String> valueExpressions = new ArrayList<>();
        List<Column> writableColumns = new ArrayList<>();
        for (StructField field : dataset.schema().fields()) {
            CanvasColumnSchema column = targetSchemaColumns.get(field.name());
            if (column == null) {
                throw new RunnerExecutionException(
                        "OUTPUT_MAPPING_INVALID",
                        "输出数据包含目标表中不存在的字段：" + field.name(),
                        output.node().id()
                );
            }
            boolean geometry = column.fieldType() == PlatformDataType.GEOMETRY;
            Integer localSrid = geometry ? output.geometryLocalSrids().get(column.name()) : null;
            if (geometry && (localSrid == null || localSrid < 1)) {
                throw new RunnerExecutionException(
                        "SPATIAL_TARGET_METADATA_UNAVAILABLE",
                        "目标 Geometry 字段缺少数据库本地 SRID：" + column.name(),
                        output.node().id()
                );
            }
            targetColumns.add(dialect.quoteIdentifier(column.name()));
            valueExpressions.add(geometry ? "ST_GeomFromWKB(?, " + localSrid + ")" : "?");
            bindings.add(new JdbcBinding(column.name(), geometry));
            writableColumns.add(geometry
                    ? st_functions.ST_AsBinary(sparkColumn(column.name())).as(column.name())
                    : sparkColumn(column.name()));
        }
        String insertSql = "INSERT INTO " + output.qualifiedTableName()
                + " (" + String.join(", ", targetColumns) + ") VALUES ("
                + String.join(", ", valueExpressions) + ")";
        RuntimeJdbcConnection connection = source.connection();
        Properties properties = jdbcProperties(connection);
        SpatialWriteSpec writeSpec = new SpatialWriteSpec(
                connection.driverClassName(),
                connection.jdbcUrl(),
                properties,
                insertSql,
                List.copyOf(bindings)
        );
        dataset.select(writableColumns.toArray(Column[]::new))
                .foreachPartition((ForeachPartitionFunction<Row>) writeSpec::write);
    }

    static void validateUpsertKeys(CanvasPreparedOutput output, Dataset<Row> dataset) {
        if (output.upsertKeyColumns().isEmpty()) {
            throw new RunnerExecutionException(
                    "UPSERT_KEY_REQUIRED", "UPSERT Key 不能为空", output.node().id());
        }
        Column nullKey = functions.lit(false);
        for (String key : output.upsertKeyColumns()) {
            nullKey = nullKey.or(sparkColumn(key).isNull());
        }
        if (dataset.filter(nullKey).limit(1).count() > 0) {
            throw new RunnerExecutionException(
                    "UPSERT_KEY_NULL", "UPSERT Key 不能包含 NULL", output.node().id());
        }
        Column[] keys = output.upsertKeyColumns().stream()
                .map(SpatialJdbcRuntimeSupport::sparkColumn)
                .toArray(Column[]::new);
        if (dataset.groupBy(keys).count()
                .filter(functions.col("count").gt(1))
                .limit(1)
                .count() > 0) {
            throw new RunnerExecutionException(
                    "UPSERT_DUPLICATE_KEY",
                    "当前批次存在重复 UPSERT Key，请先使用 DEDUPLICATE",
                    output.node().id()
            );
        }
    }

    static void writeUpsert(CanvasPreparedOutput output, Dataset<Row> dataset) {
        RuntimeDataSource source = output.runtimeDataSource();
        if (source.databaseType() != RuntimeDatabaseType.POSTGRESQL
                && source.databaseType() != RuntimeDatabaseType.MYSQL) {
            throw new RunnerExecutionException(
                    "UPSERT_DATABASE_NOT_SUPPORTED",
                    "UPSERT 只支持 PostgreSQL 和 MySQL",
                    output.node().id()
            );
        }
        DatabaseDialect dialect = dialect(source);
        Map<String, CanvasColumnSchema> targetSchemaColumns = columnsByName(output.targetSchema());
        List<JdbcBinding> bindings = new ArrayList<>();
        List<JdbcUpsertColumn> upsertColumns = new ArrayList<>();
        List<Column> writableColumns = new ArrayList<>();
        for (StructField field : dataset.schema().fields()) {
            CanvasColumnSchema column = targetSchemaColumns.get(field.name());
            if (column == null) {
                throw new RunnerExecutionException(
                        "OUTPUT_MAPPING_INVALID",
                        "输出数据包含目标表中不存在的字段：" + field.name(),
                        output.node().id()
                );
            }
            boolean geometry = column.fieldType() == PlatformDataType.GEOMETRY;
            Integer localSrid = geometry ? output.geometryLocalSrids().get(column.name()) : null;
            if (geometry && (localSrid == null || localSrid < 1)) {
                throw new RunnerExecutionException(
                        "SPATIAL_TARGET_METADATA_UNAVAILABLE",
                        "目标 Geometry 字段缺少数据库本地 SRID：" + column.name(),
                        output.node().id()
                );
            }
            upsertColumns.add(new JdbcUpsertColumn(column.name(), localSrid));
            bindings.add(new JdbcBinding(column.name(), geometry));
            writableColumns.add(geometry
                    ? st_functions.ST_AsBinary(sparkColumn(column.name())).as(column.name())
                    : sparkColumn(column.name()));
        }
        String sql = dialect.renderRowUpsert(
                output.targetTable(),
                upsertColumns,
                output.upsertKeyColumns()
        );
        RuntimeJdbcConnection connection = source.connection();
        SpatialWriteSpec writeSpec = new SpatialWriteSpec(
                connection.driverClassName(),
                connection.jdbcUrl(),
                jdbcProperties(connection),
                sql,
                List.copyOf(bindings)
        );
        dataset.select(writableColumns.toArray(Column[]::new))
                .foreachPartition((ForeachPartitionFunction<Row>) writeSpec::write);
    }

    static DatabaseDialect dialect(RuntimeDataSource source) {
        if (source.databaseType() == null) {
            throw new RunnerExecutionException(
                    "SPATIAL_JDBC_UNSUPPORTED",
                    "空间 JDBC 数据源缺少数据库类型",
                    null
            );
        }
        return DIALECTS.require(source.databaseType().name());
    }

    private static JdbcConnectionSpec jdbcSpec(RuntimeJdbcConnection connection) {
        return new JdbcConnectionSpec(
                connection.driverClassName(),
                connection.jdbcUrl(),
                jdbcProperties(connection),
                connection.schemaName()
        );
    }

    static Properties jdbcProperties(RuntimeJdbcConnection connection) {
        Properties properties = new Properties();
        properties.putAll(connection.properties());
        properties.setProperty("user", connection.username());
        if (connection.password() != null) {
            properties.setProperty("password", connection.password());
        }
        return properties;
    }

    private static boolean containsGeometry(CanvasTableSchema schema) {
        return schema.columns().stream()
                .anyMatch(column -> column.fieldType() == PlatformDataType.GEOMETRY);
    }

    private static Map<String, CanvasColumnSchema> columnsByName(CanvasTableSchema schema) {
        Map<String, CanvasColumnSchema> columns = new LinkedHashMap<>();
        for (CanvasColumnSchema column : schema.columns()) {
            columns.put(column.name(), column);
        }
        return Map.copyOf(columns);
    }

    private static Column sparkColumn(String name) {
        return functions.col("`" + name.replace("`", "``") + "`");
    }

    private record JdbcBinding(String columnName, boolean geometry) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    private static final class SpatialWriteSpec implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String driverClassName;
        private final String jdbcUrl;
        private final Properties properties;
        private final String insertSql;
        private final List<JdbcBinding> bindings;

        private SpatialWriteSpec(
                String driverClassName,
                String jdbcUrl,
                Properties properties,
                String insertSql,
                List<JdbcBinding> bindings
        ) {
            this.driverClassName = driverClassName;
            this.jdbcUrl = jdbcUrl;
            this.properties = properties;
            this.insertSql = insertSql;
            this.bindings = bindings;
        }

        private void write(java.util.Iterator<Row> rows) throws Exception {
            if (!rows.hasNext()) {
                return;
            }
            Class.forName(driverClassName);
            try (Connection connection = DriverManager.getConnection(jdbcUrl, properties);
                 PreparedStatement statement = connection.prepareStatement(insertSql)) {
                boolean originalAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    int batchSize = 0;
                    do {
                        Row row = rows.next();
                        for (int index = 0; index < bindings.size(); index++) {
                            Object value = row.isNullAt(index) ? null : row.get(index);
                            if (value instanceof byte[] bytes) {
                                statement.setBytes(index + 1, bytes);
                            } else {
                                statement.setObject(index + 1, value);
                            }
                        }
                        statement.addBatch();
                        batchSize++;
                        if (batchSize == WRITE_BATCH_SIZE) {
                            statement.executeBatch();
                            batchSize = 0;
                        }
                    } while (rows.hasNext());
                    if (batchSize > 0) {
                        statement.executeBatch();
                    }
                    connection.commit();
                } catch (SQLException | RuntimeException exception) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackFailure) {
                        exception.addSuppressed(rollbackFailure);
                    }
                    throw exception;
                } finally {
                    try {
                        connection.setAutoCommit(originalAutoCommit);
                    } catch (SQLException ignored) {
                        // Connection is closing; preserving the original failure is more useful.
                    }
                }
            }
        }
    }
}
