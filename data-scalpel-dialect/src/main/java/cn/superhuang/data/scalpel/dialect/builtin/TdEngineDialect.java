package cn.superhuang.data.scalpel.dialect.builtin;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionChoice;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionDefinition;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionType;
import cn.superhuang.data.scalpel.dialect.api.DatabaseCapability;
import cn.superhuang.data.scalpel.dialect.api.DatabaseMetadataProvider;
import cn.superhuang.data.scalpel.dialect.api.NamespaceMode;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;
import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.ColumnRole;
import cn.superhuang.data.scalpel.dialect.model.IndexMetadata;
import cn.superhuang.data.scalpel.dialect.model.JdbcTypeDescriptor;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import cn.superhuang.data.scalpel.dialect.model.NamespaceInfo;
import cn.superhuang.data.scalpel.dialect.model.PhysicalTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.PrimaryKeyMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableList;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.model.TablePhysicalStatistics;
import cn.superhuang.data.scalpel.dialect.model.TableQuery;
import cn.superhuang.data.scalpel.dialect.model.TableStorageMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableSummary;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingResult;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/** Shared, source-only TDengine dialect. The transport changes only the URL, driver and connection options. */
public final class TdEngineDialect extends AbstractJdbcDialect implements DatabaseMetadataProvider {

    private static final Set<String> SYSTEM_DATABASES = Set.of("information_schema", "performance_schema");
    private static final Set<DatabaseCapability> BASE_CAPABILITIES = EnumSet.of(
            DatabaseCapability.TEST_CONNECTION,
            DatabaseCapability.LIST_NAMESPACES,
            DatabaseCapability.LIST_TABLES,
            DatabaseCapability.READ_TABLE_METADATA,
            DatabaseCapability.PREVIEW_DATA,
            DatabaseCapability.STANDARD_QUERY
    );

    private final TdEngineJdbcTransport transport;

    TdEngineDialect(TdEngineJdbcTransport transport) {
        super(
                transport.id(), transport.displayName(), 6041,
                "数据库", "Schema（不适用）", null, NamespaceMode.CATALOG,
                capabilities(transport), connectionOptions(transport), transport.driverClassName(),
                "`", "`", QualificationMode.CATALOG, PreviewStyle.LIMIT
        );
        this.transport = transport;
    }

    private static Set<DatabaseCapability> capabilities(TdEngineJdbcTransport transport) {
        EnumSet<DatabaseCapability> capabilities = EnumSet.copyOf(BASE_CAPABILITIES);
        if (transport == TdEngineJdbcTransport.WEBSOCKET) {
            capabilities.add(DatabaseCapability.TMQ_SUBSCRIBE);
        }
        return capabilities;
    }

    @Override
    public JdbcConnectionSpec createConnectionSpec(JdbcConnectionConfig config) {
        rejectRestfulBatchLoad(config);
        Properties properties = baseProperties(config);
        applyConnectionOptions(config, properties, Set.of(), Set.of());
        String url = transport.jdbcPrefix() + hostForUrl(config) + ":" + config.port()
                + "/" + pathSegment(config.databaseName());
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
    public List<NamespaceInfo> listNamespaces(Connection connection, JdbcConnectionConfig config) throws SQLException {
        List<NamespaceInfo> namespaces = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT name FROM information_schema.ins_databases ORDER BY name")) {
            while (resultSet.next()) {
                String database = resultSet.getString(1);
                if (database == null || SYSTEM_DATABASES.contains(database.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                namespaces.add(new NamespaceInfo(
                        database,
                        null,
                        database,
                        database.equalsIgnoreCase(config.databaseName())
                ));
            }
        }
        if (namespaces.stream().noneMatch(namespace -> namespace.catalog().equalsIgnoreCase(config.databaseName()))) {
            namespaces.add(new NamespaceInfo(config.databaseName(), null, config.databaseName(), true));
        }
        namespaces.sort(Comparator.comparing(NamespaceInfo::displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(namespaces);
    }

    @Override
    public TableList listTables(Connection connection, JdbcConnectionConfig config, TableQuery query) throws SQLException {
        String database = resolveCatalog(config, query.catalog());
        String keyword = query.keyword() == null ? null : query.keyword().toLowerCase(Locale.ROOT);
        List<TableSummary> supertables = new ArrayList<>();
        boolean truncated = false;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT stable_name FROM information_schema.ins_stables WHERE db_name = ? ORDER BY stable_name")) {
            statement.setString(1, database);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String stableName = resultSet.getString(1);
                    if (keyword != null && !stableName.toLowerCase(Locale.ROOT).contains(keyword)) {
                        continue;
                    }
                    if (supertables.size() == query.limit()) {
                        truncated = true;
                        break;
                    }
                    supertables.add(new TableSummary(
                            new TableIdentifier(database, null, stableName),
                            "SUPERTABLE",
                            null
                    ));
                }
            }
        }
        return new TableList(supertables, truncated);
    }

    @Override
    public TableMetadata readTableMetadata(Connection connection, TableIdentifier table) throws SQLException {
        String database = requiredIdentifier(table.catalog(), "TDengine 数据库");
        String stableName = requiredIdentifier(table.table(), "TDengine 超级表");
        requireSupertable(connection, database, stableName);
        String timestampPrecision = readTimestampPrecision(connection, database);
        List<ColumnMetadata> columns = new ArrayList<>();
        boolean timeKeyAssigned = false;
        String describeSql = "DESCRIBE " + quoteIdentifier(database) + "." + quoteIdentifier(stableName);
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(describeSql)) {
            int ordinal = 0;
            while (resultSet.next()) {
                ordinal++;
                String name = resultSet.getString(1);
                String nativeType = normalizeNativeType(resultSet.getString(2), timestampPrecision);
                Integer length = nullableInteger(resultSet, 3);
                String note = optionalResult(resultSet, 4);
                boolean tag = "TAG".equalsIgnoreCase(note);
                boolean timeKey = !tag && !timeKeyAssigned && baseNativeType(nativeType).equals("TIMESTAMP");
                if (timeKey) {
                    timeKeyAssigned = true;
                }
                int jdbcType = jdbcType(nativeType);
                columns.add(new ColumnMetadata(
                        name,
                        ordinal,
                        jdbcType,
                        nativeType,
                        logicalType(jdbcType, nativeType),
                        stringLength(nativeType, length),
                        numericPrecision(nativeType),
                        numericScale(nativeType),
                        !timeKey,
                        null,
                        false,
                        false,
                        null,
                        null,
                        tag ? ColumnRole.TAG : timeKey ? ColumnRole.TIME_KEY : ColumnRole.REGULAR
                ));
            }
        }
        if (columns.isEmpty()) {
            throw new DatabaseAccessException("TABLE_NOT_FOUND", "未找到指定的 TDengine 超级表", null);
        }
        return new TableMetadata(
                new TableSummary(new TableIdentifier(database, null, stableName), "SUPERTABLE", null),
                columns,
                new PrimaryKeyMetadata(null, List.of()),
                List.<IndexMetadata>of(),
                TableStorageMetadata.none()
        );
    }

