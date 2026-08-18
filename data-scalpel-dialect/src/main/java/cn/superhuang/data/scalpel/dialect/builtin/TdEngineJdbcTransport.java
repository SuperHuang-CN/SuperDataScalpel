package cn.superhuang.data.scalpel.dialect.builtin;

enum TdEngineJdbcTransport {
    WEBSOCKET(
            "TDENGINE_WEBSOCKET",
            "TDengine WebSocket JDBC",
            "jdbc:TAOS-WS://",
            "com.taosdata.jdbc.ws.WebSocketDriver"
    ),
    RESTFUL(
            "TDENGINE_RESTFUL",
            "TDengine RESTful JDBC",
            "jdbc:TAOS-RS://",
            "com.taosdata.jdbc.rs.RestfulDriver"
    );

    private final String id;
    private final String displayName;
    private final String jdbcPrefix;
    private final String driverClassName;

    TdEngineJdbcTransport(String id, String displayName, String jdbcPrefix, String driverClassName) {
        this.id = id;
        this.displayName = displayName;
        this.jdbcPrefix = jdbcPrefix;
        this.driverClassName = driverClassName;
    }

    String id() {
        return id;
    }

    String displayName() {
        return displayName;
    }

    String jdbcPrefix() {
        return jdbcPrefix;
    }

    String driverClassName() {
        return driverClassName;
    }
}
