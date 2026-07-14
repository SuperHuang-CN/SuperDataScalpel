package cn.superhuang.data.scalpel.dialect.builtin;

import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionDefinition;
import cn.superhuang.data.scalpel.dialect.api.DatabaseCapability;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDefinition;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.NamespaceMode;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

abstract class AbstractJdbcDialect implements DatabaseDialect {

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
                EnumSet.allOf(DatabaseCapability.class),
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

    protected final Properties baseProperties(JdbcConnectionConfig config) {
        Properties properties = new Properties();
        properties.setProperty("user", config.username());
        if (config.password() != null) {
            properties.setProperty("password", config.password());
        }
        return properties;
    }

    protected final void copyOptions(
            JdbcConnectionConfig config,
            Properties properties,
            Set<String> supportedKeys
    ) {
        for (var entry : config.options().entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            if (!supportedKeys.contains(entry.getKey())) {
                throw new IllegalArgumentException(
                        definition.displayName() + " 不支持连接参数：" + entry.getKey()
                );
            }
            properties.setProperty(entry.getKey(), entry.getValue().trim());
        }
    }

    protected final String option(JdbcConnectionConfig config, String key, String defaultValue) {
        String value = config.options().get(key);
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

    private String qualifiedName(TableIdentifier table) {
        return switch (qualificationMode) {
            case CATALOG -> join(table.catalog(), table.table());
            case SCHEMA -> join(table.schema(), table.table());
            case CATALOG_AND_SCHEMA -> join(table.catalog(), table.schema(), table.table());
        };
    }

    private String join(String... parts) {
        return java.util.Arrays.stream(parts)
                .filter(part -> part != null && !part.isBlank())
                .map(this::quoteIdentifier)
                .collect(java.util.stream.Collectors.joining("."));
    }

    private String quoteIdentifier(String identifier) {
        return quoteStart + identifier.replace(quoteEnd, quoteEnd + quoteEnd) + quoteEnd;
    }

    protected static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
