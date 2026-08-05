package cn.superhuang.data.scalpel.dialect.builtin;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionDefinition;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionType;
import cn.superhuang.data.scalpel.dialect.api.DatabaseCapability;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDefinition;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.NamespaceMode;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;
import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.DdlPlan;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import cn.superhuang.data.scalpel.dialect.model.JdbcTypeDescriptor;
import cn.superhuang.data.scalpel.dialect.model.PhysicalTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableColumnDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableColumnType;
import cn.superhuang.data.scalpel.dialect.model.TableChangeCheck;
import cn.superhuang.data.scalpel.dialect.model.TableChangePlan;
import cn.superhuang.data.scalpel.dialect.model.TableDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableStructureComparison;
import cn.superhuang.data.scalpel.dialect.model.TableStructureDifference;
import cn.superhuang.data.scalpel.dialect.model.TableStructureDifferenceType;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingResult;
import cn.superhuang.data.scalpel.dialect.query.AggregateFunction;
import cn.superhuang.data.scalpel.dialect.query.CompiledStandardQuery;
import cn.superhuang.data.scalpel.dialect.query.CompiledSqlServiceQuery;
import cn.superhuang.data.scalpel.dialect.query.ConditionConjunction;
import cn.superhuang.data.scalpel.dialect.query.InsertSelectQuery;
import cn.superhuang.data.scalpel.dialect.query.PreparedQuery;
import cn.superhuang.data.scalpel.dialect.query.PreparedSqlQuery;
import cn.superhuang.data.scalpel.dialect.query.QueryAggregate;
import cn.superhuang.data.scalpel.dialect.query.QueryFilter;
import cn.superhuang.data.scalpel.dialect.query.QueryFilterOperator;
import cn.superhuang.data.scalpel.dialect.query.QueryOrder;
import cn.superhuang.data.scalpel.dialect.query.QueryParameter;
import cn.superhuang.data.scalpel.dialect.query.QuerySortDirection;
import cn.superhuang.data.scalpel.dialect.query.QueryValueType;
import cn.superhuang.data.scalpel.dialect.query.SqlQueryParameter;
import cn.superhuang.data.scalpel.dialect.query.StandardQuery;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

abstract class AbstractJdbcDialect implements DatabaseDialect {

    private static final Pattern CONNECTION_OPTION_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{0,63}");
    private static final Set<String> GLOBAL_RESERVED_CONNECTION_OPTION_KEYS = Set.of(
            "user", "username", "password", "host", "port", "database", "databasename", "schema",
            "driver", "driverclassname", "url", "jdbcurl", "connecttimeout", "sockettimeout",
            "logintimeout", "connection_timeout", "socket_timeout", "applicationname", "currentschema",
            "useunicode", "characterencoding", "oracle.net.connect_timeout", "oracle.jdbc.readtimeout"
    );

    enum QualificationMode {
        CATALOG,
        SCHEMA,
        CATALOG_AND_SCHEMA
    }

    enum PreviewStyle {
        LIMIT,
        TOP,
        FETCH_FIRST,
        ROWNUM
    }

    private final DatabaseDefinition definition;
    private final String driverClassName;
    private final String quoteStart;
    private final String quoteEnd;
    private final QualificationMode qualificationMode;
    private final PreviewStyle previewStyle;

    protected AbstractJdbcDialect(
            String id,
            String displayName,
            int defaultPort,
            String databaseNameLabel,
            String schemaNameLabel,
            String defaultSchema,
            NamespaceMode namespaceMode,
            List<ConnectionOptionDefinition> connectionOptions,
            String driverClassName,
            String quoteStart,
            String quoteEnd,
            QualificationMode qualificationMode,
            PreviewStyle previewStyle
    ) {
        this.definition = new DatabaseDefinition(
                id,
                displayName,
                defaultPort,
                databaseNameLabel,
                schemaNameLabel,
                defaultSchema,
                namespaceMode,
                capabilitiesFor(id),
                connectionOptions
        );
        this.driverClassName = driverClassName;
        this.quoteStart = quoteStart;
        this.quoteEnd = quoteEnd;
        this.qualificationMode = qualificationMode;
        this.previewStyle = previewStyle;
    }

    @Override
    public final DatabaseDefinition definition() {
        return definition;
    }

    @Override
    public final String driverClassName() {
        return driverClassName;
    }

    @Override
    public String resolveCatalog(JdbcConnectionConfig config, String requestedCatalog) {
        return optional(requestedCatalog);
    }

    @Override
    public String resolveSchema(JdbcConnectionConfig config, String requestedSchema) {
        String requested = optional(requestedSchema);
        if (requested != null) {
            return requested;
        }
        if (config.schemaName() != null) {
            return config.schemaName();
        }
        return definition.defaultSchema();
    }

    @Override
    public String validationQuery() {
        return "SELECT 1";
    }

    @Override
    public final String previewSql(TableIdentifier table, int rowLimit) {
        if (rowLimit < 1 || rowLimit > 101) {
            throw new IllegalArgumentException("Preview limit must be between 1 and 101");
        }
        String qualifiedTable = qualifiedName(table);
        return switch (previewStyle) {
            case LIMIT -> "SELECT * FROM " + qualifiedTable + " LIMIT " + rowLimit;
            case TOP -> "SELECT TOP (" + rowLimit + ") * FROM " + qualifiedTable;
            case FETCH_FIRST -> "SELECT * FROM " + qualifiedTable + " FETCH FIRST " + rowLimit + " ROWS ONLY";
            case ROWNUM -> "SELECT * FROM " + qualifiedTable + " WHERE ROWNUM <= " + rowLimit;
        };
    }

