package cn.superhuang.data.scalpel.dialect.builtin;

import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionChoice;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionDefinition;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionType;
import cn.superhuang.data.scalpel.dialect.api.NamespaceMode;
import cn.superhuang.data.scalpel.dialect.api.JdbcIncrementalReadDialect;
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
import cn.superhuang.data.scalpel.dialect.model.TableColumnDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableColumnType;
import cn.superhuang.data.scalpel.dialect.model.TableDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TablePhysicalStatistics;
import cn.superhuang.data.scalpel.dialect.model.TableStatisticQuality;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

public final class MySqlDialect extends AbstractJdbcDialect implements JdbcIncrementalReadDialect {

    public MySqlDialect() {
        super(
                "MYSQL", "MySQL", 3306,
                "数据库", "Schema", null, NamespaceMode.CATALOG,
                List.of(
                        booleanOption("useSSL", "使用 SSL", "false"),
                        new ConnectionOptionDefinition("serverTimezone", "服务端时区", ConnectionOptionType.TEXT, "UTC", List.of())
                ),
                "com.mysql.cj.jdbc.Driver", "`", "`", QualificationMode.CATALOG, PreviewStyle.LIMIT
        );
    }

    @Override
    public JdbcConnectionSpec createConnectionSpec(JdbcConnectionConfig config) {
        Properties properties = baseProperties(config);
        properties.setProperty("connectTimeout", "5000");
        properties.setProperty("socketTimeout", "15000");
        properties.setProperty("useUnicode", "true");
        properties.setProperty("characterEncoding", "UTF-8");
        properties.setProperty("useSSL", option(config, "useSSL", "false"));
        properties.setProperty("serverTimezone", option(config, "serverTimezone", "UTC"));
        applyConnectionOptions(
                config, properties, Set.of(),
                Set.of("connectTimeout", "socketTimeout", "useUnicode", "characterEncoding")
        );
        String url = "jdbc:mysql://" + hostForUrl(config) + ":" + config.port() + "/" + pathSegment(config.databaseName());
        return new JdbcConnectionSpec(driverClassName(), url, properties, null);
    }

    @Override
    public String resolveCatalog(JdbcConnectionConfig config, String requestedCatalog) {
        return optional(requestedCatalog) == null ? config.databaseName() : requestedCatalog.trim();
    }

    @Override
    public String resolveSchema(JdbcConnectionConfig config, String requestedSchema) {
        return null;
    }

    @Override
    public String readOnlySessionInitializationSql() {
        return "SET SESSION TRANSACTION READ ONLY";
    }

