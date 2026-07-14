package cn.superhuang.data.scalpel.dialect.connection;

import java.util.Properties;

public record JdbcConnectionSpec(String driverClassName, String jdbcUrl, Properties properties) {

    public JdbcConnectionSpec {
        Properties copy = new Properties();
        copy.putAll(properties);
        properties = copy;
    }
}
