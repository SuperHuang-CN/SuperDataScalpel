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
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;
import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.DdlPlan;
import cn.superhuang.data.scalpel.dialect.model.JdbcTypeDescriptor;
import cn.superhuang.data.scalpel.dialect.model.PhysicalTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.SpatialColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableColumnDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableColumnType;
import cn.superhuang.data.scalpel.dialect.model.TableDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

public final class MySqlDialect extends AbstractJdbcDialect {

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
            case GEOMETRY -> column.geometry().kind().name() + " SRID " + column.geometry().crs().code();
        };
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
            throw new IllegalArgumentException("MySQL Geometry 建表规划需要连接目标数据库解析 CRS");
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
        Map<CrsReference, Integer> localSrsIds = resolveMySqlSrsIds(connection, definition);
        List<String> clauses = new ArrayList<>();
        for (TableColumnDefinition column : definition.columns()) {
            String typeSql = column.type() == TableColumnType.GEOMETRY
                    ? mySqlGeometryType(column.geometry(), localSrsIds)
                    : columnTypeSql(column);
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

    private Map<CrsReference, Integer> resolveMySqlSrsIds(
            Connection connection,
            TableDefinition definition
    ) throws SQLException {
        Set<Integer> codes = definition.columns().stream()
                .filter(column -> column.type() == TableColumnType.GEOMETRY)
                .map(column -> column.geometry().crs().code())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        String placeholders = codes.stream().map(ignored -> "?")
                .collect(java.util.stream.Collectors.joining(", "));
        String sql = """
                SELECT SRS_ID, ORGANIZATION, ORGANIZATION_COORDSYS_ID
                  FROM INFORMATION_SCHEMA.ST_SPATIAL_REFERENCE_SYSTEMS
                 WHERE UPPER(ORGANIZATION) = 'EPSG'
                   AND ORGANIZATION_COORDSYS_ID IN (%s)
                """.formatted(placeholders);
        Map<CrsReference, Integer> result = new LinkedHashMap<>();
        Set<CrsReference> duplicates = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            for (Integer code : codes) {
                statement.setInt(parameter++, code);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    CrsReference crs = new CrsReference(
                            resultSet.getString("ORGANIZATION"),
                            resultSet.getInt("ORGANIZATION_COORDSYS_ID")
                    );
                    Integer previous = result.putIfAbsent(crs, resultSet.getInt("SRS_ID"));
                    if (previous != null) {
                        duplicates.add(crs);
                    }
                }
            }
        }
        for (Integer code : codes) {
            CrsReference crs = CrsReference.epsg(code);
            if (duplicates.contains(crs)) {
                throw new IllegalArgumentException("目标 MySQL 中 EPSG:" + code + " 对应多个 SRS ID");
            }
            if (!result.containsKey(crs)) {
                throw new IllegalArgumentException("目标 MySQL 中不存在 EPSG:" + code);
            }
        }
        return Map.copyOf(result);
    }

    private static String mySqlGeometryType(
            GeometryTypeDefinition geometry,
            Map<CrsReference, Integer> localSrsIds
    ) {
        String issue = SpatialTypeSupport.validateV1Geometry(geometry);
        if (issue != null) {
            throw new IllegalArgumentException(issue);
        }
        Integer localSrsId = localSrsIds.get(geometry.crs());
        if (localSrsId == null) {
            throw new IllegalArgumentException("目标 MySQL 中不存在 "
                    + geometry.crs().authority() + ":" + geometry.crs().code());
        }
        return geometry.kind().name() + " SRID " + localSrsId;
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
