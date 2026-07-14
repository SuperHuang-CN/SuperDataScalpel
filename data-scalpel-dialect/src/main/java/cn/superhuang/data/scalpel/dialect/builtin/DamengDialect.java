package cn.superhuang.data.scalpel.dialect.builtin;

import cn.superhuang.data.scalpel.dialect.api.NamespaceMode;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;

import java.util.List;
import java.util.Locale;
import java.util.Properties;

public final class DamengDialect extends AbstractJdbcDialect {

    public DamengDialect() {
        super(
                "DAMENG", "达梦", 5236,
                "数据库", "Schema", null, NamespaceMode.SCHEMA, List.of(),
                "dm.jdbc.driver.DmDriver", "\"", "\"", QualificationMode.SCHEMA, PreviewStyle.ROWNUM
        );
    }

    @Override
    public JdbcConnectionSpec createConnectionSpec(JdbcConnectionConfig config) {
        Properties properties = baseProperties(config);
        properties.setProperty("connectTimeout", "5000");
        properties.setProperty("socketTimeout", "15000");
        String url = "jdbc:dm://" + hostForUrl(config) + ":" + config.port() + "/" + pathSegment(config.databaseName());
        return new JdbcConnectionSpec(driverClassName(), url, properties);
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
}