    @Override
    public final CompiledStandardQuery compileStandardQuery(StandardQuery query) {
        if (!definition().capabilities().contains(DatabaseCapability.STANDARD_QUERY)) {
            throw new UnsupportedOperationException(definition().displayName() + " does not support standard queries");
        }
        List<QueryParameter> whereParameters = new ArrayList<>();
        String baseSql = renderBaseQuery(query, whereParameters);
        List<QueryParameter> countParameters = List.copyOf(whereParameters);
        String orderedSql = baseSql + renderOrder(query);
        PreparedQuery dataQuery = applyPagination(orderedSql, whereParameters, query.offset(), query.limit());
        PreparedQuery countQuery = query.returnCount()
                ? new PreparedQuery("SELECT COUNT(*) FROM (" + baseSql + ") ds_count", countParameters)
                : null;
        return new CompiledStandardQuery(dataQuery, countQuery);
    }

    @Override
    public final CompiledSqlServiceQuery compileSqlServiceQuery(
            String jdbcSql,
            List<SqlQueryParameter> parameters,
            int offset,
            int limit,
            boolean returnCount
    ) {
        if (!definition().capabilities().contains(DatabaseCapability.SQL_SERVICE_QUERY)) {
            throw new UnsupportedOperationException(definition().displayName() + " does not support SQL query services");
        }
        if (jdbcSql == null || jdbcSql.isBlank() || offset < 0 || limit < 1) {
            throw new IllegalArgumentException("Invalid SQL service query pagination");
        }
        List<SqlQueryParameter> dataParameters = new ArrayList<>(parameters);
        dataParameters.add(new SqlQueryParameter(limit, PlatformTypeDefinition.of(PlatformDataType.INTEGER)));
        dataParameters.add(new SqlQueryParameter(offset, PlatformTypeDefinition.of(PlatformDataType.INTEGER)));
        PreparedSqlQuery dataQuery = new PreparedSqlQuery(
                "SELECT * FROM (" + jdbcSql + ") ds_query LIMIT ? OFFSET ?", dataParameters
        );
        PreparedSqlQuery countQuery = returnCount
                ? new PreparedSqlQuery("SELECT COUNT(*) FROM (" + jdbcSql + ") ds_count", parameters)
                : null;
        return new CompiledSqlServiceQuery(dataQuery, countQuery);
    }

    @Override
    public String renderInsertSelect(TableIdentifier target, List<String> targetColumns, InsertSelectQuery query) {
        if (!definition().capabilities().contains(DatabaseCapability.INSERT_SELECT)) {
            throw new UnsupportedOperationException(definition().displayName() + " does not support insert-select tasks");
        }
        if (target == null) {
            throw new IllegalArgumentException("Target table is required");
        }
        if (targetColumns == null || targetColumns.isEmpty()) {
            throw new IllegalArgumentException("At least one target column is required");
        }
        if (query == null) {
            throw new IllegalArgumentException("Read-only select query is required");
        }
        String columns = targetColumns.stream()
                .map(column -> {
                    if (column == null || column.isBlank()) {
                        throw new IllegalArgumentException("Target column must not be blank");
                    }
                    return quoteIdentifier(column.trim());
                })
                .collect(java.util.stream.Collectors.joining(", "));
        String insert = "INSERT INTO " + qualifiedName(target) + " (" + columns + ") ";
        return query.withClause() == null
                ? insert + query.selectSql()
                : insert + query.withClause() + " " + query.selectSql();
    }

    @Override
    public String renderOverwriteCleanup(TableIdentifier target) {
        if (!definition().capabilities().contains(DatabaseCapability.OVERWRITE_INSERT_SELECT)) {
            throw new UnsupportedOperationException(definition().displayName() + " does not support transactional task overwrite");
        }
        if (target == null) {
            throw new IllegalArgumentException("Target table is required");
        }
        return "TRUNCATE TABLE " + qualifiedName(target);
    }

    @Override
    public LogicalType logicalType(int jdbcType, String nativeTypeName) {
        String normalizedTypeName = nativeTypeName == null ? "" : nativeTypeName.toLowerCase(Locale.ROOT);
        if (normalizedTypeName.equals("json") || normalizedTypeName.equals("jsonb")) {
            return LogicalType.JSON;
        }
        return switch (jdbcType) {
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR,
                    Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR, Types.SQLXML -> LogicalType.STRING;
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> LogicalType.INTEGER;
            case Types.NUMERIC, Types.DECIMAL, Types.FLOAT, Types.REAL, Types.DOUBLE -> LogicalType.DECIMAL;
            case Types.BOOLEAN, Types.BIT -> LogicalType.BOOLEAN;
            case Types.DATE -> LogicalType.DATE;
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> LogicalType.TIME;
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> LogicalType.DATETIME;
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> LogicalType.BINARY;
            case Types.ARRAY -> LogicalType.ARRAY;
            default -> LogicalType.OTHER;
        };
    }

    @Override
    public final TypeMappingResult<PlatformTypeDefinition> mapToPlatformType(JdbcTypeDescriptor physicalType) {
        if (physicalType == null) {
            throw new IllegalArgumentException("JDBC type descriptor is required");
        }
        return mapDialectTypeToPlatform(physicalType).orElseGet(() -> standardJdbcTypeToPlatform(physicalType));
    }

    @Override
    public final TypeMappingResult<PhysicalTypeDefinition> mapToPhysicalType(PlatformTypeDefinition platformType) {
        if (platformType == null) {
            throw new IllegalArgumentException("Platform type definition is required");
        }
        return mapPlatformTypeToPhysical(platformType);
    }

