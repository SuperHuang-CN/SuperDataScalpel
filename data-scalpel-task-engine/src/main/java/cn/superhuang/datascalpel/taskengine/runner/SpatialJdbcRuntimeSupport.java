package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.JdbcInputReadOption;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;
import cn.superhuang.data.scalpel.dialect.model.JdbcUpsertColumn;
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
import org.apache.spark.sql.DataFrameReader;
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
 * Controlled PostgreSQL-family PostGIS-compatible and MySQL 8 Geometry bridge.
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
            List<JdbcInputReadOption> readOptions,
            String nodeId
    ) {
        return readTable(spark, source, table, logicalSchema, reader(spark, source, readOptions), nodeId);
    }

    static Dataset<Row> readTable(SparkSession spark, RuntimeDataSource source, TableIdentifier table,
                                  CanvasTableSchema logicalSchema, DataFrameReader configuredReader, String nodeId) {
        validateSourceTableBoundary(source, table, nodeId);
        boolean containsGeometry = containsGeometry(logicalSchema);
        if (!containsGeometry) {
            return configuredReader
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
                selectExpressions.add(geometryReadExpression(dialect, quotedColumn) + " AS "
                        + dialect.quoteIdentifier(alias));
            } else {
                selectExpressions.add(quotedColumn);
            }
        }
        String query = "(SELECT " + String.join(", ", selectExpressions)
                + " FROM " + dialect.qualifiedName(table) + ") AS "
                + dialect.quoteIdentifier(INPUT_ALIAS);
        Dataset<Row> raw = configuredReader
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

    private static DataFrameReader reader(
            SparkSession spark,
            RuntimeDataSource source,
            List<JdbcInputReadOption> readOptions
    ) {
        DataFrameReader reader = CanvasTaskExecutor.reader(spark, source);
        if (readOptions != null) {
            for (JdbcInputReadOption option : readOptions) {
                reader = reader.option(option.name(), option.value());
            }
        }
        return reader;
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

    // Native spatial writes use standard EPSG identifiers declared by the target field snapshot.
    // Generic geometry columns need not declare an SRID; do not query their catalog metadata here.
    static Map<String, Integer> resolveGeometryWriteSrids(
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
        Map<String, Integer> writeSrids = new LinkedHashMap<>();
        for (CanvasColumnSchema geometryColumn : geometryColumns) {
            var geometry = geometryColumn.geometry();
            if (geometry == null || geometry.crs() == null
                    || !"EPSG".equals(geometry.crs().authority()) || geometry.crs().code() < 1) {
                throw new RunnerExecutionException(
                        "SPATIAL_TARGET_METADATA_UNAVAILABLE",
                        "目标 Geometry 字段快照缺少有效的 EPSG CRS：" + geometryColumn.name(),
                        nodeId
                );
            }
            writeSrids.put(geometryColumn.name(), geometry.crs().code());
        }
        return Map.copyOf(writeSrids);
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

    /** Canvas-specific snapshot validation; SQL and partition writes are shared with SDK. */
    static Map<String, Integer> directWriteSrids(CanvasPreparedOutput output, Dataset<Row> dataset) {
        boolean geometry = requiresSpatialWriter(output);
        DirectJdbcWriter.requireSupported(output.runtimeDataSource(), output.writeMode().name(),
                geometry, output.node().id());
        if (!geometry && output.writeMode() != cn.superhuang.data.scalpel.contract.task.JdbcWriteMode.UPSERT) {
            return Map.of();
        }
        Map<String, CanvasColumnSchema> targetSchemaColumns = columnsByName(output.targetSchema());
        Map<String, Integer> srids = new LinkedHashMap<>();
        for (StructField field : dataset.schema().fields()) {
            CanvasColumnSchema column = targetSchemaColumns.get(field.name());
            if (column == null) {
                throw new RunnerExecutionException(
                        "OUTPUT_MAPPING_INVALID",
                        "输出数据包含目标表中不存在的字段：" + field.name(),
                        output.node().id());
            }
            if (column.fieldType() == PlatformDataType.GEOMETRY) {
                Integer srid = output.geometryWriteSrids().get(column.name());
                if (srid == null || srid < 1) {
                    throw new RunnerExecutionException(
                            "SPATIAL_TARGET_METADATA_UNAVAILABLE",
                            "目标 Geometry 字段快照缺少有效的 EPSG CRS：" + column.name(),
                            output.node().id());
                }
                srids.put(column.name(), srid);
            }
        }
        return Map.copyOf(srids);
    }

    static void validateUpsertKeys(CanvasPreparedOutput output, Dataset<Row> dataset) {
        DirectJdbcWriter.validateUpsertKeys(dataset, output.upsertKeyColumns(), output.node().id());
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

    /** Same WKB constructor and partition transaction used by Canvas, also used by the public SDK. */
    static void writeDataset(RuntimeDataSource source, TableIdentifier target, String qualifiedTableName,
                             Dataset<Row> dataset, List<String> keys, Map<String, Integer> srids) {
        DatabaseDialect dialect = dialect(source);
        List<JdbcUpsertColumn> columns = new ArrayList<>();
        List<JdbcBinding> bindings = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        for (String name : dataset.columns()) {
            Integer srid = srids.get(name);
            columns.add(new JdbcUpsertColumn(name, srid));
            bindings.add(new JdbcBinding(name, srid != null));
            placeholders.add(srid == null ? "?" : geometryWriteExpression(dialect, srid));
        }
        String sql = keys.isEmpty() ? "INSERT INTO " + qualifiedTableName + " ("
                + columns.stream().map(c -> dialect.quoteIdentifier(c.name())).collect(java.util.stream.Collectors.joining(", "))
                + ") VALUES (" + String.join(", ", placeholders) + ")" : dialect.renderRowUpsert(target, columns, keys);
        var spec = new SpatialWriteSpec(source.connection().driverClassName(), source.connection().jdbcUrl(),
                jdbcProperties(source.connection()), sql, bindings);
        encodeGeometry(dataset, srids).foreachPartition((ForeachPartitionFunction<Row>) spec::write);
    }

    static String geometryReadExpression(DatabaseDialect dialect, String quotedColumn) {
        return "ST_AsBinary(" + quotedColumn
                + ("MYSQL".equals(dialect.definition().id()) ? ", 'axis-order=long-lat'" : "") + ")";
    }

    static String geometryWriteExpression(DatabaseDialect dialect, int srid) {
        return "ST_GeomFromWKB(?, " + srid
                + ("MYSQL".equals(dialect.definition().id()) ? ", 'axis-order=long-lat'" : "") + ")";
    }

    static Dataset<Row> encodeGeometry(Dataset<Row> dataset, Map<String, Integer> srids) {
        for (StructField field : dataset.schema().fields()) {
            boolean geometry = field.dataType().typeName().equalsIgnoreCase("geometry")
                    || field.dataType().getClass().getSimpleName().equals("GeometryUDT");
            if (geometry != srids.containsKey(field.name())) {
                throw new RunnerExecutionException("SPATIAL_TARGET_METADATA_UNAVAILABLE", "Geometry 字段与显式 EPSG 配置不匹配", null);
            }
        }
        if (!Set.of(dataset.columns()).containsAll(srids.keySet()) || srids.values().stream().anyMatch(v -> v == null || v <= 0)) {
            throw new RunnerExecutionException("SPATIAL_TARGET_METADATA_UNAVAILABLE", "Geometry 字段或 EPSG 配置无效", null);
        }
        return dataset.select(java.util.Arrays.stream(dataset.columns()).map(name -> srids.containsKey(name)
                ? st_functions.ST_AsBinary(sparkColumn(name)).as(name) : sparkColumn(name)).toArray(Column[]::new));
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