    @Override
    protected Optional<TypeMappingResult<PlatformTypeDefinition>> mapDialectTypeToPlatform(
            JdbcTypeDescriptor physicalType
    ) {
        String nativeType = physicalType.nativeTypeName().trim().toUpperCase(Locale.ROOT);
        String baseType = baseNativeType(nativeType);
        TypeMappingResult<PlatformTypeDefinition> mapping = switch (baseType) {
            case "BOOL", "BOOLEAN" -> scalar(PlatformDataType.BOOLEAN);
            case "TINYINT" -> scalar(nativeType.contains("UNSIGNED") ? PlatformDataType.SHORT : PlatformDataType.BYTE);
            case "SMALLINT" -> scalar(nativeType.contains("UNSIGNED") ? PlatformDataType.INTEGER : PlatformDataType.SHORT);
            case "INT", "INTEGER" -> scalar(nativeType.contains("UNSIGNED") ? PlatformDataType.LONG : PlatformDataType.INTEGER);
            case "BIGINT" -> nativeType.contains("UNSIGNED")
                    ? TypeMappingResult.exact(PlatformTypeDefinition.decimal(20, 0))
                    : scalar(PlatformDataType.LONG);
            case "FLOAT" -> scalar(PlatformDataType.FLOAT);
            case "DOUBLE" -> scalar(PlatformDataType.DOUBLE);
            case "TIMESTAMP" -> nativeType.contains("(NS)")
                    ? TypeMappingResult.unsupported("TDengine 纳秒时间戳无法由当前平台无损保存")
                    : scalar(PlatformDataType.TIMESTAMP);
            case "BINARY", "NCHAR", "VARCHAR" -> TypeMappingResult.exact(
                    PlatformTypeDefinition.string(physicalType.length())
            );
            case "VARBINARY" -> scalar(PlatformDataType.BINARY);
            case "JSON" -> TypeMappingResult.unsupported("TDengine JSON TAG 第一版不导入平台模型或任务");
            case "GEOMETRY" -> TypeMappingResult.unsupported("TDengine GEOMETRY 第一版不导入平台模型或任务");
            default -> TypeMappingResult.unsupported("不支持的 TDengine 字段类型：" + physicalType.nativeTypeName());
        };
        return Optional.of(mapping);
    }

    @Override
    protected TypeMappingResult<PhysicalTypeDefinition> mapPlatformTypeToPhysical(
            PlatformTypeDefinition platformType
    ) {
        return TypeMappingResult.unsupported("TDengine 第一版仅支持读取超级表，不支持创建或写入物理表");
    }

    @Override
    public TablePhysicalStatistics readTablePhysicalStatistics(
            Connection connection,
            TableIdentifier table,
            Duration timeout
    ) {
        return TablePhysicalStatistics.unsupported("TDengine 第一版不提供超级表物理统计");
    }

