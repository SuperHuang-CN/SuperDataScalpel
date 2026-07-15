package cn.superhuang.data.scalpel.dialect.builtin;

import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionChoice;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionDefinition;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionType;
import cn.superhuang.data.scalpel.dialect.api.NamespaceMode;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;
import cn.superhuang.data.scalpel.dialect.model.TableColumnDefinition;

import java.util.List;
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
        copyOptions(config, properties, Set.of("useSSL", "serverTimezone"));
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
        };
    }
}
