package cn.superhuang.data.scalpel.dialect.builtin;

import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionChoice;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionDefinition;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionType;
import cn.superhuang.data.scalpel.dialect.api.NamespaceMode;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;

import java.util.List;
import java.util.Properties;
import java.util.Set;

public final class SqlServerDialect extends AbstractJdbcDialect {

    public SqlServerDialect() {
        super(
                "SQL_SERVER", "SQL Server", 1433,
                "数据库", "Schema", "dbo", NamespaceMode.CATALOG_AND_SCHEMA,
                List.of(
                        booleanOption("encrypt", "加密连接", "true"),
                        booleanOption("trustServerCertificate", "信任服务器证书", "false")
                ),
                "com.microsoft.sqlserver.jdbc.SQLServerDriver", "[", "]", QualificationMode.CATALOG_AND_SCHEMA, PreviewStyle.TOP
        );
    }

    @Override
    public JdbcConnectionSpec createConnectionSpec(JdbcConnectionConfig config) {
        Properties properties = baseProperties(config);
        properties.setProperty("databaseName", config.databaseName());
        properties.setProperty("loginTimeout", "5");
        properties.setProperty("socketTimeout", "15000");
        properties.setProperty("encrypt", option(config, "encrypt", "true"));
        properties.setProperty("trustServerCertificate", option(config, "trustServerCertificate", "false"));
        copyOptions(config, properties, Set.of("encrypt", "trustServerCertificate"));
        String url = "jdbc:sqlserver://" + hostForUrl(config) + ":" + config.port();
        return new JdbcConnectionSpec(driverClassName(), url, properties);
    }

    @Override
    public String resolveCatalog(JdbcConnectionConfig config, String requestedCatalog) {
        return optional(requestedCatalog) == null ? config.databaseName() : requestedCatalog.trim();
    }

    private static ConnectionOptionDefinition booleanOption(String key, String label, String defaultValue) {
        return new ConnectionOptionDefinition(
                key, label, ConnectionOptionType.BOOLEAN, defaultValue,
                List.of(new ConnectionOptionChoice("true", "是"), new ConnectionOptionChoice("false", "否"))
        );
    }
}
