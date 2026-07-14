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

public final class ClickHouseDialect extends AbstractJdbcDialect {

    public ClickHouseDialect() {
        super(
                "CLICKHOUSE", "ClickHouse", 8123,
                "数据库", "Schema", null, NamespaceMode.CATALOG,
                List.of(new ConnectionOptionDefinition(
                        "ssl", "使用 SSL", ConnectionOptionType.BOOLEAN, "false",
                        List.of(new ConnectionOptionChoice("true", "是"), new ConnectionOptionChoice("false", "否"))
                )),
                "com.clickhouse.jdbc.ClickHouseDriver", "`", "`", QualificationMode.CATALOG, PreviewStyle.LIMIT
        );
    }

    @Override
    public JdbcConnectionSpec createConnectionSpec(JdbcConnectionConfig config) {
        Properties properties = baseProperties(config);
        properties.setProperty("connection_timeout", "5000");
        properties.setProperty("socket_timeout", "15000");
        properties.setProperty("ssl", option(config, "ssl", "false"));
        copyOptions(config, properties, Set.of("ssl"));
        String scheme = Boolean.parseBoolean(option(config, "ssl", "false")) ? "https" : "http";
        String url = "jdbc:clickhouse:" + scheme + "://" + hostForUrl(config) + ":" + config.port() + "/" + pathSegment(config.databaseName());
        return new JdbcConnectionSpec(driverClassName(), url, properties);
    }

    @Override
    public String resolveCatalog(JdbcConnectionConfig config, String requestedCatalog) {
        return optional(requestedCatalog) == null ? config.databaseName() : requestedCatalog.trim();
    }

    @Override
    public String resolveSchema(JdbcConnectionConfig config, String requestedSchema) {
        return null;
    }
}