    /** Dialect-native type names are evaluated before the common JDBC fallback, following Spark JDBC's precedence. */
    protected Optional<TypeMappingResult<PlatformTypeDefinition>> mapDialectTypeToPlatform(
            JdbcTypeDescriptor physicalType
    ) {
        return Optional.empty();
    }

    /** Dialects override only where their controlled physical representation differs from the common families. */
    protected TypeMappingResult<PhysicalTypeDefinition> mapPlatformTypeToPhysical(
            PlatformTypeDefinition platformType
    ) {
        if (platformType.type() == PlatformDataType.GEOMETRY) {
            return TypeMappingResult.unsupported(
                    definition.displayName() + " 暂不支持受管 GEOMETRY 字段"
            );
        }
        PhysicalTypeDefinition physical = switch (platformType.type()) {
            case BOOLEAN -> physical(TableColumnType.BOOLEAN);
            case BYTE -> physical(TableColumnType.BYTE);
            case SHORT -> physical(TableColumnType.SHORT);
            case INTEGER -> physical(TableColumnType.INTEGER);
            case LONG -> physical(TableColumnType.LONG);
            case FLOAT -> physical(TableColumnType.FLOAT);
            case DOUBLE -> physical(TableColumnType.DOUBLE);
            case DECIMAL -> new PhysicalTypeDefinition(
                    TableColumnType.DECIMAL, null, platformType.precision(), platformType.scale()
            );
            case STRING -> platformType.length() == null
                    ? physical(TableColumnType.TEXT)
                    : new PhysicalTypeDefinition(TableColumnType.STRING, platformType.length(), null, null);
            case BINARY -> physical(TableColumnType.BINARY);
            case DATE -> physical(TableColumnType.DATE);
            case TIMESTAMP -> physical(TableColumnType.TIMESTAMP);
            case TIMESTAMP_NTZ -> physical(TableColumnType.TIMESTAMP_NTZ);
            case GEOMETRY -> throw new IllegalStateException("GEOMETRY mapping must be handled by a spatial dialect");
        };
        return TypeMappingResult.exact(physical);
    }

    @Override
    public DdlPlan planCreateTable(TableDefinition definition) {
        if (!definition().capabilities().contains(DatabaseCapability.CREATE_TABLE)) {
            throw new UnsupportedOperationException(definition().displayName() + " does not support managed table creation");
        }
        if (!definition.storage().isNone()) {
            throw new IllegalArgumentException(definition().displayName() + " does not support the requested table storage definition");
        }
        List<String> clauses = new ArrayList<>();
        for (TableColumnDefinition column : definition.columns()) {
            clauses.add(quoteIdentifier(column.name()) + " " + columnTypeSql(column)
                    + (column.nullable() ? "" : " NOT NULL"));
        }
        if (!definition.primaryKeyColumns().isEmpty()) {
            String primaryKeys = definition.primaryKeyColumns().stream()
                    .map(this::quoteIdentifier)
                    .collect(java.util.stream.Collectors.joining(", "));
            clauses.add("PRIMARY KEY (" + primaryKeys + ")");
        }
        return new DdlPlan(
                definition.table(),
                List.of("CREATE TABLE " + qualifiedName(definition.table()) + " (" + String.join(", ", clauses) + ")")
        );
    }

    @Override
    public final TableStructureComparison compareTable(TableDefinition expected, TableMetadata actual) {
        Map<String, ColumnMetadata> actualColumns = new HashMap<>();
        for (ColumnMetadata column : actual.columns()) {
            actualColumns.put(normalizeIdentifier(column.name()), column);
        }

        List<TableStructureDifference> differences = new ArrayList<>();
        Set<String> expectedNames = new java.util.HashSet<>();
        for (TableColumnDefinition expectedColumn : expected.columns()) {
            String normalizedName = normalizeIdentifier(expectedColumn.name());
            expectedNames.add(normalizedName);
            ColumnMetadata actualColumn = actualColumns.get(normalizedName);
            if (actualColumn == null) {
                differences.add(new TableStructureDifference(
                        expectedColumn.name(), TableStructureDifferenceType.MISSING_COLUMN,
                        describeColumn(expectedColumn), "—"
                ));
                continue;
            }
            if (!matchesColumnType(expectedColumn, actualColumn)) {
                differences.add(new TableStructureDifference(
                        expectedColumn.name(), TableStructureDifferenceType.TYPE_MISMATCH,
                        describeColumn(expectedColumn), describeActualColumn(actualColumn)
                ));
                continue;
            }
            if (requiresStringLengthComparison(expectedColumn)
                    && !java.util.Objects.equals(expectedColumn.length(), actualColumn.length())) {
                differences.add(new TableStructureDifference(
                        expectedColumn.name(), TableStructureDifferenceType.LENGTH_MISMATCH,
                        String.valueOf(expectedColumn.length()), String.valueOf(actualColumn.length())
                ));
            }
            if (expectedColumn.type() == TableColumnType.DECIMAL
                    && (!java.util.Objects.equals(expectedColumn.precision(), actualColumn.precision())
                    || !java.util.Objects.equals(expectedColumn.scale(), actualColumn.scale()))) {
                differences.add(new TableStructureDifference(
                        expectedColumn.name(), TableStructureDifferenceType.PRECISION_MISMATCH,
                        expectedColumn.precision() + "," + expectedColumn.scale(),
                        actualColumn.precision() + "," + actualColumn.scale()
                ));
            }
            if (expectedColumn.nullable() != actualColumn.nullable()) {
                differences.add(new TableStructureDifference(
                        expectedColumn.name(), TableStructureDifferenceType.NULLABILITY_MISMATCH,
                        expectedColumn.nullable() ? "可为空" : "非空",
                        actualColumn.nullable() ? "可为空" : "非空"
                ));
            }
        }
        for (ColumnMetadata actualColumn : actual.columns()) {
            if (!expectedNames.contains(normalizeIdentifier(actualColumn.name()))) {
                differences.add(new TableStructureDifference(
                        actualColumn.name(), TableStructureDifferenceType.EXTRA_COLUMN,
                        "—", describeActualColumn(actualColumn)
                ));
            }
        }

        if (shouldComparePrimaryKey(expected, actual)) {
            List<String> expectedPrimaryKeys = expected.primaryKeyColumns().stream()
                    .map(AbstractJdbcDialect::normalizeIdentifier)
                    .toList();
            List<String> actualPrimaryKeys = actual.primaryKey().columns().stream()
                    .map(AbstractJdbcDialect::normalizeIdentifier)
                    .toList();
            if (!expectedPrimaryKeys.equals(actualPrimaryKeys)) {
                differences.add(new TableStructureDifference(
                        null,
                        TableStructureDifferenceType.PRIMARY_KEY_MISMATCH,
                        expected.primaryKeyColumns().isEmpty() ? "无主键" : String.join(", ", expected.primaryKeyColumns()),
                        actual.primaryKey().columns().isEmpty() ? "无主键" : String.join(", ", actual.primaryKey().columns())
                ));
            }
        }
        differences.addAll(storageDifferences(expected, actual));
        return new TableStructureComparison(differences);
    }