    private void requireSupertable(Connection connection, String database, String stableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT stable_name FROM information_schema.ins_stables WHERE db_name = ? AND stable_name = ?")) {
            statement.setString(1, database);
            statement.setString(2, stableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new DatabaseAccessException(
                            "TABLE_NOT_FOUND",
                            "对象不存在或不是 TDengine 超级表；子表不在 DataScalpel 支持范围内",
                            null
                    );
                }
            }
        }
    }

    private static String readTimestampPrecision(Connection connection, String database) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT precision FROM information_schema.ins_databases WHERE name = ?")) {
            statement.setString(1, database);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String precision = resultSet.getString(1);
                    String normalized = precision == null ? "" : precision.trim().toUpperCase(Locale.ROOT);
                    if (Set.of("MS", "US", "NS").contains(normalized)) {
                        return normalized;
                    }
                }
            }
        }
        throw new DatabaseAccessException(
                "DATABASE_METADATA_UNAVAILABLE",
                "无法确认 TDengine 数据库时间精度；为避免纳秒数据被静默截断，已停止读取结构",
                null
        );
    }

    private void rejectRestfulBatchLoad(JdbcConnectionConfig config) {
        if (transport != TdEngineJdbcTransport.RESTFUL) {
            return;
        }
        config.options().forEach((key, value) -> {
            if (("batchLoad".equalsIgnoreCase(key) || "batchfetch".equalsIgnoreCase(key))
                    && Boolean.parseBoolean(value)) {
                throw new IllegalArgumentException(
                        "TDengine RESTful 数据源不允许 batchfetch/batchLoad=true；该参数会隐式切换到 WebSocket，请直接选择 WebSocket 类型"
                );
            }
        });
    }

    private static List<ConnectionOptionDefinition> connectionOptions(TdEngineJdbcTransport transport) {
        if (transport != TdEngineJdbcTransport.WEBSOCKET) {
            return List.of();
        }
        return List.of(new ConnectionOptionDefinition(
                "useSSL",
                "使用 SSL",
                ConnectionOptionType.BOOLEAN,
                "false",
                List.of(new ConnectionOptionChoice("true", "是"), new ConnectionOptionChoice("false", "否"))
        ));
    }

    private static TypeMappingResult<PlatformTypeDefinition> scalar(PlatformDataType type) {
        return TypeMappingResult.exact(PlatformTypeDefinition.of(type));
    }

    private static String normalizeNativeType(String rawType, String timestampPrecision) {
        String nativeType = rawType == null ? "" : rawType.trim().toUpperCase(Locale.ROOT);
        if (baseNativeType(nativeType).equals("TIMESTAMP") && timestampPrecision != null) {
            return "TIMESTAMP(" + timestampPrecision + ")";
        }
        return nativeType;
    }

    private static String baseNativeType(String nativeType) {
        String normalized = nativeType == null ? "" : nativeType.trim().toUpperCase(Locale.ROOT);
        int parameter = normalized.indexOf('(');
        if (parameter >= 0) {
            normalized = normalized.substring(0, parameter).trim();
        }
        return normalized.replace(" UNSIGNED", "").trim();
    }

    private static int jdbcType(String nativeType) {
        boolean unsigned = nativeType.contains("UNSIGNED");
        return switch (baseNativeType(nativeType)) {
            case "BOOL", "BOOLEAN" -> Types.BOOLEAN;
            case "TINYINT" -> unsigned ? Types.SMALLINT : Types.TINYINT;
            case "SMALLINT" -> unsigned ? Types.INTEGER : Types.SMALLINT;
            case "INT", "INTEGER" -> unsigned ? Types.BIGINT : Types.INTEGER;
            case "BIGINT" -> unsigned ? Types.DECIMAL : Types.BIGINT;
            case "FLOAT" -> Types.REAL;
            case "DOUBLE" -> Types.DOUBLE;
            case "TIMESTAMP" -> Types.TIMESTAMP;
            case "BINARY", "VARCHAR" -> Types.VARCHAR;
            case "NCHAR" -> Types.NVARCHAR;
            case "VARBINARY" -> Types.VARBINARY;
            default -> Types.OTHER;
        };
    }

    private static Integer stringLength(String nativeType, Integer length) {
        return switch (baseNativeType(nativeType)) {
            case "BINARY", "NCHAR", "VARCHAR", "VARBINARY" -> length;
            default -> null;
        };
    }

    private static Integer numericPrecision(String nativeType) {
        return baseNativeType(nativeType).equals("BIGINT") && nativeType.contains("UNSIGNED") ? 20 : null;
    }

    private static Integer numericScale(String nativeType) {
        return baseNativeType(nativeType).equals("BIGINT") && nativeType.contains("UNSIGNED") ? 0 : null;
    }

    private static Integer nullableInteger(ResultSet resultSet, int index) throws SQLException {
        int value = resultSet.getInt(index);
        return resultSet.wasNull() ? null : value;
    }

    private static String optionalResult(ResultSet resultSet, int index) {
        try {
            String value = resultSet.getString(index);
            return value == null || value.isBlank() ? null : value.trim();
        } catch (SQLException ignored) {
            return null;
        }
    }

    private static String requiredIdentifier(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return value.trim();
    }
}
