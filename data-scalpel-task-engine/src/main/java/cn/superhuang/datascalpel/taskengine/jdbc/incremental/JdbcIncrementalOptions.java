package cn.superhuang.datascalpel.taskengine.jdbc.incremental;

import cn.superhuang.data.scalpel.contract.task.JdbcIncrementalStartPosition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.apache.spark.sql.util.CaseInsensitiveStringMap;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

record JdbcIncrementalOptions(
        String dataSourceId,
        String nodeId,
        String sourceSignature,
        String databaseType,
        String driverClassName,
        String jdbcUrl,
        String catalogName,
        String schemaName,
        String username,
        String password,
        Map<String, String> jdbcProperties,
        String tableName,
        String incrementalTimeColumn,
        PlatformDataType temporalType,
        ZoneId cursorTimeZone,
        int visibilityDelaySeconds,
        JdbcIncrementalStartPosition startPosition,
        Instant startTime,
        String resumeOffset
) implements Serializable {

    JdbcIncrementalOptions {
        jdbcProperties = Map.copyOf(jdbcProperties);
    }

    static JdbcIncrementalOptions from(CaseInsensitiveStringMap options) {
        int delay;
        try {
            delay = Integer.parseInt(required(options, "visibilityDelaySeconds"));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("visibilityDelaySeconds must be an integer", exception);
        }
        if (delay < 0 || delay > 3600) {
            throw new IllegalArgumentException("visibilityDelaySeconds must be between 0 and 3600");
        }
        JdbcIncrementalStartPosition position = JdbcIncrementalStartPosition.valueOf(
                required(options, "startPosition").toUpperCase(java.util.Locale.ROOT));
        Instant startTime = optional(options.get("startTime")) == null
                ? null : Instant.parse(options.get("startTime"));
        if (position == JdbcIncrementalStartPosition.AT_TIME && startTime == null) {
            throw new IllegalArgumentException("startTime is required for AT_TIME");
        }
        Map<String, String> properties = new LinkedHashMap<>();
        options.asCaseSensitiveMap().forEach((key, value) -> {
            if (key.startsWith("jdbcProperty.")) {
                properties.put(key.substring("jdbcProperty.".length()), value);
            }
        });
        return new JdbcIncrementalOptions(
                required(options, "dataSourceId"), required(options, "nodeId"),
                required(options, "sourceSignature"), required(options, "databaseType"),
                required(options, "driverClassName"), required(options, "jdbcUrl"),
                optional(options.get("catalogName")), optional(options.get("schemaName")),
                required(options, "username"), options.get("password"), properties,
                required(options, "tableName"), required(options, "incrementalTimeColumn"),
                PlatformDataType.valueOf(required(options, "temporalType")),
                ZoneId.of(required(options, "cursorTimeZone")), delay, position, startTime,
                optional(options.get("resumeOffset"))
        );
    }

    private static String required(CaseInsensitiveStringMap options, String key) {
        String value = optional(options.get(key));
        if (value == null) throw new IllegalArgumentException(key + " is required");
        return value;
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    public String toString() {
        return "JdbcIncrementalOptions[dataSourceId=" + dataSourceId
                + ", nodeId=" + nodeId + ", databaseType=" + databaseType
                + ", tableName=" + tableName + ", incrementalTimeColumn=" + incrementalTimeColumn
                + ", temporalType=" + temporalType + ", cursorTimeZone=" + cursorTimeZone
                + ", visibilityDelaySeconds=" + visibilityDelaySeconds
                + ", startPosition=" + startPosition + ']';
    }
}
