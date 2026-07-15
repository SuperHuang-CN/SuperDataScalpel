package cn.superhuang.data.scalpel.dialect.connection;

import java.util.Properties;

public record JdbcConnectionSpec(String driverClassName, String jdbcUrl, Properties properties, String schemaName) {

    public JdbcConnectionSpec {
        Properties copy = new Properties();
        copy.putAll(properties);
        properties = copy;
        schemaName = schemaName == null || schemaName.isBlank() ? null : schemaName.trim();
    }
}
