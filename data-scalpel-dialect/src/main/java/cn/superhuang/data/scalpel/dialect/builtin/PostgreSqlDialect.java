package cn.superhuang.data.scalpel.dialect.builtin;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionChoice;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionDefinition;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionType;
import cn.superhuang.data.scalpel.dialect.api.NamespaceMode;
import cn.superhuang.data.scalpel.dialect.api.JdbcIncrementalReadDialect;
import cn.superhuang.data.scalpel.dialect.api.SpatialPreviewDialect;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;
import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.DdlPlan;
import cn.superhuang.data.scalpel.dialect.model.JdbcTypeDescriptor;
import cn.superhuang.data.scalpel.dialect.model.JdbcUpsertColumn;
import cn.superhuang.data.scalpel.dialect.model.JdbcSnapshotColumn;
import cn.superhuang.data.scalpel.dialect.model.JdbcSnapshotSyncSql;
import cn.superhuang.data.scalpel.dialect.model.PhysicalTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.SpatialColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewColumn;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewData;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewLimits;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewMetadata;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewViewport;
import cn.superhuang.data.scalpel.dialect.model.TableChangeCheck;
import cn.superhuang.data.scalpel.dialect.model.TableChangeCheckType;
import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionMode;
import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionOption;
import cn.superhuang.data.scalpel.dialect.model.TableChangeOperation;
import cn.superhuang.data.scalpel.dialect.model.TableChangeOperationType;
import cn.superhuang.data.scalpel.dialect.model.TableChangePlan;
import cn.superhuang.data.scalpel.dialect.model.TableChangeReason;
import cn.superhuang.data.scalpel.dialect.model.TableChangeReasonCode;
import cn.superhuang.data.scalpel.dialect.model.TableChangeRisk;
import cn.superhuang.data.scalpel.dialect.model.TableChangeStrategy;
import cn.superhuang.data.scalpel.dialect.model.TableColumnDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableColumnType;
import cn.superhuang.data.scalpel.dialect.model.TableDdlAtomicity;
import cn.superhuang.data.scalpel.dialect.model.TableDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.model.TablePhysicalStatistics;
import cn.superhuang.data.scalpel.dialect.model.TableStatisticQuality;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public class PostgreSqlDialect extends AbstractJdbcDialect implements JdbcIncrementalReadDialect, SpatialPreviewDialect {

    private final String jdbcUrlPrefix;
    private final String databaseDisplayName;

    public PostgreSqlDialect() {
        this("POSTGRESQL", "PostgreSQL", 5432, "org.postgresql.Driver", "jdbc:postgresql://");
    }

    protected PostgreSqlDialect(String id, String displayName, int defaultPort,
            String driverClassName, String jdbcUrlPrefix) {
        super(
                id, displayName, defaultPort,
                "数据库", "Schema", "public", NamespaceMode.SCHEMA,
                List.of(new ConnectionOptionDefinition(
                        "sslmode", "SSL 模式", ConnectionOptionType.SELECT, null,
                        List.of(
                                new ConnectionOptionChoice("disable", "禁用"),
                                new ConnectionOptionChoice("prefer", "优先"),
                                new ConnectionOptionChoice("require", "必须")
                        )
                )),
                driverClassName, "\"", "\"", QualificationMode.SCHEMA, PreviewStyle.LIMIT
        );
        this.jdbcUrlPrefix = jdbcUrlPrefix;
        this.databaseDisplayName = displayName;
    }

    protected PostgreSqlCatalogNames catalogNames(Connection connection) throws SQLException {
        return PostgreSqlCatalogNames.postgresql();
    }

    protected final String databaseDisplayName() {
        return databaseDisplayName;
    }

    @Override
    public JdbcConnectionSpec createConnectionSpec(JdbcConnectionConfig config) {
        Properties properties = baseProperties(config);
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "15");
        properties.setProperty("ApplicationName", "DataScalpel");
        applyConnectionOptions(
                config, properties, Set.of(),
                Set.of("connectTimeout", "socketTimeout", "ApplicationName", "currentSchema")
        );
        String url = jdbcUrlPrefix + hostForUrl(config) + ":" + config.port() + "/" + pathSegment(config.databaseName());
        return new JdbcConnectionSpec(driverClassName(), url, properties, config.schemaName());
    }

    @Override
    public String resolveCatalog(JdbcConnectionConfig config, String requestedCatalog) {
        return optional(requestedCatalog) == null ? config.databaseName() : requestedCatalog.trim();
    }

    @Override
    public String readOnlySessionInitializationSql() {
        return "SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY";
    }

    @Override
    public TablePhysicalStatistics readTablePhysicalStatistics(
            Connection connection,
            TableIdentifier table,
            Duration timeout
    ) throws SQLException {
        PostgreSqlCatalogNames catalogs = catalogNames(connection);
        String sql = """
                WITH RECURSIVE target AS (
                    SELECT c.oid, c.relkind, c.reltuples
                    FROM %s c
                    JOIN %s n ON n.oid = c.relnamespace
                    WHERE n.nspname = ? AND c.relname = ?
                ), relations AS (
                    SELECT oid, relkind, reltuples FROM target
                    UNION ALL
                    SELECT child.oid, child.relkind, child.reltuples
                    FROM relations parent
                    JOIN %s inheritance ON inheritance.inhparent = parent.oid
                    JOIN %s child ON child.oid = inheritance.inhrelid
                )
                SELECT
                    (SELECT CAST(relkind AS varchar) FROM target),
                    CASE WHEN SUM(CASE WHEN reltuples >= 0 THEN 1 ELSE 0 END) = 0 THEN NULL
                         ELSE CAST(SUM(CASE WHEN reltuples >= 0 THEN reltuples ELSE 0 END) AS bigint) END,
                    CAST(SUM(CASE WHEN relkind IN ('r', 'm') THEN %s(oid) ELSE 0 END) AS bigint)
                FROM relations
                """.formatted(
                catalogs.relation("class"),
                catalogs.relation("namespace"),
                catalogs.relation("inherits"),
                catalogs.relation("class"),
                catalogs.function("total_relation_size")
        );
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(TableStatisticsJdbcSupport.timeoutSeconds(timeout));
            statement.setString(1, table.schema());
            statement.setString(2, table.table());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getString(1) == null) {
                    return TablePhysicalStatistics.notFound();
                }
                String kind = resultSet.getString(1);
                if ("v".equals(kind)) {
                    return TablePhysicalStatistics.unsupported("普通视图没有独立物理存储统计");
                }
                if (!"r".equals(kind) && !"p".equals(kind) && !"m".equals(kind)) {
                    return TablePhysicalStatistics.unsupported(
                            "当前 " + databaseDisplayName + " 物理对象类型无法提供表统计");
                }
                Long rowCount = TableStatisticsJdbcSupport.nullableLong(resultSet, 2);
                Long storageBytes = TableStatisticsJdbcSupport.nullableLong(resultSet, 3);
                return TablePhysicalStatistics.available(
                        rowCount,
                        TableStatisticQuality.ESTIMATED,
                        storageBytes,
                        TableStatisticQuality.EXACT
                );
            }
        }
    }

    @Override
    public SpatialPreviewMetadata inspectSpatialPreview(
            Connection connection,
            TableIdentifier table,
            List<SpatialPreviewColumn> columns,
            Duration timeout
    ) throws SQLException {
        String extensionSchema = postGisSchema(connection, timeout);
        if (extensionSchema == null) {
            return SpatialPreviewMetadata.unsupported(
                    "目标 " + databaseDisplayName + " 数据库未安装或未启用 PostGIS 兼容扩展");
        }
        Set<String> indexedColumns = spatialPreviewIndexedColumns(connection, table, timeout);
        List<SpatialPreviewColumnMetadata> result = new ArrayList<>();
        for (SpatialPreviewColumn column : columns) {
            String issue = SpatialTypeSupport.validateV1Geometry(column.geometry());
            if (issue == null) {
                try {
                    resolveSpatialPreviewSrid(
                            connection, extensionSchema, column.geometry().crs().code(), timeout
                    );
                    resolveSpatialPreviewSrid(connection, extensionSchema, 3857, timeout);
                } catch (IllegalArgumentException exception) {
                    issue = exception.getMessage();
                }
            }
            boolean indexed = indexedColumns.contains(normalizeSpatialName(column.name()));
            result.add(new SpatialPreviewColumnMetadata(
                    column.name(), indexed, issue == null, issue
            ));
        }
        return new SpatialPreviewMetadata(true, null, result);
    }

    @Override
    public SpatialPreviewData readSpatialPreview(
            Connection connection,
            TableIdentifier table,
            SpatialPreviewColumn column,
            SpatialPreviewViewport viewport,
            SpatialPreviewLimits limits,
            Duration timeout
    ) throws SQLException {
        String extensionSchema = postGisSchema(connection, timeout);
        if (extensionSchema == null) {
            throw new IllegalArgumentException(
                    "目标 " + databaseDisplayName + " 数据库未安装或未启用 PostGIS 兼容扩展");
        }
        int sourceSrid = resolveSpatialPreviewSrid(
                connection, extensionSchema, column.geometry().crs().code(), timeout
        );
        int targetSrid = resolveSpatialPreviewSrid(connection, extensionSchema, 3857, timeout);
        boolean indexed = spatialPreviewIndexedColumns(connection, table, timeout)
                .contains(normalizeSpatialName(column.name()));
        if (!indexed) {
            Long estimatedRows = limits.unindexedTableRowCount();
            if (estimatedRows == null) {
                throw new IllegalArgumentException(
                        "未发现可用空间索引，且模型物理统计行数不可用；请先刷新物理统计，仍不可用时执行 ANALYZE"
                );
            }
            if (estimatedRows > limits.unindexedMaximumRows()) {
                throw new IllegalArgumentException(
                        "未发现可用空间索引，估算行数超过 50,000；请创建 GiST/SP-GiST 索引"
                );
            }
        }

        String postGis = quoteIdentifier(extensionSchema) + ".";
        String geometryColumn = quoteIdentifier(column.name());
        String sql = """
                WITH params AS (
                    SELECT %1$s%2$s(?, ?, ?, ?, ?) AS target_bbox
                ), transformed_params AS (
                    SELECT target_bbox, %1$s%3$s(target_bbox, ?) AS source_bbox
                    FROM params
                ), candidates AS (
                    SELECT source.%4$s AS geom
                    FROM %5$s source
                    CROSS JOIN transformed_params bounds
                    WHERE source.%4$s IS NOT NULL
                      AND source.%4$s OPERATOR(%6$s.&&) bounds.source_bbox
                    LIMIT ?
                )
                SELECT CASE
                         WHEN %1$s%7$s(geom) OR NOT %1$s%8$s(geom) THEN NULL
                         ELSE %1$s%9$s(
                           %1$s%10$s(
                             %1$s%11$s(%1$s%3$s(geom, ?), target_bbox),
                             ?
                           )
                         )
                       END AS geometry_wkb
                FROM candidates
                CROSS JOIN transformed_params
                """.formatted(
                postGis,
                quoteIdentifier("st_makeenvelope"),
                quoteIdentifier("st_transform"),
                geometryColumn,
                qualifiedName(table),
                quoteIdentifier(extensionSchema),
                quoteIdentifier("st_isempty"),
                quoteIdentifier("st_isvalid"),
                quoteIdentifier("st_asbinary"),
                quoteIdentifier("st_simplifypreservetopology"),
                quoteIdentifier("st_intersection")
        );
        List<byte[]> geometries = new ArrayList<>();
        int skipped = 0;
        int rowsRead = 0;
        boolean truncated = false;
        long wkbBytes = 0L;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(TableStatisticsJdbcSupport.timeoutSeconds(timeout));
            statement.setDouble(1, viewport.minX());
            statement.setDouble(2, viewport.minY());
            statement.setDouble(3, viewport.maxX());
            statement.setDouble(4, viewport.maxY());
            statement.setInt(5, targetSrid);
            statement.setInt(6, sourceSrid);
            statement.setInt(7, Math.addExact(limits.maximumFeatures(), 1));
            statement.setInt(8, targetSrid);
            statement.setDouble(9, viewport.simplificationTolerance());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rowsRead++;
                    if (rowsRead > limits.maximumFeatures()) {
                        truncated = true;
                        break;
                    }
                    byte[] wkb = resultSet.getBytes(1);
                    if (wkb == null || wkb.length == 0) {
                        skipped++;
                        continue;
                    }
                    if (wkbBytes + wkb.length > limits.maximumWkbBytes()) {
                        truncated = true;
                        break;
                    }
                    geometries.add(wkb);
                    wkbBytes += wkb.length;
                }
            }
        }
        return new SpatialPreviewData(geometries, skipped, truncated);
    }

    @Override
    public String renderRowUpsert(
            TableIdentifier target,
            List<JdbcUpsertColumn> columns,
            List<String> keyColumns
    ) {
        validateUpsert(columns, keyColumns);
        String names = columns.stream().map(column -> quoteIdentifier(column.name()))
                .collect(java.util.stream.Collectors.joining(", "));
        String values = columns.stream().map(PostgreSqlDialect::upsertValue)
                .collect(java.util.stream.Collectors.joining(", "));
        String keys = keyColumns.stream().map(this::quoteIdentifier)
                .collect(java.util.stream.Collectors.joining(", "));
        Set<String> keySet = Set.copyOf(keyColumns);
        List<JdbcUpsertColumn> updates = columns.stream()
                .filter(column -> !keySet.contains(column.name())).toList();
        String conflict = updates.isEmpty()
                ? " DO NOTHING"
                : " DO UPDATE SET " + updates.stream()
                .map(column -> quoteIdentifier(column.name()) + " = EXCLUDED." + quoteIdentifier(column.name()))
                .collect(java.util.stream.Collectors.joining(", "));
        return "INSERT INTO " + qualifiedName(target) + " (" + names + ") VALUES (" + values
                + ") ON CONFLICT (" + keys + ")" + conflict;
    }

    @Override
    public JdbcSnapshotSyncSql renderSnapshotSyncSql(
            TableIdentifier target,
            List<JdbcSnapshotColumn> columns,
            List<String> keyColumns,
            Duration lockTimeout
    ) {
        validateSnapshotSync(columns, keyColumns, lockTimeout);
        String table = qualifiedName(target);
        String selectColumns = columns.stream()
                .map(column -> column.geometry()
                        ? "ST_AsBinary(" + quoteIdentifier(column.name()) + ") AS "
                        + quoteIdentifier(column.name())
                        : quoteIdentifier(column.name()))
                .collect(java.util.stream.Collectors.joining(", "));
        String keyPredicate = keyColumns.stream()
                .map(key -> quoteIdentifier(key) + " = ?")
                .collect(java.util.stream.Collectors.joining(" AND "));
        Set<String> keySet = Set.copyOf(keyColumns);
        List<JdbcSnapshotColumn> updateColumns = columns.stream()
                .filter(column -> !keySet.contains(column.name()))
                .toList();
        String updateSql = updateColumns.isEmpty() ? null : "UPDATE " + table + " SET "
                + updateColumns.stream()
                .map(column -> quoteIdentifier(column.name()) + " = " + snapshotValue(column))
                .collect(java.util.stream.Collectors.joining(", "))
                + " WHERE " + keyPredicate;
        String names = columns.stream().map(column -> quoteIdentifier(column.name()))
                .collect(java.util.stream.Collectors.joining(", "));
        String values = columns.stream().map(PostgreSqlDialect::snapshotValue)
                .collect(java.util.stream.Collectors.joining(", "));
        long millis = Math.max(1L, lockTimeout.toMillis());
        return new JdbcSnapshotSyncSql(
                List.of(
                        "SET LOCAL lock_timeout = '" + millis + "ms'",
                        "LOCK TABLE " + table + " IN ACCESS EXCLUSIVE MODE"
                ),
                "SELECT " + selectColumns + " FROM " + table,
                "DELETE FROM " + table + " WHERE " + keyPredicate,
                updateSql,
                "INSERT INTO " + table + " (" + names + ") VALUES (" + values + ")",
                null
        );
    }

    private static String snapshotValue(JdbcSnapshotColumn column) {
        return column.geometry()
                ? "ST_GeomFromWKB(?, " + column.geometrySpatialReferenceId() + ")"
                : "?";
    }

    private static void validateSnapshotSync(
            List<JdbcSnapshotColumn> columns,
            List<String> keyColumns,
            Duration timeout
    ) {
        if (columns == null || columns.isEmpty() || keyColumns == null || keyColumns.isEmpty()
                || timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Invalid snapshot synchronization SQL arguments");
        }
        Set<String> names = columns.stream().map(JdbcSnapshotColumn::name)
                .collect(java.util.stream.Collectors.toSet());
        if (names.size() != columns.size() || !names.containsAll(keyColumns)) {
            throw new IllegalArgumentException("Snapshot Key must be included in unique columns");
        }
    }

    private static String upsertValue(JdbcUpsertColumn column) {
        return column.geometrySpatialReferenceId() == null
                ? "?"
                : "ST_GeomFromWKB(?, " + column.geometrySpatialReferenceId() + ")";
    }

    private static void validateUpsert(List<JdbcUpsertColumn> columns, List<String> keyColumns) {
        if (columns == null || columns.isEmpty() || keyColumns == null || keyColumns.isEmpty()) {
            throw new IllegalArgumentException("UPSERT columns and key columns are required");
        }
        Set<String> names = columns.stream().map(JdbcUpsertColumn::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!names.containsAll(keyColumns)) {
            throw new IllegalArgumentException("UPSERT key columns must be inserted");
        }
    }

    @Override
    protected String columnTypeSql(TableColumnDefinition column) {
        return switch (column.type()) {
            case BYTE, SHORT -> "smallint";
            case STRING -> "varchar(" + column.length() + ")";
            case TEXT -> "text";
            case INTEGER -> "integer";
            case LONG -> "bigint";
            case FLOAT -> "real";
            case DOUBLE -> "double precision";
            case DECIMAL -> "numeric(" + column.precision() + "," + column.scale() + ")";
            case BOOLEAN -> "boolean";
            case DATE -> "date";
            case TIMESTAMP -> "timestamp with time zone";
            case TIMESTAMP_NTZ, DATETIME -> "timestamp";
            case BINARY -> "bytea";
            case GEOMETRY -> "geometry";
        };
    }

    @Override
    protected boolean matchesGeometryColumn(TableColumnDefinition expected, ColumnMetadata actual) {
        return "geometry".equals(postGisNativeType(actual.nativeType()));
    }

    @Override
    protected Optional<TypeMappingResult<PlatformTypeDefinition>> mapDialectTypeToPlatform(
            JdbcTypeDescriptor physicalType
    ) {
        String nativeType = postGisNativeType(physicalType.nativeTypeName());
        if ("geography".equals(nativeType)) {
            return Optional.of(TypeMappingResult.unsupported("PostGIS geography 第一版不支持"));
        }
        if ("raster".equals(nativeType)) {
            return Optional.of(TypeMappingResult.unsupported("PostGIS raster 第一版不支持"));
        }
        if ("geometry".equals(nativeType)) {
            return Optional.of(mapGeometryToPlatform(physicalType));
        }
        return switch (nativeType) {
            case "bool", "boolean" -> exact(PlatformDataType.BOOLEAN);
            case "int2", "smallint", "smallserial", "serial2" -> exact(PlatformDataType.SHORT);
            case "int4", "integer", "serial", "serial4" -> exact(PlatformDataType.INTEGER);
            case "int8", "bigint", "bigserial", "serial8" -> exact(PlatformDataType.LONG);
            case "float4", "real" -> exact(PlatformDataType.FLOAT);
            case "float8", "double precision" -> exact(PlatformDataType.DOUBLE);
            case "text" -> Optional.of(TypeMappingResult.normalized(
                    PlatformTypeDefinition.string(null),
                    databaseDisplayName + " text 按无长度上限的 STRING 归一化"
            ));
            case "bytea" -> exact(PlatformDataType.BINARY);
            case "date" -> exact(PlatformDataType.DATE);
            case "timestamp", "timestamp without time zone" -> exact(PlatformDataType.TIMESTAMP_NTZ);
            case "timestamptz", "timestamp with time zone" -> exact(PlatformDataType.TIMESTAMP);
            default -> Optional.empty();
        };
    }

    @Override
    protected TypeMappingResult<PhysicalTypeDefinition> mapPlatformTypeToPhysical(
            PlatformTypeDefinition platformType
    ) {
        if (platformType.type() == PlatformDataType.GEOMETRY) {
            String issue = SpatialTypeSupport.validateV1Geometry(platformType.geometry());
            return issue == null
                    ? TypeMappingResult.exact(new PhysicalTypeDefinition(
                    TableColumnType.GEOMETRY, null, null, null, platformType.geometry()
            ))
                    : TypeMappingResult.unsupported(issue);
        }
        if (platformType.type() == PlatformDataType.BYTE) {
            return TypeMappingResult.normalized(
                    new PhysicalTypeDefinition(TableColumnType.SHORT, null, null, null),
                    databaseDisplayName + " 没有 8 位整数，BYTE 使用 smallint 存储"
            );
        }
        return super.mapPlatformTypeToPhysical(platformType);
    }

    private static Optional<TypeMappingResult<PlatformTypeDefinition>> exact(PlatformDataType type) {
        return Optional.of(TypeMappingResult.exact(PlatformTypeDefinition.of(type)));
    }

    @Override
    public List<ColumnMetadata> enrichColumnMetadata(
            Connection connection,
            TableIdentifier table,
            List<ColumnMetadata> columns
    ) throws SQLException {
        if (columns.stream().noneMatch(PostgreSqlDialect::isPostGisGeometryNativeType)) {
            return List.copyOf(columns);
        }
        String extensionSchema = postGisSchema(connection);
        if (extensionSchema == null) {
            return columns.stream().map(PostgreSqlDialect::unresolvedSpatialColumn).toList();
        }

        Map<String, SpatialColumnMetadata> spatialByColumn = new HashMap<>();
        String sql = """
                SELECT gc.f_geometry_column, gc.type, gc.srid, gc.coord_dimension,
                       s.auth_name, s.auth_srid
                  FROM %s.geometry_columns gc
                  LEFT JOIN %s.spatial_ref_sys s ON s.srid = gc.srid
                 WHERE gc.f_table_schema = ? AND gc.f_table_name = ?
                """.formatted(quoteIdentifier(extensionSchema), quoteIdentifier(extensionSchema));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table.schema());
            statement.setString(2, table.table());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Integer srid = nullableInteger(resultSet, "srid");
                    Integer authorityCode = nullableInteger(resultSet, "auth_srid");
                    String columnName = resultSet.getString("f_geometry_column");
                    spatialByColumn.put(normalizeSpatialName(columnName), new SpatialColumnMetadata(
                            resultSet.getString("type"),
                            srid,
                            resultSet.getString("auth_name"),
                            authorityCode,
                            SpatialTypeSupport.coordinateDimension(nullableInteger(resultSet, "coord_dimension")),
                            true,
                            srid != null && srid > 0
                    ));
                }
            }
        }
        return columns.stream().map(column -> {
            if (!isPostGisGeometryNativeType(column)) {
                return column;
            }
            SpatialColumnMetadata spatial = spatialByColumn.get(normalizeSpatialName(column.name()));
            return spatial == null ? unresolvedSpatialColumn(column) : column.withSpatial(spatial);
        }).toList();
    }

    @Override
    public DdlPlan planCreateTable(TableDefinition definition) {
        if (SpatialTypeSupport.containsGeometry(definition)) {
            throw new IllegalArgumentException(
                    "Geometry 建表规划需要连接目标 " + databaseDisplayName + " 数据库确认 PostGIS 兼容扩展");
        }
        return super.planCreateTable(definition);
    }

    @Override
    public DdlPlan planCreateTable(Connection connection, TableDefinition definition) throws SQLException {
        if (!SpatialTypeSupport.containsGeometry(definition)) {
            return super.planCreateTable(definition);
        }
        String extensionSchema = postGisSchema(connection);
        if (extensionSchema == null) {
            throw new IllegalArgumentException(
                    "目标 " + databaseDisplayName + " 数据库未安装或未启用 PostGIS 兼容扩展");
        }
        List<String> clauses = new ArrayList<>();
        for (TableColumnDefinition column : definition.columns()) {
            String typeSql = column.type() == TableColumnType.GEOMETRY
                    ? quoteIdentifier(extensionSchema) + "." + quoteIdentifier("geometry")
                    : columnTypeSql(column);
            clauses.add(quoteIdentifier(column.name()) + " " + typeSql
                    + (column.nullable() ? "" : " NOT NULL"));
        }
        appendPrimaryKey(definition, clauses);
        return new DdlPlan(
                definition.table(),
                List.of("CREATE TABLE " + qualifiedName(definition.table()) + " (" + String.join(", ", clauses) + ")")
        );
    }

    @Override
    public TableChangePlan planTableChange(TableDefinition before, TableDefinition target, TableMetadata actual) {
        if (SpatialTypeSupport.containsGeometry(before) || SpatialTypeSupport.containsGeometry(target)) {
            if (!SpatialTypeSupport.isConstraintOnlyChange(before, target)) {
                throw new UnsupportedOperationException(
                        "空间字段所在受管表当前仅支持修改可空性和非空间字段主键约束"
                );
            }
        }
        if (!compareTable(before, actual).compatible()) {
            throw new IllegalArgumentException("Physical table structure has drifted from the source definition");
        }
        if (requiresRebuild(before, target)) {
            return planRebuild(before, target, actual);
        }

        List<TableChangeOperation> operations = new ArrayList<>();
        LinkedHashSet<TableChangeCheck> checks = new LinkedHashSet<>();
        List<TableChangeReason> reasons = new ArrayList<>();
        List<String> renameStatements = new ArrayList<>();
        List<String> alterStatements = new ArrayList<>();
        List<String> addStatements = new ArrayList<>();
        List<String> dropStatements = new ArrayList<>();
        String table = qualifiedName(before.table());

        checks.add(structureCheck(before));
        Map<UUID, TableColumnDefinition> beforeById = columnsById(before, "source");
        Map<UUID, TableColumnDefinition> targetById = columnsById(target, "target");
        Map<String, TableColumnDefinition> targetByName = columnsByName(target);
        Set<UUID> primaryKeyIds = primaryKeyIds(before, beforeById);
        Set<UUID> changedPrimaryKeyColumns = new HashSet<>();
        boolean unsupported = false;

        for (TableColumnDefinition source : before.columns()) {
            TableColumnDefinition destination = source.columnId() == null
                    ? targetByName.get(normalize(source.name()))
                    : targetById.get(source.columnId());
            if (destination == null) {
                TableChangeOperation operation = columnOperation(
                        TableChangeOperationType.DROP_COLUMN,
                        source,
                        null,
                        TableChangeStrategy.IN_PLACE,
                        TableChangeRisk.DESTRUCTIVE,
                        List.of(new TableChangeReason(
                                TableChangeReasonCode.POSSIBLE_DATA_LOSS,
                                "删除字段会永久删除该列中的数据"
                        )),
                        List.of(externalDependencyCheck())
                );
                operations.add(operation);
                checks.addAll(operation.checks());
                dropStatements.add("ALTER TABLE " + table + " DROP COLUMN " + quoteIdentifier(source.name()));
                continue;
            }

            String currentName = source.name();
            TableColumnDefinition effectiveSource = source;
            if (!source.name().equalsIgnoreCase(destination.name())) {
                TableChangeOperation operation = columnOperation(
                        TableChangeOperationType.RENAME_COLUMN,
                        source,
                        destination,
                        TableChangeStrategy.IN_PLACE,
                        TableChangeRisk.SAFE,
                        List.of(),
                        List.of()
                );
                operations.add(operation);
                renameStatements.add("ALTER TABLE " + table + " RENAME COLUMN "
                        + quoteIdentifier(source.name()) + " TO " + quoteIdentifier(destination.name()));
                currentName = destination.name();
                effectiveSource = new TableColumnDefinition(
                        destination.name(), source.type(), source.length(), source.precision(), source.scale(),
                        source.nullable(), source.columnId()
                );
            }

            TypeChangeResult typeChange = classifyTypeChange(source, destination, currentName);
            if (typeChange.unsupported()) {
                TableChangeOperation operation = columnOperation(
                        TableChangeOperationType.ALTER_COLUMN_TYPE,
                        effectiveSource,
                        destination,
                        TableChangeStrategy.UNSUPPORTED,
                        TableChangeRisk.CAUTION,
                        List.of(new TableChangeReason(
                                TableChangeReasonCode.PHYSICAL_TYPE_UNSUPPORTED,
                                "当前 " + databaseDisplayName + " 原表修改不支持 "
                                        + source.type() + " 到 " + destination.type() + " 的类型转换"
                        )),
                        List.of()
                );
                operations.add(operation);
                reasons.addAll(operation.reasons());
                unsupported = true;
            } else if (typeChange.changed()) {
                TableChangeOperation operation = columnOperation(
                        TableChangeOperationType.ALTER_COLUMN_TYPE,
                        effectiveSource,
                        destination,
                        TableChangeStrategy.IN_PLACE,
                        typeChange.risk(),
                        typeChange.reasons(),
                        typeChange.checks()
                );
                operations.add(operation);
                checks.addAll(operation.checks());
                alterStatements.add(alterColumnType(table, currentName, destination));
                if (primaryKeyIds.contains(source.columnId())) {
                    changedPrimaryKeyColumns.add(source.columnId());
                }
            }

            if (source.type() == TableColumnType.STRING && destination.type() == TableColumnType.STRING
                    && !source.length().equals(destination.length())) {
                boolean shrinking = destination.length() < source.length();
                List<TableChangeCheck> operationChecks = shrinking
                        ? List.of(maxStringLengthCheck(source.name(), destination.length()))
                        : List.of();
                TableChangeOperation operation = columnOperation(
                        TableChangeOperationType.ALTER_COLUMN_LENGTH,
                        effectiveSource,
                        destination,
                        TableChangeStrategy.IN_PLACE,
                        shrinking ? TableChangeRisk.CAUTION : TableChangeRisk.SAFE,
                        shrinking ? List.of(new TableChangeReason(
                                TableChangeReasonCode.DATA_PRECHECK_REQUIRED,
                                "缩短字符串长度前必须确认现有值不会超出目标长度"
                        )) : List.of(),
                        operationChecks
                );
                operations.add(operation);
                checks.addAll(operation.checks());
                alterStatements.add(alterColumnType(table, currentName, destination));
            }

            if (source.type() == TableColumnType.DECIMAL && destination.type() == TableColumnType.DECIMAL
                    && (!source.precision().equals(destination.precision()) || !source.scale().equals(destination.scale()))) {
                boolean narrowing = destination.scale() < source.scale()
                        || destination.precision() - destination.scale() < source.precision() - source.scale();
                List<TableChangeCheck> operationChecks = narrowing
                        ? List.of(decimalFitsCheck(source.name(), destination.precision(), destination.scale()))
                        : List.of();
                TableChangeOperation operation = columnOperation(
                        TableChangeOperationType.ALTER_COLUMN_PRECISION,
                        effectiveSource,
                        destination,
                        TableChangeStrategy.IN_PLACE,
                        narrowing ? TableChangeRisk.CAUTION : TableChangeRisk.SAFE,
                        narrowing ? List.of(new TableChangeReason(
                                TableChangeReasonCode.DATA_PRECHECK_REQUIRED,
                                "调整小数精度前必须确认现有数据可以放入目标精度"
                        )) : List.of(),
                        operationChecks
                );
                operations.add(operation);
                checks.addAll(operation.checks());
                alterStatements.add(alterColumnType(table, currentName, destination));
            }

            if (source.nullable() != destination.nullable()) {
                boolean becomingRequired = !destination.nullable();
                List<TableChangeCheck> operationChecks = becomingRequired
                        ? List.of(noNullsCheck(source.name()))
                        : List.of();
                TableChangeOperation operation = columnOperation(
                        TableChangeOperationType.ALTER_COLUMN_NULLABILITY,
                        effectiveSource,
                        destination,
                        TableChangeStrategy.IN_PLACE,
                        becomingRequired ? TableChangeRisk.CAUTION : TableChangeRisk.SAFE,
                        becomingRequired ? List.of(new TableChangeReason(
                                TableChangeReasonCode.DATA_PRECHECK_REQUIRED,
                                "设为非空前必须确认不存在空值"
                        )) : List.of(),
                        operationChecks
                );
                operations.add(operation);
                checks.addAll(operation.checks());
                alterStatements.add("ALTER TABLE " + table + " ALTER COLUMN " + quoteIdentifier(currentName)
                        + (destination.nullable() ? " DROP NOT NULL" : " SET NOT NULL"));
            }
        }

        for (TableColumnDefinition destination : target.columns()) {
            if (destination.columnId() != null && beforeById.containsKey(destination.columnId())) {
                continue;
            }
            if (destination.columnId() == null && columnsByName(before).containsKey(normalize(destination.name()))) {
                continue;
            }
            List<TableChangeCheck> operationChecks = destination.nullable() ? List.of() : List.of(tableEmptyCheck());
            TableChangeOperation operation = columnOperation(
                    TableChangeOperationType.ADD_COLUMN,
                    null,
                    destination,
                    TableChangeStrategy.IN_PLACE,
                    destination.nullable() ? TableChangeRisk.SAFE : TableChangeRisk.CAUTION,
                    destination.nullable() ? List.of() : List.of(new TableChangeReason(
                            TableChangeReasonCode.DATA_PRECHECK_REQUIRED,
                            "新增非空字段且没有默认值时，当前表必须为空"
                    )),
                    operationChecks
            );
            operations.add(operation);
            checks.addAll(operation.checks());
            addStatements.add("ALTER TABLE " + table + " ADD COLUMN " + quoteIdentifier(destination.name()) + " "
                    + columnTypeSql(destination) + (destination.nullable() ? "" : " NOT NULL"));
        }

        List<String> effectiveSourcePrimaryKey = effectiveSourcePrimaryKey(before, target, beforeById, targetById);
        List<String> targetPrimaryKey = target.primaryKeyColumns();
        boolean primaryKeyDefinitionChanged = !sameColumns(effectiveSourcePrimaryKey, targetPrimaryKey);
        boolean primaryKeyRecreateRequired = primaryKeyDefinitionChanged || !changedPrimaryKeyColumns.isEmpty();
        if (primaryKeyDefinitionChanged) {
            TableChangeOperationType type = effectiveSourcePrimaryKey.isEmpty()
                    ? TableChangeOperationType.ADD_PRIMARY_KEY
                    : targetPrimaryKey.isEmpty() ? TableChangeOperationType.DROP_PRIMARY_KEY : TableChangeOperationType.REPLACE_PRIMARY_KEY;
            List<TableChangeCheck> primaryChecks = primaryKeyChecks(target, beforeById, targetById);
            if (!effectiveSourcePrimaryKey.isEmpty()) {
                primaryChecks = append(primaryChecks, externalDependencyCheck());
            }
            TableChangeOperation operation = primaryKeyOperation(
                    type,
                    effectiveSourcePrimaryKey,
                    targetPrimaryKey,
                    TableChangeStrategy.IN_PLACE,
                    TableChangeRisk.CAUTION,
                    List.of(new TableChangeReason(
                            TableChangeReasonCode.KEY_CONSTRAINT_CHANGE,
                            "修改主键前需要检查空值、重复值和外部依赖"
                    )),
                    primaryChecks
            );
            operations.add(operation);
            checks.addAll(operation.checks());
        }
        if (!changedPrimaryKeyColumns.isEmpty()) {
            checks.add(externalDependencyCheck());
            reasons.add(new TableChangeReason(
                    TableChangeReasonCode.KEY_CONSTRAINT_CHANGE,
                    "修改主键字段类型前需要临时删除并重新创建主键约束"
            ));
        }

        if (operations.isEmpty()) {
            return new TableChangePlan(
                    before, target, TableChangeStrategy.METADATA_ONLY, TableChangeRisk.SAFE,
                    TableDdlAtomicity.NOT_APPLICABLE, List.of(), List.of(), List.of(), List.of()
            );
        }
        if (primaryKeyRecreateRequired && actual.primaryKey().name() == null && !effectiveSourcePrimaryKey.isEmpty()) {
            unsupported = true;
            reasons.add(new TableChangeReason(
                    TableChangeReasonCode.KEY_CONSTRAINT_CHANGE,
                    "无法读取当前主键约束名称，平台不能安全修改主键"
            ));
        }
        if (unsupported) {
            return new TableChangePlan(
                    before, target, TableChangeStrategy.UNSUPPORTED, highestRisk(operations),
                    TableDdlAtomicity.NOT_APPLICABLE, operations, List.copyOf(checks), List.copyOf(reasons), List.of()
            );
        }

        List<String> statements = new ArrayList<>(renameStatements);
        if (primaryKeyRecreateRequired && !effectiveSourcePrimaryKey.isEmpty()) {
            statements.add("ALTER TABLE " + table + " DROP CONSTRAINT " + quoteIdentifier(actual.primaryKey().name()));
        }
        statements.addAll(alterStatements);
        statements.addAll(addStatements);
        statements.addAll(dropStatements);
        if (primaryKeyRecreateRequired && !targetPrimaryKey.isEmpty()) {
            statements.add("ALTER TABLE " + table + " ADD PRIMARY KEY (" + targetPrimaryKey.stream()
                    .map(this::quoteIdentifier).collect(java.util.stream.Collectors.joining(", ")) + ")");
        }
        return new TableChangePlan(
                before, target, TableChangeStrategy.IN_PLACE, highestRisk(operations),
                TableDdlAtomicity.TRANSACTIONAL_BATCH, operations, List.copyOf(checks), List.copyOf(reasons),
                List.of(new TableChangeExecutionOption(
                        TableChangeExecutionMode.IN_PLACE, TableDdlAtomicity.TRANSACTIONAL_BATCH, statements
                ))
        );
    }

    @Override
    public boolean checkTableChange(Connection connection, TableIdentifier table, TableChangeCheck check) throws SQLException {
        if (check.type() == TableChangeCheckType.STRUCTURE_FINGERPRINT_MATCH) {
            throw new IllegalArgumentException("Structure fingerprint is checked by the table operator");
        }
        if (check.type() == TableChangeCheckType.DATABASE_RUNTIME_SUPPORTED) {
            return true;
        }
        String qualifiedTable = qualifiedName(table);
        String sql = switch (check.type()) {
            case TABLE_EMPTY -> "SELECT NOT EXISTS (SELECT 1 FROM " + qualifiedTable + " LIMIT 1)";
            case COLUMNS_HAVE_NO_NULLS -> "SELECT NOT EXISTS (SELECT 1 FROM " + qualifiedTable + " WHERE "
                    + check.columnNames().stream().map(name -> quoteIdentifier(name) + " IS NULL")
                    .collect(java.util.stream.Collectors.joining(" OR ")) + ")";
            case COLUMNS_ARE_UNIQUE -> "SELECT NOT EXISTS (SELECT 1 FROM " + qualifiedTable + " GROUP BY "
                    + check.columnNames().stream().map(this::quoteIdentifier)
                    .collect(java.util.stream.Collectors.joining(", ")) + " HAVING COUNT(*) > 1)";
            case MAX_STRING_LENGTH -> "SELECT COALESCE(MAX(char_length(" + quoteIdentifier(check.columnNames().getFirst())
                    + ")), 0) <= " + check.lengthLimit() + " FROM " + qualifiedTable;
            case DECIMAL_VALUES_FIT -> decimalFitSql(qualifiedTable, check);
            case NO_EXTERNAL_DEPENDENCIES -> noExternalDependencySql(catalogNames(connection), table);
            case NO_REBUILD_DEPENDENCIES -> noRebuildDependencySql(catalogNames(connection), table);
            case STRUCTURE_FINGERPRINT_MATCH, DATABASE_RUNTIME_SUPPORTED -> throw new IllegalStateException("Unexpected check type");
        };
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() && resultSet.getBoolean(1);
        }
    }

    private TableChangePlan planRebuild(TableDefinition before, TableDefinition target, TableMetadata actual) {
        List<TableChangeOperation> operations = new ArrayList<>();
        LinkedHashSet<TableChangeCheck> checks = new LinkedHashSet<>();
        List<TableChangeReason> reasons = new ArrayList<>();
        Map<UUID, TableColumnDefinition> beforeById = columnsById(before, "source");
        Map<UUID, TableColumnDefinition> targetById = columnsById(target, "target");
        Map<String, TableColumnDefinition> beforeByName = columnsByName(before);
        Map<String, TableColumnDefinition> targetByName = columnsByName(target);
        boolean containsUnsupportedConversion = false;

        checks.add(structureCheck(before));
        checks.add(rebuildDependenciesCheck());

        for (TableColumnDefinition source : before.columns()) {
            TableColumnDefinition destination = destinationFor(source, targetById, targetByName);
            if (destination == null) {
                TableChangeOperation operation = columnOperation(
                        TableChangeOperationType.DROP_COLUMN,
                        source,
                        null,
                        TableChangeStrategy.REBUILD_REQUIRED,
                        TableChangeRisk.DESTRUCTIVE,
                        List.of(new TableChangeReason(
                                TableChangeReasonCode.POSSIBLE_DATA_LOSS,
                                "重建后不会复制已删除字段中的数据"
                        )),
                        List.of()
                );
                addRebuildOperation(operations, checks, reasons, operation);
                continue;
            }

            TableColumnDefinition effectiveSource = source;
            if (!source.name().equalsIgnoreCase(destination.name())) {
                TableChangeOperation operation = columnOperation(
                        TableChangeOperationType.RENAME_COLUMN,
                        source,
                        destination,
                        TableChangeStrategy.REBUILD_REQUIRED,
                        TableChangeRisk.SAFE,
                        List.of(new TableChangeReason(
                                TableChangeReasonCode.STORAGE_LAYOUT_CHANGE,
                                "字段将在重建表的数据复制过程中改名"
                        )),
                        List.of()
                );
                addRebuildOperation(operations, checks, reasons, operation);
                effectiveSource = renamedSource(source, destination);
            }

            if (source.type() != destination.type()) {
                boolean supported = supportsRebuildConversion(source.type(), destination.type());
                TableChangeOperation operation = columnOperation(
                        TableChangeOperationType.ALTER_COLUMN_TYPE,
                        effectiveSource,
                        destination,
                        supported ? TableChangeStrategy.REBUILD_REQUIRED : TableChangeStrategy.UNSUPPORTED,
                        supported ? TableChangeRisk.CAUTION : TableChangeRisk.DESTRUCTIVE,
                        List.of(new TableChangeReason(
                                supported ? TableChangeReasonCode.DATA_CONVERSION_REQUIRED
                                        : TableChangeReasonCode.PHYSICAL_TYPE_UNSUPPORTED,
                                supported
                                        ? "字段类型将在复制到影子表时由 " + databaseDisplayName
                                        + " 转换；无法转换的数据会使整个重建事务回滚"
                                        : "当前 " + databaseDisplayName + " 重建规则不能定义该字段类型转换"
                        )),
                        rebuildConversionChecks(source, destination)
                );
                addRebuildOperation(operations, checks, reasons, operation);
                containsUnsupportedConversion |= !supported;
            } else {
                addSameTypeRebuildOperations(source, destination, effectiveSource, operations, checks, reasons);
            }

            if (source.nullable() != destination.nullable()) {
                boolean becomingRequired = !destination.nullable();
                TableChangeOperation operation = columnOperation(
                        TableChangeOperationType.ALTER_COLUMN_NULLABILITY,
                        effectiveSource,
                        destination,
                        TableChangeStrategy.REBUILD_REQUIRED,
                        becomingRequired ? TableChangeRisk.CAUTION : TableChangeRisk.SAFE,
                        becomingRequired ? List.of(new TableChangeReason(
                                TableChangeReasonCode.DATA_PRECHECK_REQUIRED,
                                "重建前必须确认该字段不存在空值"
                        )) : List.of(),
                        becomingRequired ? List.of(noNullsCheck(source.name())) : List.of()
                );
                addRebuildOperation(operations, checks, reasons, operation);
            }
        }

        for (TableColumnDefinition destination : target.columns()) {
            if (sourceFor(destination, beforeById, beforeByName) != null) {
                continue;
            }
            List<TableChangeCheck> operationChecks = destination.nullable() ? List.of() : List.of(tableEmptyCheck());
            TableChangeOperation operation = columnOperation(
                    TableChangeOperationType.ADD_COLUMN,
                    null,
                    destination,
                    TableChangeStrategy.REBUILD_REQUIRED,
                    destination.nullable() ? TableChangeRisk.SAFE : TableChangeRisk.CAUTION,
                    destination.nullable() ? List.of() : List.of(new TableChangeReason(
                            TableChangeReasonCode.DATA_PRECHECK_REQUIRED,
                            "新增非空字段但没有默认值时，源表必须为空"
                    )),
                    operationChecks
            );
            addRebuildOperation(operations, checks, reasons, operation);
        }

        List<String> effectiveSourcePrimaryKey = effectiveSourcePrimaryKey(before, target, beforeById, targetById);
        if (!sameColumns(effectiveSourcePrimaryKey, target.primaryKeyColumns())) {
            TableChangeOperationType type = effectiveSourcePrimaryKey.isEmpty()
                    ? TableChangeOperationType.ADD_PRIMARY_KEY
                    : target.primaryKeyColumns().isEmpty()
                    ? TableChangeOperationType.DROP_PRIMARY_KEY : TableChangeOperationType.REPLACE_PRIMARY_KEY;
            TableChangeOperation operation = primaryKeyOperation(
                    type,
                    effectiveSourcePrimaryKey,
                    target.primaryKeyColumns(),
                    TableChangeStrategy.REBUILD_REQUIRED,
                    TableChangeRisk.CAUTION,
                    List.of(new TableChangeReason(
                            TableChangeReasonCode.KEY_CONSTRAINT_CHANGE,
                            "主键将在影子表创建时按目标定义重建"
                    )),
                    primaryKeyChecks(target, beforeById, targetById)
            );
            addRebuildOperation(operations, checks, reasons, operation);
        }

        if (containsUnsupportedConversion) {
            return new TableChangePlan(
                    before, target, TableChangeStrategy.UNSUPPORTED, highestRisk(operations),
                    TableDdlAtomicity.NOT_APPLICABLE, operations, List.copyOf(checks), List.copyOf(reasons), List.of()
            );
        }

        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        TableIdentifier shadowTable = siblingTable(before.table(), "__dsc_new_" + token);
        TableIdentifier backupTable = siblingTable(before.table(), "__dsc_old_" + token);
        TableDefinition shadowDefinition = new TableDefinition(shadowTable, target.columns(), target.primaryKeyColumns());
        List<String> targetColumns = target.columns().stream().map(column -> quoteIdentifier(column.name())).toList();
        List<String> copyExpressions = target.columns().stream()
                .map(destination -> rebuildCopyExpression(sourceFor(destination, beforeById, beforeByName), destination))
                .toList();
        String sourceTable = qualifiedName(before.table());
        String shadowTableName = qualifiedName(shadowTable);
        List<String> statements = List.of(
                "LOCK TABLE " + sourceTable + " IN ACCESS EXCLUSIVE MODE",
                planCreateTable(shadowDefinition).statements().getFirst(),
                "INSERT INTO " + shadowTableName + " (" + String.join(", ", targetColumns) + ") SELECT "
                        + String.join(", ", copyExpressions) + " FROM " + sourceTable,
                "ALTER TABLE " + sourceTable + " RENAME TO " + quoteIdentifier(backupTable.table()),
                "ALTER TABLE " + shadowTableName + " RENAME TO " + quoteIdentifier(before.table().table()),
                "DROP TABLE " + qualifiedName(backupTable)
        );
        return new TableChangePlan(
                before, target, TableChangeStrategy.REBUILD_REQUIRED, highestRisk(operations),
                TableDdlAtomicity.TRANSACTIONAL_BATCH, operations, List.copyOf(checks), List.copyOf(reasons),
                List.of(new TableChangeExecutionOption(
                        TableChangeExecutionMode.REBUILD, TableDdlAtomicity.TRANSACTIONAL_BATCH, statements
                ))
        );
    }

    private static boolean requiresRebuild(TableDefinition before, TableDefinition target) {
        Map<UUID, TableColumnDefinition> targetById = columnsById(target, "target");
        Map<String, TableColumnDefinition> targetByName = columnsByName(target);
        for (TableColumnDefinition source : before.columns()) {
            TableColumnDefinition destination = destinationFor(source, targetById, targetByName);
            if (destination != null && source.type() != destination.type()
                    && !supportsInPlaceTypeChange(source.type(), destination.type())) {
                return true;
            }
        }
        return false;
    }

    private static boolean supportsInPlaceTypeChange(TableColumnType source, TableColumnType destination) {
        return ((source == TableColumnType.BYTE || source == TableColumnType.SHORT)
                    && (destination == TableColumnType.INTEGER || destination == TableColumnType.LONG))
                || (source == TableColumnType.INTEGER && destination == TableColumnType.LONG)
                || (source == TableColumnType.FLOAT && destination == TableColumnType.DOUBLE)
                || (source == TableColumnType.STRING && destination == TableColumnType.TEXT);
    }

    private static boolean supportsRebuildConversion(TableColumnType source, TableColumnType destination) {
        if (source == TableColumnType.BINARY || destination == TableColumnType.BINARY) {
            return false;
        }
        if (isStringLike(source) || isStringLike(destination)) {
            return true;
        }
        if (isNumeric(source) && isNumeric(destination)) {
            return true;
        }
        return isDateTimeLike(source) && isDateTimeLike(destination);
    }

    private static boolean isStringLike(TableColumnType type) {
        return type == TableColumnType.STRING || type == TableColumnType.TEXT;
    }

    private static boolean isNumeric(TableColumnType type) {
        return type == TableColumnType.BYTE
                || type == TableColumnType.SHORT
                || type == TableColumnType.INTEGER
                || type == TableColumnType.LONG
                || type == TableColumnType.FLOAT
                || type == TableColumnType.DOUBLE
                || type == TableColumnType.DECIMAL;
    }

    private static boolean isDateTimeLike(TableColumnType type) {
        return type == TableColumnType.DATE
                || type == TableColumnType.TIMESTAMP
                || type == TableColumnType.TIMESTAMP_NTZ
                || type == TableColumnType.DATETIME;
    }

    private static TableColumnDefinition destinationFor(
            TableColumnDefinition source,
            Map<UUID, TableColumnDefinition> targetById,
            Map<String, TableColumnDefinition> targetByName
    ) {
        return source.columnId() == null ? targetByName.get(normalize(source.name())) : targetById.get(source.columnId());
    }

    private static TableColumnDefinition sourceFor(
            TableColumnDefinition destination,
            Map<UUID, TableColumnDefinition> beforeById,
            Map<String, TableColumnDefinition> beforeByName
    ) {
        return destination.columnId() == null
                ? beforeByName.get(normalize(destination.name()))
                : beforeById.get(destination.columnId());
    }

    private static TableColumnDefinition renamedSource(TableColumnDefinition source, TableColumnDefinition destination) {
        return new TableColumnDefinition(
                destination.name(), source.type(), source.length(), source.precision(), source.scale(),
                source.nullable(), source.columnId()
        );
    }

    private static void addRebuildOperation(
            List<TableChangeOperation> operations,
            LinkedHashSet<TableChangeCheck> checks,
            List<TableChangeReason> reasons,
            TableChangeOperation operation
    ) {
        operations.add(operation);
        checks.addAll(operation.checks());
        reasons.addAll(operation.reasons());
    }

    private static List<TableChangeCheck> rebuildConversionChecks(
            TableColumnDefinition source,
            TableColumnDefinition destination
    ) {
        List<TableChangeCheck> checks = new ArrayList<>();
        if (destination.type() == TableColumnType.STRING && destination.length() != null && isStringLike(source.type())) {
            checks.add(maxStringLengthCheck(source.name(), destination.length()));
        }
        if (destination.type() == TableColumnType.DECIMAL && isNumeric(source.type())) {
            checks.add(decimalFitsCheck(source.name(), destination.precision(), destination.scale()));
        }
        if (!destination.nullable()) {
            checks.add(noNullsCheck(source.name()));
        }
        return List.copyOf(checks);
    }

    private static void addSameTypeRebuildOperations(
            TableColumnDefinition source,
            TableColumnDefinition destination,
            TableColumnDefinition effectiveSource,
            List<TableChangeOperation> operations,
            LinkedHashSet<TableChangeCheck> checks,
            List<TableChangeReason> reasons
    ) {
        if (source.type() == TableColumnType.STRING && !source.length().equals(destination.length())) {
            boolean shrinking = destination.length() < source.length();
            TableChangeOperation operation = columnOperation(
                    TableChangeOperationType.ALTER_COLUMN_LENGTH,
                    effectiveSource,
                    destination,
                    TableChangeStrategy.REBUILD_REQUIRED,
                    shrinking ? TableChangeRisk.CAUTION : TableChangeRisk.SAFE,
                    shrinking ? List.of(new TableChangeReason(
                            TableChangeReasonCode.DATA_PRECHECK_REQUIRED,
                            "缩短字符串长度前必须确认现有值不会超出目标长度"
                    )) : List.of(),
                    shrinking ? List.of(maxStringLengthCheck(source.name(), destination.length())) : List.of()
            );
            addRebuildOperation(operations, checks, reasons, operation);
        }
        if (source.type() == TableColumnType.DECIMAL
                && (!source.precision().equals(destination.precision()) || !source.scale().equals(destination.scale()))) {
            boolean narrowing = destination.scale() < source.scale()
                    || destination.precision() - destination.scale() < source.precision() - source.scale();
            TableChangeOperation operation = columnOperation(
                    TableChangeOperationType.ALTER_COLUMN_PRECISION,
                    effectiveSource,
                    destination,
                    TableChangeStrategy.REBUILD_REQUIRED,
                    narrowing ? TableChangeRisk.CAUTION : TableChangeRisk.SAFE,
                    narrowing ? List.of(new TableChangeReason(
                            TableChangeReasonCode.DATA_PRECHECK_REQUIRED,
                            "调整小数精度前必须确认现有数据可以放入目标精度"
                    )) : List.of(),
                    narrowing ? List.of(decimalFitsCheck(source.name(), destination.precision(), destination.scale())) : List.of()
            );
            addRebuildOperation(operations, checks, reasons, operation);
        }
    }

    private String rebuildCopyExpression(TableColumnDefinition source, TableColumnDefinition destination) {
        if (source == null) {
            return "CAST(NULL AS " + columnTypeSql(destination) + ")";
        }
        String sourceExpression = quoteIdentifier(source.name());
        if (source.type() == destination.type()
                && java.util.Objects.equals(source.length(), destination.length())
                && java.util.Objects.equals(source.precision(), destination.precision())
                && java.util.Objects.equals(source.scale(), destination.scale())) {
            return sourceExpression;
        }
        return "CAST(" + sourceExpression + " AS " + columnTypeSql(destination) + ")";
    }

    private static TableIdentifier siblingTable(TableIdentifier source, String tableName) {
        return new TableIdentifier(source.catalog(), source.schema(), tableName);
    }

    private TypeChangeResult classifyTypeChange(
            TableColumnDefinition source,
            TableColumnDefinition target,
            String currentName
    ) {
        if (source.type() == target.type()) {
            return TypeChangeResult.unchanged();
        }
        if ((source.type() == TableColumnType.INTEGER && target.type() == TableColumnType.LONG)
                || (source.type() == TableColumnType.STRING && target.type() == TableColumnType.TEXT)) {
            return TypeChangeResult.safe();
        }
        if (source.type() == TableColumnType.TEXT && target.type() == TableColumnType.STRING) {
            return new TypeChangeResult(false, true, TableChangeRisk.CAUTION,
                    List.of(new TableChangeReason(
                            TableChangeReasonCode.DATA_PRECHECK_REQUIRED,
                            "从长文本转换为字符串前必须确认现有值不会超出目标长度"
                    )),
                    List.of(maxStringLengthCheck(source.name(), target.length())));
        }
        return TypeChangeResult.unsupportedResult();
    }

    private static Map<UUID, TableColumnDefinition> columnsById(TableDefinition definition, String label) {
        Map<UUID, TableColumnDefinition> columns = new HashMap<>();
        for (TableColumnDefinition column : definition.columns()) {
            if (column.columnId() == null) {
                continue;
            }
            if (columns.put(column.columnId(), column) != null) {
                throw new IllegalArgumentException("Duplicate " + label + " column identity: " + column.columnId());
            }
        }
        return columns;
    }

    private static Map<String, TableColumnDefinition> columnsByName(TableDefinition definition) {
        Map<String, TableColumnDefinition> columns = new HashMap<>();
        for (TableColumnDefinition column : definition.columns()) {
            columns.put(normalize(column.name()), column);
        }
        return columns;
    }

    private static Set<UUID> primaryKeyIds(TableDefinition definition, Map<UUID, TableColumnDefinition> columnsById) {
        Set<UUID> ids = new HashSet<>();
        for (String name : definition.primaryKeyColumns()) {
            TableColumnDefinition column = definition.columns().stream()
                    .filter(candidate -> candidate.name().equalsIgnoreCase(name))
                    .findFirst()
                    .orElseThrow();
            if (column.columnId() != null) {
                ids.add(column.columnId());
            }
        }
        return ids;
    }

    private static List<String> effectiveSourcePrimaryKey(
            TableDefinition before,
            TableDefinition target,
            Map<UUID, TableColumnDefinition> beforeById,
            Map<UUID, TableColumnDefinition> targetById
    ) {
        List<String> effective = new ArrayList<>();
        for (String sourcePrimaryKey : before.primaryKeyColumns()) {
            TableColumnDefinition source = before.columns().stream()
                    .filter(column -> column.name().equalsIgnoreCase(sourcePrimaryKey))
                    .findFirst().orElseThrow();
            TableColumnDefinition targetColumn = source.columnId() == null ? null : targetById.get(source.columnId());
            effective.add(targetColumn == null ? source.name() : targetColumn.name());
        }
        return effective;
    }

    private static List<TableChangeCheck> primaryKeyChecks(
            TableDefinition target,
            Map<UUID, TableColumnDefinition> beforeById,
            Map<UUID, TableColumnDefinition> targetById
    ) {
        List<String> currentColumns = new ArrayList<>();
        boolean containsNewColumn = false;
        for (String targetPrimaryKey : target.primaryKeyColumns()) {
            TableColumnDefinition targetColumn = target.columns().stream()
                    .filter(column -> column.name().equalsIgnoreCase(targetPrimaryKey))
                    .findFirst().orElseThrow();
            TableColumnDefinition source = targetColumn.columnId() == null ? null : beforeById.get(targetColumn.columnId());
            if (source == null) {
                containsNewColumn = true;
            } else {
                currentColumns.add(source.name());
            }
        }
        if (containsNewColumn) {
            return List.of(tableEmptyCheck());
        }
        if (currentColumns.isEmpty()) {
            return List.of();
        }
        return List.of(
                new TableChangeCheck(TableChangeCheckType.COLUMNS_HAVE_NO_NULLS, currentColumns,
                        null, null, null, null, "主键字段不能包含空值"),
                new TableChangeCheck(TableChangeCheckType.COLUMNS_ARE_UNIQUE, currentColumns,
                        null, null, null, null, "主键字段组合必须唯一")
        );
    }

    private static boolean sameColumns(List<String> first, List<String> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            if (!first.get(index).equalsIgnoreCase(second.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static TableChangeOperation columnOperation(
            TableChangeOperationType type,
            TableColumnDefinition before,
            TableColumnDefinition after,
            TableChangeStrategy strategy,
            TableChangeRisk risk,
            List<TableChangeReason> reasons,
            List<TableChangeCheck> checks
    ) {
        return new TableChangeOperation(type, before, after, List.of(), List.of(), strategy, risk, reasons, checks);
    }

    private static TableChangeOperation primaryKeyOperation(
            TableChangeOperationType type,
            List<String> before,
            List<String> after,
            TableChangeStrategy strategy,
            TableChangeRisk risk,
            List<TableChangeReason> reasons,
            List<TableChangeCheck> checks
    ) {
        return new TableChangeOperation(type, null, null, before, after, strategy, risk, reasons, checks);
    }

    private String alterColumnType(String table, String columnName, TableColumnDefinition target) {
        return "ALTER TABLE " + table + " ALTER COLUMN " + quoteIdentifier(columnName) + " TYPE " + columnTypeSql(target);
    }

    private static TableChangeCheck structureCheck(TableDefinition before) {
        return new TableChangeCheck(
                TableChangeCheckType.STRUCTURE_FINGERPRINT_MATCH,
                List.of(), null, null, null, before.structureFingerprint(), "执行前必须确认物理表结构未发生漂移"
        );
    }

    private static TableChangeCheck tableEmptyCheck() {
        return new TableChangeCheck(TableChangeCheckType.TABLE_EMPTY, List.of(), null, null, null, null,
                "当前表必须为空");
    }

    private static TableChangeCheck noNullsCheck(String columnName) {
        return new TableChangeCheck(TableChangeCheckType.COLUMNS_HAVE_NO_NULLS, List.of(columnName),
                null, null, null, null, "字段不能包含空值");
    }

    private static TableChangeCheck maxStringLengthCheck(String columnName, Integer length) {
        return new TableChangeCheck(TableChangeCheckType.MAX_STRING_LENGTH, List.of(columnName),
                length, null, null, null, "字段值长度不能超过 " + length);
    }

    private static TableChangeCheck decimalFitsCheck(String columnName, Integer precision, Integer scale) {
        return new TableChangeCheck(TableChangeCheckType.DECIMAL_VALUES_FIT, List.of(columnName),
                null, precision, scale, null, "字段值必须能放入目标小数精度");
    }

    private static TableChangeCheck externalDependencyCheck() {
        return new TableChangeCheck(TableChangeCheckType.NO_EXTERNAL_DEPENDENCIES, List.of(),
                null, null, null, null, "不能存在引用当前表的外部外键约束");
    }

    private static TableChangeCheck rebuildDependenciesCheck() {
        return new TableChangeCheck(TableChangeCheckType.NO_REBUILD_DEPENDENCIES, List.of(),
                null, null, null, null, "重建表前不能存在平台无法安全重建的外键、索引、约束、触发器、规则或视图依赖");
    }

    private static List<TableChangeCheck> append(List<TableChangeCheck> checks, TableChangeCheck value) {
        List<TableChangeCheck> result = new ArrayList<>(checks);
        result.add(value);
        return result;
    }

    private static TableChangeRisk highestRisk(List<TableChangeOperation> operations) {
        TableChangeRisk risk = TableChangeRisk.SAFE;
        for (TableChangeOperation operation : operations) {
            if (operation.risk().isAtLeastAsSevereAs(risk)) {
                risk = operation.risk();
            }
        }
        return risk;
    }

    private String decimalFitSql(String table, TableChangeCheck check) {
        String column = quoteIdentifier(check.columnNames().getFirst());
        int integerDigits = check.precisionLimit() - check.scaleLimit();
        return "SELECT NOT EXISTS (SELECT 1 FROM " + table + " WHERE " + column + " IS NOT NULL AND (ABS("
                + column + ") >= POWER(10, " + integerDigits + ") OR " + column + " <> ROUND(" + column + ", "
                + check.scaleLimit() + ")))";
    }

    private static String noExternalDependencySql(
            PostgreSqlCatalogNames catalogs,
            TableIdentifier table
    ) {
        String schemaPredicate = table.schema() == null
                ? "target_schema.nspname = current_schema()"
                : "target_schema.nspname = " + stringLiteral(table.schema());
        return "SELECT NOT EXISTS (SELECT 1 FROM " + catalogs.relation("constraint") + " foreign_key "
                + "JOIN " + catalogs.relation("class")
                + " target_table ON foreign_key.confrelid = target_table.oid "
                + "JOIN " + catalogs.relation("namespace")
                + " target_schema ON target_table.relnamespace = target_schema.oid "
                + "WHERE foreign_key.contype = 'f' AND target_table.relname = " + stringLiteral(table.table())
                + " AND " + schemaPredicate + ")";
    }

    private static String noRebuildDependencySql(
            PostgreSqlCatalogNames catalogs,
            TableIdentifier table
    ) {
        String schemaPredicate = table.schema() == null
                ? "target_schema.nspname = current_schema()"
                : "target_schema.nspname = " + stringLiteral(table.schema());
        String target = "WITH target AS (SELECT target_table.oid FROM " + catalogs.relation("class")
                + " target_table JOIN " + catalogs.relation("namespace")
                + " target_schema ON target_table.relnamespace = target_schema.oid "
                + "WHERE target_table.relname = " + stringLiteral(table.table()) + " AND " + schemaPredicate + ") ";
        return target + "SELECT NOT EXISTS ("
                + "SELECT 1 FROM " + catalogs.relation("constraint") + " constraint_definition JOIN target ON "
                + "(constraint_definition.conrelid = target.oid OR constraint_definition.confrelid = target.oid) "
                + "WHERE constraint_definition.contype IN ('f', 'c', 'u', 'x') "
                + "UNION ALL SELECT 1 FROM " + catalogs.relation("index")
                + " secondary_index JOIN target ON secondary_index.indrelid = target.oid "
                + "WHERE NOT secondary_index.indisprimary "
                + "UNION ALL SELECT 1 FROM " + catalogs.relation("trigger")
                + " user_trigger JOIN target ON user_trigger.tgrelid = target.oid "
                + "WHERE NOT user_trigger.tgisinternal "
                + "UNION ALL SELECT 1 FROM " + catalogs.relation("rewrite")
                + " user_rule JOIN target ON user_rule.ev_class = target.oid "
                + "WHERE user_rule.rulename <> '_RETURN' "
                + "UNION ALL SELECT 1 FROM " + catalogs.relation("rewrite") + " view_rule "
                + "JOIN " + catalogs.relation("class") + " dependent_view ON dependent_view.oid = view_rule.ev_class "
                + "JOIN " + catalogs.relation("depend") + " dependency ON dependency.objid = view_rule.oid "
                + "JOIN target ON dependency.refobjid = target.oid "
                + "WHERE dependent_view.relkind IN ('v', 'm') AND dependent_view.oid <> target.oid)";
    }

    private static String stringLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static TypeMappingResult<PlatformTypeDefinition> mapGeometryToPlatform(
            JdbcTypeDescriptor physicalType
    ) {
        SpatialColumnMetadata spatial = physicalType.spatial();
        if (spatial == null) {
            return TypeMappingResult.unsupported("PostGIS Geometry 缺少 subtype、SRID 和维度元数据");
        }
        if (!spatial.subtypeConstrained()) {
            return TypeMappingResult.unsupported("PostGIS Geometry 列没有稳定的 subtype 约束");
        }
        GeometryKind kind = SpatialTypeSupport.geometryKind(spatial.nativeGeometryKind());
        if (kind == null) {
            return TypeMappingResult.unsupported("不支持 PostGIS Geometry 类型：" + spatial.nativeGeometryKind());
        }
        if (!spatial.crsConstrained() || spatial.spatialReferenceId() == null
                || spatial.spatialReferenceId() < 1) {
            return TypeMappingResult.unsupported("PostGIS Geometry 列没有固定 SRID");
        }
        if (!"EPSG".equals(spatial.crsAuthority()) || spatial.crsCode() == null
                || spatial.crsCode() < 1) {
            return TypeMappingResult.unsupported("PostGIS Geometry SRID 无法解析为 EPSG CRS");
        }
        if (spatial.coordinateDimension() != CoordinateDimension.XY) {
            return TypeMappingResult.unsupported("空间字段第一版只支持 XY 二维坐标");
        }
        return TypeMappingResult.exact(PlatformTypeDefinition.geometry(new GeometryTypeDefinition(
                kind,
                CrsReference.epsg(spatial.crsCode()),
                CoordinateDimension.XY
        )));
    }

    private static boolean isPostGisGeometryNativeType(ColumnMetadata column) {
        return isPostGisGeometryNativeType(column.nativeType());
    }

    private static boolean isPostGisGeometryNativeType(String nativeType) {
        return "geometry".equals(postGisNativeType(nativeType));
    }

    private static String postGisNativeType(String nativeType) {
        if (nativeType == null || nativeType.isBlank()) {
            return "";
        }
        String unquoted = nativeType.trim().replace("\"", "");
        int separator = unquoted.lastIndexOf('.');
        return (separator < 0 ? unquoted : unquoted.substring(separator + 1)).trim().toLowerCase(Locale.ROOT);
    }

    private static ColumnMetadata unresolvedSpatialColumn(ColumnMetadata column) {
        if (!isPostGisGeometryNativeType(column)) {
            return column;
        }
        return column.withSpatial(new SpatialColumnMetadata(
                column.nativeType(), null, null, null, null, false, false
        ));
    }

    private static String normalizeSpatialName(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private Set<String> spatialPreviewIndexedColumns(
            Connection connection,
            TableIdentifier table,
            Duration timeout
    ) throws SQLException {
        PostgreSqlCatalogNames catalogs = catalogNames(connection);
        String sql = """
                SELECT attribute.attname
                  FROM %s target
                  JOIN %s namespace ON namespace.oid = target.relnamespace
                  JOIN %s index_definition ON index_definition.indrelid = target.oid
                  JOIN %s index_relation ON index_relation.oid = index_definition.indexrelid
                  JOIN %s access_method ON access_method.oid = index_relation.relam
                  JOIN %s attribute
                    ON attribute.attrelid = target.oid
                   AND attribute.attnum = ANY(index_definition.indkey)
                 WHERE namespace.nspname = ?
                   AND target.relname = ?
                   AND access_method.amname IN ('gist', 'spgist')
                   AND index_definition.indisvalid
                   AND index_definition.indisready
                   AND index_definition.indpred IS NULL
                   AND index_definition.indexprs IS NULL
                   AND index_definition.indnkeyatts = 1
                   AND index_definition.indnatts = 1
                """.formatted(
                catalogs.relation("class"),
                catalogs.relation("namespace"),
                catalogs.relation("index"),
                catalogs.relation("class"),
                catalogs.relation("am"),
                catalogs.relation("attribute")
        );
        Set<String> result = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(TableStatisticsJdbcSupport.timeoutSeconds(timeout));
            statement.setString(1, table.schema());
            statement.setString(2, table.table());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(normalizeSpatialName(resultSet.getString(1)));
                }
            }
        }
        return Set.copyOf(result);
    }

    private int resolveSpatialPreviewSrid(
            Connection connection,
            String extensionSchema,
            int epsgCode,
            Duration timeout
    ) throws SQLException {
        String sql = "SELECT srid FROM " + quoteIdentifier(extensionSchema)
                + "." + quoteIdentifier("spatial_ref_sys")
                + " WHERE UPPER(auth_name) = 'EPSG' AND auth_srid = ?";
        Integer srid = null;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(TableStatisticsJdbcSupport.timeoutSeconds(timeout));
            statement.setInt(1, epsgCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    if (srid != null) {
                        throw new IllegalArgumentException(
                                "目标 PostGIS 中 EPSG:" + epsgCode + " 对应多个本地 SRID"
                        );
                    }
                    srid = resultSet.getInt(1);
                }
            }
        }
        if (srid == null) {
            throw new IllegalArgumentException("目标 PostGIS 中不存在 EPSG:" + epsgCode);
        }
        return srid;
    }

    private String postGisSchema(Connection connection) throws SQLException {
        return postGisSchema(connection, null);
    }

    private String postGisSchema(Connection connection, Duration timeout) throws SQLException {
        PostgreSqlCatalogNames catalogs = catalogNames(connection);
        String sql = """
                SELECT namespace.nspname
                  FROM %s extension
                  JOIN %s namespace ON namespace.oid = extension.extnamespace
                 WHERE extension.extname = 'postgis'
                """.formatted(catalogs.relation("extension"), catalogs.relation("namespace"));
        try (Statement statement = connection.createStatement()) {
            if (timeout != null) {
                statement.setQueryTimeout(TableStatisticsJdbcSupport.timeoutSeconds(timeout));
            }
            try (ResultSet resultSet = statement.executeQuery(sql)) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        } catch (SQLException exception) {
            if ("42P01".equals(exception.getSQLState()) || "42703".equals(exception.getSQLState())) {
                return null;
            }
            throw exception;
        }
    }

    private void appendPrimaryKey(TableDefinition definition, List<String> clauses) {
        if (definition.primaryKeyColumns().isEmpty()) {
            return;
        }
        String primaryKeys = definition.primaryKeyColumns().stream()
                .map(this::quoteIdentifier)
                .collect(java.util.stream.Collectors.joining(", "));
        clauses.add("PRIMARY KEY (" + primaryKeys + ")");
    }

    private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private record TypeChangeResult(
            boolean changed,
            boolean unsupported,
            TableChangeRisk risk,
            List<TableChangeReason> reasons,
            List<TableChangeCheck> checks
    ) {
        private static TypeChangeResult unchanged() {
            return new TypeChangeResult(false, false, TableChangeRisk.SAFE, List.of(), List.of());
        }

        private static TypeChangeResult safe() {
            return new TypeChangeResult(true, false, TableChangeRisk.SAFE, List.of(), List.of());
        }

        private static TypeChangeResult unsupportedResult() {
            return new TypeChangeResult(true, true, TableChangeRisk.CAUTION, List.of(), List.of());
        }
    }
}