    @Override
    public java.time.Instant readDatabaseCurrentTime(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT UTC_TIMESTAMP(6)");
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next() || resultSet.getTimestamp(1) == null) {
                throw new SQLException("Database current time query returned no value");
            }
            return resultSet.getTimestamp(1).toLocalDateTime().toInstant(java.time.ZoneOffset.UTC);
        }
    }

    @Override
    public TablePhysicalStatistics readTablePhysicalStatistics(
            Connection connection,
            TableIdentifier table,
            Duration timeout
    ) throws SQLException {
        String sql = """
                SELECT TABLE_TYPE, TABLE_ROWS, COALESCE(DATA_LENGTH, 0) + COALESCE(INDEX_LENGTH, 0)
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(TableStatisticsJdbcSupport.timeoutSeconds(timeout));
            statement.setString(1, table.catalog());
            statement.setString(2, table.table());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return TablePhysicalStatistics.notFound();
                }
                if (!"BASE TABLE".equalsIgnoreCase(resultSet.getString(1))) {
                    return TablePhysicalStatistics.unsupported("普通视图没有独立物理存储统计");
                }
                return TablePhysicalStatistics.available(
                        TableStatisticsJdbcSupport.nullableLong(resultSet, 2),
                        TableStatisticQuality.ESTIMATED,
                        TableStatisticsJdbcSupport.nullableLong(resultSet, 3),
                        TableStatisticQuality.ESTIMATED
                );
            }
        }
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
        String values = columns.stream().map(MySqlDialect::upsertValue)
                .collect(java.util.stream.Collectors.joining(", "));
        Set<String> keySet = Set.copyOf(keyColumns);
        List<JdbcUpsertColumn> updates = columns.stream()
                .filter(column -> !keySet.contains(column.name())).toList();
        String update = updates.isEmpty()
                ? quoteIdentifier(keyColumns.getFirst()) + " = " + quoteIdentifier(keyColumns.getFirst())
                : updates.stream()
                .map(column -> quoteIdentifier(column.name()) + " = VALUES(" + quoteIdentifier(column.name()) + ")")
                .collect(java.util.stream.Collectors.joining(", "));
        return "INSERT INTO " + qualifiedName(target) + " (" + names + ") VALUES (" + values
                + ") ON DUPLICATE KEY UPDATE " + update;
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
        String values = columns.stream().map(MySqlDialect::snapshotValue)
                .collect(java.util.stream.Collectors.joining(", "));
        long seconds = Math.max(1L, lockTimeout.toSeconds());
        return new JdbcSnapshotSyncSql(
                List.of(
                        "SET SESSION lock_wait_timeout = " + seconds,
                        "SET SESSION innodb_lock_wait_timeout = " + seconds,
                        "LOCK TABLES " + table + " WRITE"
                ),
                "SELECT " + selectColumns + " FROM " + table,
                "DELETE FROM " + table + " WHERE " + keyPredicate,
                updateSql,
                "INSERT INTO " + table + " (" + names + ") VALUES (" + values + ")",
                "UNLOCK TABLES"
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

    private static ConnectionOptionDefinition booleanOption(String key, String label, String defaultValue) {
        return new ConnectionOptionDefinition(
                key, label, ConnectionOptionType.BOOLEAN, defaultValue,
                List.of(new ConnectionOptionChoice("true", "是"), new ConnectionOptionChoice("false", "否"))
        );
    }

    @Override
    protected String columnTypeSql(TableColumnDefinition column) {
        return switch (column.type()) {
            case BYTE -> "tinyint";
            case SHORT -> "smallint";
            case STRING -> "varchar(" + column.length() + ")";
            case TEXT -> "text";
            case INTEGER -> "int";
            case LONG -> "bigint";
            case FLOAT -> "float";
            case DOUBLE -> "double";
            case DECIMAL -> "decimal(" + column.precision() + "," + column.scale() + ")";
            case BOOLEAN -> "bit";
            case DATE -> "date";
            case TIMESTAMP -> "timestamp";
            case TIMESTAMP_NTZ, DATETIME -> "datetime";
            case BINARY -> "blob";
            case GEOMETRY -> "GEOMETRY";
        };
    }

    @Override
    protected boolean matchesGeometryColumn(TableColumnDefinition expected, ColumnMetadata actual) {
        return isMySqlSpatialColumn(actual);
    }

    @Override
    protected Optional<TypeMappingResult<PlatformTypeDefinition>> mapDialectTypeToPlatform(
            JdbcTypeDescriptor physicalType
    ) {
        if (!isMySqlSpatialType(physicalType.nativeTypeName())) {
            return Optional.empty();
        }
        SpatialColumnMetadata spatial = physicalType.spatial();
        if (spatial == null) {
            return Optional.of(TypeMappingResult.unsupported(
                    "MySQL Geometry 缺少 subtype、SRS ID 和 CRS 元数据"
            ));
        }
        GeometryKind kind = SpatialTypeSupport.geometryKind(spatial.nativeGeometryKind());
        if (!spatial.subtypeConstrained() || kind == null) {
            return Optional.of(TypeMappingResult.unsupported(
                    "不支持 MySQL Geometry 类型：" + spatial.nativeGeometryKind()
            ));
        }
        if (!spatial.crsConstrained() || spatial.spatialReferenceId() == null
                || spatial.spatialReferenceId() < 1) {
            return Optional.of(TypeMappingResult.unsupported("MySQL Geometry 列没有 SRID restriction"));
        }
        if (!"EPSG".equals(spatial.crsAuthority()) || spatial.crsCode() == null
                || spatial.crsCode() < 1) {
            return Optional.of(TypeMappingResult.unsupported(
                    "MySQL Geometry SRS ID 无法解析为 EPSG CRS"
            ));
        }
        if (spatial.coordinateDimension() != CoordinateDimension.XY) {
            return Optional.of(TypeMappingResult.unsupported("空间字段第一版只支持 XY 二维坐标"));
        }
        return Optional.of(TypeMappingResult.exact(PlatformTypeDefinition.geometry(
                new GeometryTypeDefinition(kind, CrsReference.epsg(spatial.crsCode()), CoordinateDimension.XY)
        )));
    }

    @Override
    protected TypeMappingResult<PhysicalTypeDefinition> mapPlatformTypeToPhysical(
            PlatformTypeDefinition platformType
    ) {
        if (platformType.type() != PlatformDataType.GEOMETRY) {
            return super.mapPlatformTypeToPhysical(platformType);
        }
        String issue = SpatialTypeSupport.validateV1Geometry(platformType.geometry());
        return issue == null
                ? TypeMappingResult.exact(new PhysicalTypeDefinition(
                TableColumnType.GEOMETRY, null, null, null, platformType.geometry()
        ))
                : TypeMappingResult.unsupported(issue);
    }

    @Override
    public List<ColumnMetadata> enrichColumnMetadata(
            Connection connection,
            TableIdentifier table,
            List<ColumnMetadata> columns
    ) throws SQLException {
        if (columns.stream().noneMatch(MySqlDialect::isMySqlSpatialColumn)) {
            return List.copyOf(columns);
        }
        if (!isMySql8(connection)) {
            return columns.stream().map(MySqlDialect::unresolvedSpatialColumn).toList();
        }
        Map<String, SpatialColumnMetadata> spatialByColumn = new HashMap<>();
        String sql = """
                SELECT geometry_column.COLUMN_NAME,
                       geometry_column.GEOMETRY_TYPE_NAME,
                       geometry_column.SRS_ID,
                       reference_system.ORGANIZATION,
                       reference_system.ORGANIZATION_COORDSYS_ID
                  FROM INFORMATION_SCHEMA.ST_GEOMETRY_COLUMNS geometry_column
                  LEFT JOIN INFORMATION_SCHEMA.ST_SPATIAL_REFERENCE_SYSTEMS reference_system
                    ON reference_system.SRS_ID = geometry_column.SRS_ID
                 WHERE geometry_column.TABLE_SCHEMA = ?
                   AND geometry_column.TABLE_NAME = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table.catalog());
            statement.setString(2, table.table());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Integer srsId = nullableInteger(resultSet, "SRS_ID");
                    spatialByColumn.put(normalizeColumnName(resultSet.getString("COLUMN_NAME")),
                            new SpatialColumnMetadata(
                                    resultSet.getString("GEOMETRY_TYPE_NAME"),
                                    srsId,
                                    resultSet.getString("ORGANIZATION"),
                                    nullableInteger(resultSet, "ORGANIZATION_COORDSYS_ID"),
                                    CoordinateDimension.XY,
                                    true,
                                    srsId != null && srsId > 0
                            ));
                }
            }
        }
        return columns.stream().map(column -> {
            if (!isMySqlSpatialColumn(column)) {
                return column;
            }
            SpatialColumnMetadata spatial = spatialByColumn.get(normalizeColumnName(column.name()));
            return spatial == null ? unresolvedSpatialColumn(column) : column.withSpatial(spatial);
        }).toList();
    }

    @Override
    public DdlPlan planCreateTable(TableDefinition definition) {
        if (SpatialTypeSupport.containsGeometry(definition)) {
            throw new IllegalArgumentException("MySQL Geometry 建表规划需要连接目标数据库确认版本");
        }
        return super.planCreateTable(definition);
    }

    @Override
    public DdlPlan planCreateTable(Connection connection, TableDefinition definition) throws SQLException {
        if (!SpatialTypeSupport.containsGeometry(definition)) {
            return super.planCreateTable(definition);
        }
        if (!isMySql8(connection)) {
            throw new IllegalArgumentException("空间字段第一版只支持 MySQL 8.x，不支持 MySQL 5.7 或 MariaDB");
        }
        List<String> clauses = new ArrayList<>();
        for (TableColumnDefinition column : definition.columns()) {
            String typeSql = columnTypeSql(column);
            clauses.add(quoteIdentifier(column.name()) + " " + typeSql
                    + (column.nullable() ? "" : " NOT NULL"));
        }
        appendPrimaryKey(definition, clauses);
        return new DdlPlan(
                definition.table(),
                List.of("CREATE TABLE " + qualifiedName(definition.table()) + " ("
                        + String.join(", ", clauses) + ") ENGINE=InnoDB")
        );
    }

    private static boolean isMySql8(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName();
        return "MySQL".equalsIgnoreCase(productName)
                && connection.getMetaData().getDatabaseMajorVersion() == 8;
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

    private static boolean isMySqlSpatialColumn(ColumnMetadata column) {
        return isMySqlSpatialType(column.nativeType());
    }

    private static boolean isMySqlSpatialType(String nativeType) {
        return SpatialTypeSupport.geometryKind(nativeType) != null;
    }

    private static ColumnMetadata unresolvedSpatialColumn(ColumnMetadata column) {
        if (!isMySqlSpatialColumn(column)) {
            return column;
        }
        return column.withSpatial(new SpatialColumnMetadata(
                column.nativeType(), null, null, null, CoordinateDimension.XY, true, false
        ));
    }

    private static String normalizeColumnName(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }
}
