package cn.superhuang.datascalpel.taskengine.contract;



import java.util.Map;

public record RuntimeJdbcConnection(
        String driverClassName,
        String jdbcUrl,
        String catalogName,
        String schemaName,
        String username,
        String password,
        Map<String, String> properties
) {
    public RuntimeJdbcConnection {
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }
}
