package cn.superhuang.datascalpel.taskengine.contract;



public enum RuntimeDatabaseType {
    POSTGRESQL("jdbc:postgresql://"),
    MYSQL("jdbc:mysql://"),
    ORACLE("jdbc:oracle:thin:@"),
    SQL_SERVER("jdbc:sqlserver://"),
    CLICKHOUSE("jdbc:clickhouse:"),
    DAMENG("jdbc:dm://"),
    OPENGAUSS("jdbc:opengauss://"),
    KINGBASE("jdbc:kingbase8://"),
    TDENGINE_WEBSOCKET("jdbc:TAOS-WS://"),
    TDENGINE_RESTFUL("jdbc:TAOS-RS://");

    private final String jdbcUrlPrefix;

    RuntimeDatabaseType(String jdbcUrlPrefix) {
        this.jdbcUrlPrefix = jdbcUrlPrefix;
    }

    public String jdbcUrlPrefix() {
        return jdbcUrlPrefix;
    }
}
