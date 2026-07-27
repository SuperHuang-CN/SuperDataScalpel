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

public final class KingbaseDialect extends AbstractJdbcDialect {

    public KingbaseDialect() {
        super(
                "KINGBASE", "人大金仓", 54321,
                "数据库", "Schema", "public", NamespaceMode.SCHEMA,
                List.of(new ConnectionOptionDefinition(
                        "sslmode", "SSL 模式", ConnectionOptionType.SELECT, null,
                        List.of(
                                new ConnectionOptionChoice("disable", "禁用"),
                                new ConnectionOptionChoice("prefer", "优先"),
                                new ConnectionOptionChoice("require", "必须")
                        )
                )),
                "com.kingbase8.Driver", "\"", "\"", QualificationMode.SCHEMA, PreviewStyle.LIMIT
        );
    }

    @Override
    public JdbcConnectionSpec createConnectionSpec(JdbcConnectionConfig config) {
        Properties properties = baseProperties(config);
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "15");
        applyConnectionOptions(
                config, properties, Set.of(),
                Set.of("connectTimeout", "socketTimeout", "currentSchema")
        );
        String url = "jdbc:kingbase8://" + hostForUrl(config) + ":" + config.port() + "/" + pathSegment(config.databaseName());
        return new JdbcConnectionSpec(driverClassName(), url, properties, config.schemaName());
    }

    @Override
    public String resolveCatalog(JdbcConnectionConfig config, String requestedCatalog) {
        return optional(requestedCatalog) == null ? config.databaseName() : requestedCatalog.trim();
    }
}