    @Override
    public TableDefinition snapshotTableDefinition(TableMetadata actual) {
        List<TableColumnDefinition> columns = actual.columns().stream()
                .map(this::snapshotColumn)
                .toList();
        return new TableDefinition(actual.table().identifier(), columns, actual.primaryKey().columns());
    }

    @Override
    public TableChangePlan planTableChange(TableDefinition before, TableDefinition target, TableMetadata actual) {
        throw new UnsupportedOperationException(definition.displayName() + " does not support managed table change planning");
    }

    @Override
    public boolean checkTableChange(Connection connection, TableIdentifier table, TableChangeCheck check) throws SQLException {
        throw new UnsupportedOperationException(definition.displayName() + " does not support managed table change checks");
    }

    protected final Properties baseProperties(JdbcConnectionConfig config) {
        Properties properties = new Properties();
        properties.setProperty("user", config.username());
        if (config.password() != null) {
            properties.setProperty("password", config.password());
        }
        return properties;
    }

    protected final void applyConnectionOptions(
            JdbcConnectionConfig config,
            Properties properties,
            Set<String> dialectOnlyKeys,
            Set<String> protectedKeys
    ) {
        if (config.options().size() > 20) {
            throw new IllegalArgumentException("JDBC 连接参数不能超过 20 个");
        }
        Map<String, ConnectionOptionDefinition> definitions = definition.connectionOptions().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        option -> normalizeOptionKey(option.key()),
                        option -> option
                ));
        Set<String> normalizedDialectOnlyKeys = normalizeOptionKeys(dialectOnlyKeys);
        Set<String> normalizedProtectedKeys = new HashSet<>(GLOBAL_RESERVED_CONNECTION_OPTION_KEYS);
        normalizedProtectedKeys.addAll(normalizeOptionKeys(protectedKeys));
        Set<String> seenKeys = new HashSet<>();

        for (var entry : config.options().entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (key.isEmpty() && value.isEmpty()) {
                continue;
            }
            if (!CONNECTION_OPTION_KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("JDBC 连接参数名格式无效：" + key);
            }
            if (value.isEmpty()) {
                throw new IllegalArgumentException("JDBC 连接参数值不能为空：" + key);
            }
            if (value.length() > 512) {
                throw new IllegalArgumentException("JDBC 连接参数值不能超过 512 个字符：" + key);
            }
            String normalizedKey = normalizeOptionKey(key);
            if (!seenKeys.add(normalizedKey)) {
                throw new IllegalArgumentException("JDBC 连接参数名不能重复：" + key);
            }
            ConnectionOptionDefinition optionDefinition = definitions.get(normalizedKey);
            if (optionDefinition == null && normalizedProtectedKeys.contains(normalizedKey)) {
                throw new IllegalArgumentException(
                        definition.displayName() + " 连接参数由系统管理，不能自定义：" + key
                );
            }
            if (optionDefinition == null && sensitiveOptionKey(normalizedKey)) {
                throw new IllegalArgumentException("敏感 JDBC 连接参数不能保存在普通 options 中：" + key);
            }

            String validatedValue = optionDefinition == null
                    ? value
                    : validateDefinedOption(optionDefinition, value);
            if (!normalizedDialectOnlyKeys.contains(normalizedKey)) {
                properties.setProperty(optionDefinition == null ? key : optionDefinition.key(), validatedValue);
            }
        }
    }

    private static String validateDefinedOption(ConnectionOptionDefinition definition, String value) {
        if (definition.type() == ConnectionOptionType.BOOLEAN) {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw new IllegalArgumentException(definition.label() + "只允许 true 或 false");
            }
            return value.toLowerCase(Locale.ROOT);
        }
        if (definition.type() == ConnectionOptionType.SELECT) {
            return definition.choices().stream()
                    .map(choice -> choice.value())
                    .filter(choice -> choice.equalsIgnoreCase(value))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(definition.label() + "的值不受支持：" + value));
        }
        return value;
    }

    private static Set<String> normalizeOptionKeys(Set<String> keys) {
        return keys.stream().map(AbstractJdbcDialect::normalizeOptionKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String normalizeOptionKey(String key) {
        return key.toLowerCase(Locale.ROOT);
    }

    private static boolean sensitiveOptionKey(String normalizedKey) {
        return normalizedKey.contains("password")
                || normalizedKey.contains("passwd")
                || normalizedKey.contains("secret")
                || normalizedKey.contains("token")
                || normalizedKey.contains("apikey")
                || normalizedKey.contains("api_key")
                || normalizedKey.equals("accesskey")
                || normalizedKey.equals("access_key")
                || normalizedKey.endsWith(".accesskey");
    }

    protected final String option(JdbcConnectionConfig config, String key, String defaultValue) {
        String value = config.options().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(key))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    protected final String hostForUrl(JdbcConnectionConfig config) {
        String host = config.host();
        if (host.indexOf('/') >= 0 || host.indexOf('?') >= 0 || host.indexOf('#') >= 0 || host.indexOf(';') >= 0) {
            throw new IllegalArgumentException("主机地址包含非法字符");
        }
        return host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
    }

    protected final String pathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Override
    public final String qualifiedName(TableIdentifier table) {
        return switch (qualificationMode) {
            case CATALOG -> join(table.catalog(), table.table());
            case SCHEMA -> join(table.schema(), table.table());
            case CATALOG_AND_SCHEMA -> join(table.catalog(), table.schema(), table.table());
        };
    }

    private String renderBaseQuery(StandardQuery query, List<QueryParameter> parameters) {
        List<String> selections = new ArrayList<>();
        query.projections().forEach(projection -> selections.add(
                quoteIdentifier(projection.column()) + " AS " + quoteIdentifier(projection.alias())
        ));
        query.aggregates().forEach(aggregate -> selections.add(renderAggregate(aggregate)));
        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(", ", selections))
                .append(" FROM ").append(qualifiedName(query.table()));
        String where = renderWhere(query.filters(), query.conjunction(), parameters);
        if (!where.isEmpty()) {
            sql.append(" WHERE ").append(where);
        }
        if (!query.groups().isEmpty()) {
            sql.append(" GROUP BY ").append(query.groups().stream()
                    .map(this::quoteIdentifier)
                    .collect(java.util.stream.Collectors.joining(", ")));
        }
        return sql.toString();
    }

    private String renderAggregate(QueryAggregate aggregate) {
        String column = "*".equals(aggregate.column()) ? "*" : quoteIdentifier(aggregate.column());
        return switch (aggregate.function()) {
            case COUNT -> "COUNT(" + column + ") AS " + quoteIdentifier(aggregate.alias());
            case SUM -> "SUM(" + column + ") AS " + quoteIdentifier(aggregate.alias());
            case MIN -> "MIN(" + column + ") AS " + quoteIdentifier(aggregate.alias());
            case MAX -> "MAX(" + column + ") AS " + quoteIdentifier(aggregate.alias());
            case AVG -> "AVG(" + column + ") AS " + quoteIdentifier(aggregate.alias());
        };
    }

    private String renderWhere(
            List<QueryFilter> filters,
            ConditionConjunction conjunction,
            List<QueryParameter> parameters
    ) {
        if (filters.isEmpty()) {
            return "";
        }
        String joiner = conjunction == ConditionConjunction.OR ? " OR " : " AND ";
        return filters.stream().map(filter -> renderFilter(filter, parameters))
                .collect(java.util.stream.Collectors.joining(joiner, "(", ")"));
    }

    private String renderFilter(QueryFilter filter, List<QueryParameter> parameters) {
        String column = quoteIdentifier(filter.column());
        return switch (filter.operator()) {
            case EQ -> comparison(column, "=", filter, parameters);
            case NE -> comparison(column, "<>", filter, parameters);
            case GT -> comparison(column, ">", filter, parameters);
            case GE -> comparison(column, ">=", filter, parameters);
            case LT -> comparison(column, "<", filter, parameters);
            case LE -> comparison(column, "<=", filter, parameters);
            case IN -> membership(column, "IN", filter, parameters);
            case NOT_IN -> membership(column, "NOT IN", filter, parameters);
            case BETWEEN -> range(column, "BETWEEN", filter, parameters);
            case NOT_BETWEEN -> range(column, "NOT BETWEEN", filter, parameters);
            case LIKE -> like(column, "LIKE", filter, parameters);
            case NOT_LIKE -> like(column, "NOT LIKE", filter, parameters);
            case IS_NULL -> requireNoValues(filter, column + " IS NULL");
            case IS_NOT_NULL -> requireNoValues(filter, column + " IS NOT NULL");
            case IS_EMPTY -> requireString(filter, column + " = ''");
            case IS_NOT_EMPTY -> requireString(filter, column + " <> ''");
        };
    }

    private String comparison(String column, String operator, QueryFilter filter, List<QueryParameter> parameters) {
        Object value = requireValueCount(filter, 1).getFirst();
        parameters.add(new QueryParameter(value, filter.valueType()));
        return column + " " + operator + " ?";
    }

    private String membership(String column, String operator, QueryFilter filter, List<QueryParameter> parameters) {
        if (filter.values().isEmpty()) {
            throw new IllegalArgumentException(filter.operator() + " requires at least one value");
        }
        String placeholders = filter.values().stream().map(value -> {
            parameters.add(new QueryParameter(value, filter.valueType()));
            return "?";
        }).collect(java.util.stream.Collectors.joining(", "));
        return column + " " + operator + " (" + placeholders + ")";
    }

    private String range(String column, String operator, QueryFilter filter, List<QueryParameter> parameters) {
        List<Object> values = requireValueCount(filter, 2);
        parameters.add(new QueryParameter(values.get(0), filter.valueType()));
        parameters.add(new QueryParameter(values.get(1), filter.valueType()));
        return column + " " + operator + " ? AND ?";
    }

    private String like(String column, String operator, QueryFilter filter, List<QueryParameter> parameters) {
        if (filter.valueType() != QueryValueType.STRING) {
            throw new IllegalArgumentException(filter.operator() + " is only supported for string fields");
        }
        Object value = requireValueCount(filter, 1).getFirst();
        parameters.add(new QueryParameter("%" + value + "%", QueryValueType.STRING));
        return column + " " + operator + " ?";
    }

    private static String requireNoValues(QueryFilter filter, String sql) {
        if (!filter.values().isEmpty()) {
            throw new IllegalArgumentException(filter.operator() + " does not accept a value");
        }
        return sql;
    }

    private static String requireString(QueryFilter filter, String sql) {
        if (filter.valueType() != QueryValueType.STRING || !filter.values().isEmpty()) {
            throw new IllegalArgumentException(filter.operator() + " is only supported for string fields and has no value");
        }
        return sql;
    }

    private static List<Object> requireValueCount(QueryFilter filter, int expectedCount) {
        if (filter.values().size() != expectedCount) {
            throw new IllegalArgumentException(filter.operator() + " requires " + expectedCount + " value(s)");
        }
        return filter.values();
    }

    private String renderOrder(StandardQuery query) {
        if (query.orders().isEmpty()) {
            return previewStyle == PreviewStyle.TOP ? " ORDER BY (SELECT 0)" : "";
        }
        return " ORDER BY " + query.orders().stream().map(this::renderOrder)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private String renderOrder(QueryOrder order) {
        return quoteIdentifier(order.target()) + " " + (order.direction() == QuerySortDirection.DESC ? "DESC" : "ASC");
    }

    private PreparedQuery applyPagination(
            String sql,
            List<QueryParameter> parameters,
            int offset,
            int limit
    ) {
        List<QueryParameter> bindings = new ArrayList<>(parameters);
        return switch (previewStyle) {
            case LIMIT -> {
                bindings.add(new QueryParameter(limit, QueryValueType.INTEGER));
                bindings.add(new QueryParameter(offset, QueryValueType.INTEGER));
                yield new PreparedQuery(sql + " LIMIT ? OFFSET ?", bindings);
            }
            case TOP, FETCH_FIRST -> {
                bindings.add(new QueryParameter(offset, QueryValueType.INTEGER));
                bindings.add(new QueryParameter(limit, QueryValueType.INTEGER));
                yield new PreparedQuery(sql + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", bindings);
            }
            case ROWNUM -> {
                bindings.add(new QueryParameter(Math.addExact(offset, limit), QueryValueType.INTEGER));
                bindings.add(new QueryParameter(offset, QueryValueType.INTEGER));
                yield new PreparedQuery(
                        "SELECT * FROM (SELECT ds_inner.*, ROWNUM ds_row_number FROM (" + sql
                                + ") ds_inner WHERE ROWNUM <= ?) WHERE ds_row_number > ?",
                        bindings
                );
            }
        };
    }

    private String join(String... parts) {
        return java.util.Arrays.stream(parts)
                .filter(part -> part != null && !part.isBlank())
                .map(this::quoteIdentifier)
                .collect(java.util.stream.Collectors.joining("."));
    }

    @Override
    public final String quoteIdentifier(String identifier) {
        return quoteStart + identifier.replace(quoteEnd, quoteEnd + quoteEnd) + quoteEnd;
    }

    protected String columnTypeSql(TableColumnDefinition column) {
        throw new UnsupportedOperationException(definition.displayName() + " does not support managed table creation");
    }

    protected boolean matchesColumnType(TableColumnDefinition expected, ColumnMetadata actual) {
        int jdbcType = actual.jdbcType();
        return switch (expected.type()) {
            case BYTE -> jdbcType == Types.TINYINT;
            case SHORT -> jdbcType == Types.SMALLINT;
            case STRING -> jdbcType == Types.CHAR || jdbcType == Types.VARCHAR
                    || jdbcType == Types.NCHAR || jdbcType == Types.NVARCHAR;
            case TEXT -> jdbcType == Types.LONGVARCHAR || jdbcType == Types.LONGNVARCHAR
                    || jdbcType == Types.CLOB || jdbcType == Types.NCLOB
                    || containsAny(actual.nativeType(), "TEXT", "CLOB");
            case INTEGER -> jdbcType == Types.TINYINT || jdbcType == Types.SMALLINT || jdbcType == Types.INTEGER;
            case LONG -> jdbcType == Types.BIGINT;
            case FLOAT -> jdbcType == Types.REAL;
            case DOUBLE -> jdbcType == Types.FLOAT || jdbcType == Types.DOUBLE;
            case DECIMAL -> jdbcType == Types.DECIMAL || jdbcType == Types.NUMERIC;
            case BOOLEAN -> jdbcType == Types.BOOLEAN || jdbcType == Types.BIT;
            case DATE -> jdbcType == Types.DATE;
            case TIMESTAMP -> jdbcType == Types.TIMESTAMP_WITH_TIMEZONE;
            case TIMESTAMP_NTZ, DATETIME -> jdbcType == Types.TIMESTAMP
                    || containsAny(actual.nativeType(), "DATETIME");
            case BINARY -> jdbcType == Types.BINARY || jdbcType == Types.VARBINARY || jdbcType == Types.LONGVARBINARY
                    || jdbcType == Types.BLOB || containsAny(actual.nativeType(), "BLOB", "BINARY");
            case GEOMETRY -> {
                TypeMappingResult<PlatformTypeDefinition> mapping =
                        mapToPlatformType(JdbcTypeDescriptor.from(actual));
                yield mapping.acceptable()
                        && mapping.definition().type() == PlatformDataType.GEOMETRY
                        && java.util.Objects.equals(expected.geometry(), mapping.definition().geometry());
            }
        };
    }

    /** Returns whether a logical string length is a physical constraint for this dialect. */
    protected boolean requiresStringLengthComparison(TableColumnDefinition expected) {
        return expected.type() == TableColumnType.STRING;
    }

    /** Some engines expose an index-like key through JDBC that is not a relational primary key. */
    protected boolean shouldComparePrimaryKey(TableDefinition expected, TableMetadata actual) {
        return true;
    }

    /** Lets a dialect compare explicit engine/order attributes in addition to JDBC columns. */
    protected List<TableStructureDifference> storageDifferences(TableDefinition expected, TableMetadata actual) {
        if (expected.storage().isNone()) {
            return List.of();
        }
        return List.of(new TableStructureDifference(
                null,
                TableStructureDifferenceType.STORAGE_CONFIGURATION_MISMATCH,
                expected.storage().engine().name(),
                actual.storage().engine() == null ? "未知" : actual.storage().engine()
        ));
    }

    private TableColumnDefinition snapshotColumn(ColumnMetadata actual) {
        TableColumnType type = tableColumnType(actual);
        Integer length = type == TableColumnType.STRING ? requireLength(actual) : null;
        Integer precision = type == TableColumnType.DECIMAL ? requirePrecision(actual) : null;
        Integer scale = type == TableColumnType.DECIMAL ? requireScale(actual) : null;
        if (type == TableColumnType.GEOMETRY) {
            TypeMappingResult<PlatformTypeDefinition> mapping =
                    mapToPlatformType(JdbcTypeDescriptor.from(actual));
            if (!mapping.acceptable() || mapping.definition().type() != PlatformDataType.GEOMETRY) {
                throw new UnsupportedOperationException(
                        mapping.message() == null ? "Unsupported geometry column: " + actual.name() : mapping.message()
                );
            }
            return new TableColumnDefinition(
                    actual.name(), type, null, null, null, actual.nullable(), null,
                    mapping.definition().geometry()
            );
        }
        return new TableColumnDefinition(actual.name(), type, length, precision, scale, actual.nullable());
    }

    private static TableColumnType tableColumnType(ColumnMetadata actual) {
        if (actual.spatial() != null) {
            return TableColumnType.GEOMETRY;
        }
        if (containsAny(actual.nativeType(), "TEXT", "CLOB")) {
            return TableColumnType.TEXT;
        }
        return switch (actual.jdbcType()) {
            case Types.CHAR, Types.VARCHAR, Types.NCHAR, Types.NVARCHAR -> TableColumnType.STRING;
            case Types.LONGVARCHAR, Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB -> TableColumnType.TEXT;
            case Types.TINYINT -> TableColumnType.BYTE;
            case Types.SMALLINT -> TableColumnType.SHORT;
            case Types.INTEGER -> TableColumnType.INTEGER;
            case Types.BIGINT -> TableColumnType.LONG;
            case Types.REAL -> TableColumnType.FLOAT;
            case Types.FLOAT, Types.DOUBLE -> TableColumnType.DOUBLE;
            case Types.NUMERIC, Types.DECIMAL -> TableColumnType.DECIMAL;
            case Types.BOOLEAN, Types.BIT -> TableColumnType.BOOLEAN;
            case Types.DATE -> TableColumnType.DATE;
            case Types.TIMESTAMP -> TableColumnType.TIMESTAMP_NTZ;
            case Types.TIMESTAMP_WITH_TIMEZONE -> TableColumnType.TIMESTAMP;
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> TableColumnType.BINARY;
            default -> throw new UnsupportedOperationException(
                    "Unsupported physical column type for structure snapshot: " + actual.nativeType()
            );
        };
    }

    private static TypeMappingResult<PlatformTypeDefinition> standardJdbcTypeToPlatform(
            JdbcTypeDescriptor physicalType
    ) {
        String nativeType = physicalType.nativeTypeName();
        return switch (physicalType.jdbcType()) {
            case Types.BOOLEAN -> TypeMappingResult.exact(PlatformTypeDefinition.of(PlatformDataType.BOOLEAN));
            case Types.BIT -> TypeMappingResult.normalized(
                    PlatformTypeDefinition.of(PlatformDataType.BOOLEAN),
                    "JDBC BIT 按布尔类型归一化"
            );
            case Types.TINYINT -> TypeMappingResult.exact(PlatformTypeDefinition.of(PlatformDataType.BYTE));
            case Types.SMALLINT -> TypeMappingResult.exact(PlatformTypeDefinition.of(PlatformDataType.SHORT));
            case Types.INTEGER -> TypeMappingResult.exact(PlatformTypeDefinition.of(PlatformDataType.INTEGER));
            case Types.BIGINT -> TypeMappingResult.exact(PlatformTypeDefinition.of(PlatformDataType.LONG));
            case Types.REAL -> TypeMappingResult.exact(PlatformTypeDefinition.of(PlatformDataType.FLOAT));
            case Types.FLOAT, Types.DOUBLE -> TypeMappingResult.exact(
                    PlatformTypeDefinition.of(PlatformDataType.DOUBLE)
            );
            case Types.NUMERIC, Types.DECIMAL -> decimalMapping(physicalType);
            case Types.CHAR, Types.VARCHAR, Types.NCHAR, Types.NVARCHAR -> boundedStringMapping(physicalType);
            case Types.LONGVARCHAR, Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB -> TypeMappingResult.normalized(
                    PlatformTypeDefinition.string(null),
                    "数据库长文本按无长度上限的 STRING 归一化"
            );
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> TypeMappingResult.exact(
                    PlatformTypeDefinition.of(PlatformDataType.BINARY)
            );
            case Types.DATE -> TypeMappingResult.exact(PlatformTypeDefinition.of(PlatformDataType.DATE));
            case Types.TIMESTAMP -> TypeMappingResult.exact(
                    PlatformTypeDefinition.of(PlatformDataType.TIMESTAMP_NTZ)
            );
            case Types.TIMESTAMP_WITH_TIMEZONE -> TypeMappingResult.exact(
                    PlatformTypeDefinition.of(PlatformDataType.TIMESTAMP)
            );
            default -> TypeMappingResult.unsupported(
                    "暂不支持数据库字段类型：" + (nativeType.isBlank() ? physicalType.jdbcType() : nativeType)
            );
        };
    }

    private static TypeMappingResult<PlatformTypeDefinition> boundedStringMapping(JdbcTypeDescriptor physicalType) {
        if (physicalType.length() == null || physicalType.length() < 1) {
            return TypeMappingResult.lossy(
                    PlatformTypeDefinition.string(null),
                    "数据库未返回字符串长度，不能安全保留长度约束"
            );
        }
        return TypeMappingResult.exact(PlatformTypeDefinition.string(physicalType.length()));
    }

    private static TypeMappingResult<PlatformTypeDefinition> decimalMapping(JdbcTypeDescriptor physicalType) {
        Integer precision = physicalType.precision();
        Integer scale = physicalType.scale();
        if (precision == null || precision < 1 || scale == null || scale < 0 || scale > precision) {
            return TypeMappingResult.unsupported("数据库未返回有效的小数精度和小数位");
        }
        if (precision > 38) {
            return TypeMappingResult.unsupported("小数精度 " + precision + " 超过平台与 Spark 支持上限 38");
        }
        return TypeMappingResult.exact(PlatformTypeDefinition.decimal(precision, scale));
    }

    private static PhysicalTypeDefinition physical(TableColumnType type) {
        return new PhysicalTypeDefinition(type, null, null, null);
    }

    private static Integer requireLength(ColumnMetadata actual) {
        if (actual.length() == null || actual.length() < 1) {
            throw new UnsupportedOperationException("String column length is unavailable: " + actual.name());
        }
        return actual.length();
    }

    private static Integer requirePrecision(ColumnMetadata actual) {
        if (actual.precision() == null || actual.precision() < 1) {
            throw new UnsupportedOperationException("Decimal precision is unavailable: " + actual.name());
        }
        return actual.precision();
    }

    private static Integer requireScale(ColumnMetadata actual) {
        if (actual.scale() == null || actual.scale() < 0) {
            throw new UnsupportedOperationException("Decimal scale is unavailable: " + actual.name());
        }
        return actual.scale();
    }

    protected String describeColumn(TableColumnDefinition column) {
        return columnTypeSql(column) + (column.nullable() ? "" : " NOT NULL");
    }

    private static String describeActualColumn(ColumnMetadata column) {
        StringBuilder result = new StringBuilder(column.nativeType() == null ? "未知类型" : column.nativeType());
        if (column.length() != null) {
            result.append('(').append(column.length()).append(')');
        } else if (column.precision() != null) {
            result.append('(').append(column.precision());
            if (column.scale() != null) result.append(',').append(column.scale());
            result.append(')');
        }
        if (!column.nullable()) result.append(" NOT NULL");
        if (column.spatial() != null) {
            result.append(" [")
                    .append(column.spatial().nativeGeometryKind())
                    .append(", SRID=")
                    .append(column.spatial().spatialReferenceId())
                    .append(']');
        }
        return result.toString();
    }

    private static boolean containsAny(String value, String... values) {
        if (value == null) return false;
        String normalized = value.toUpperCase(Locale.ROOT);
        return java.util.Arrays.stream(values).anyMatch(normalized::contains);
    }

    private static String normalizeIdentifier(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static Set<DatabaseCapability> capabilitiesFor(String id) {
        Set<DatabaseCapability> capabilities = EnumSet.of(
                DatabaseCapability.TEST_CONNECTION,
                DatabaseCapability.LIST_NAMESPACES,
                DatabaseCapability.LIST_TABLES,
                DatabaseCapability.READ_TABLE_METADATA,
                DatabaseCapability.PREVIEW_DATA,
                DatabaseCapability.STANDARD_QUERY,
                DatabaseCapability.QUERY_METADATA,
                DatabaseCapability.INSERT_SELECT
        );
        if ("POSTGRESQL".equals(id)) {
            capabilities.add(DatabaseCapability.SQL_SERVICE_QUERY);
            capabilities.add(DatabaseCapability.OVERWRITE_INSERT_SELECT);
        }
        if ("POSTGRESQL".equals(id) || "MYSQL".equals(id)) {
            capabilities.add(DatabaseCapability.ROW_UPSERT);
        }
        if ("POSTGRESQL".equals(id) || "MYSQL".equals(id) || "CLICKHOUSE".equals(id)) {
            capabilities.add(DatabaseCapability.CREATE_TABLE);
        }
        return capabilities;
    }

    protected static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
