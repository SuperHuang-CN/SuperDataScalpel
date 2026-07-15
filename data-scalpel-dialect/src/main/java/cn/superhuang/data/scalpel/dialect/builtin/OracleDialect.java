package cn.superhuang.data.scalpel.dialect.builtin;

import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionChoice;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionDefinition;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionType;
import cn.superhuang.data.scalpel.dialect.api.NamespaceMode;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;

import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

public final class OracleDialect extends AbstractJdbcDialect {

    public OracleDialect() {
        super(
                "ORACLE", "Oracle", 1521,
                "服务名/SID", "Schema", null, NamespaceMode.SCHEMA,
                List.of(new ConnectionOptionDefinition(
                        "connectionMode", "连接方式", ConnectionOptionType.SELECT, "SERVICE",
                        List.of(
                                new ConnectionOptionChoice("SERVICE", "Service Name"),
                                new ConnectionOptionChoice("SID", "SID")
                        )
                )),
                "oracle.jdbc.OracleDriver", "\"", "\"", QualificationMode.SCHEMA, PreviewStyle.FETCH_FIRST
        );
    }

    @Override
    public JdbcConnectionSpec createConnectionSpec(JdbcConnectionConfig config) {
        Properties properties = baseProperties(config);
        properties.setProperty("oracle.net.CONNECT_TIMEOUT", "5000");
        properties.setProperty("oracle.jdbc.ReadTimeout", "15000");
        copyOptions(config, new Properties(), Set.of("connectionMode"));
        String mode = option(config, "connectionMode", "SERVICE").toUpperCase(Locale.ROOT);
        String url = switch (mode) {
            case "SERVICE" -> "jdbc:oracle:thin:@//" + hostForUrl(config) + ":" + config.port() + "/" + pathSegment(config.databaseName());
            case "SID" -> "jdbc:oracle:thin:@" + hostForUrl(config) + ":" + config.port() + ":" + config.databaseName();
            default -> throw new IllegalArgumentException("Oracle 连接方式只支持 SERVICE 或 SID");
        };
        return new JdbcConnectionSpec(driverClassName(), url, properties, config.schemaName());
    }

    @Override
    public String resolveCatalog(JdbcConnectionConfig config, String requestedCatalog) {
        return null;
    }

    @Override
    public String resolveSchema(JdbcConnectionConfig config, String requestedSchema) {
        String resolved = super.resolveSchema(config, requestedSchema);
        return resolved == null ? config.username().toUpperCase(Locale.ROOT) : resolved;
    }

    @Override
    public String validationQuery() {
        return "SELECT 1 FROM DUAL";
    }
}
