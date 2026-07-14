package cn.superhuang.data.scalpel.dialect.connection;

import java.util.Map;

public record JdbcConnectionConfig(
        String host,
        int port,
        String databaseName,
        String schemaName,
        String username,
        String password,
        Map<String, String> options
) {
    public JdbcConnectionConfig {
        host = required(host, "host");
        databaseName = required(databaseName, "databaseName");
        username = required(username, "username");
        schemaName = optional(schemaName);
        options = options == null ? Map.of() : Map.copyOf(options);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
