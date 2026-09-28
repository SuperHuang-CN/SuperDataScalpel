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
        if ("org.postgresql.Driver".equals(driverClassName)) {
            // pgjdbc otherwise embeds bind values and failing rows into exceptions,
            // which Spark can log before the Runner's exception sanitizer runs.
            var safeProperties = new java.util.LinkedHashMap<>(properties);
            safeProperties.keySet().removeIf("logServerErrorDetail"::equalsIgnoreCase);
            safeProperties.put("logServerErrorDetail", "false");
            properties = Map.copyOf(safeProperties);
            // JDBC URL parameters take precedence over connection Properties.
            // pgjdbc takes the last value for a repeated URL parameter.
            if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:postgresql:")) {
                jdbcUrl += (jdbcUrl.contains("?") ? "&" : "?") + "logServerErrorDetail=false";
            }
        }
    }
}
