package cn.superhuang.data.scalpel.contract.task;

import java.time.Instant;

public record JdbcIncrementalInputConfiguration(
        String dataSourceId,
        String tableName,
        String outputTableName,
        String incrementalTimeColumn,
        JdbcIncrementalStartPosition startPosition,
        Instant startTime,
        String cursorTimeZone,
        Integer visibilityDelaySeconds,
        Integer triggerIntervalSeconds
) {
    public static final String DEFAULT_CURSOR_TIME_ZONE = "UTC";
    public static final int DEFAULT_VISIBILITY_DELAY_SECONDS = 30;
    public static final int DEFAULT_TRIGGER_INTERVAL_SECONDS = 60;

    public JdbcIncrementalInputConfiguration {
        startPosition = startPosition == null ? JdbcIncrementalStartPosition.LATEST : startPosition;
        startTime = startPosition == JdbcIncrementalStartPosition.AT_TIME ? startTime : null;
        cursorTimeZone = cursorTimeZone == null || cursorTimeZone.isBlank()
                ? DEFAULT_CURSOR_TIME_ZONE
                : cursorTimeZone.trim();
        visibilityDelaySeconds = visibilityDelaySeconds == null
                ? DEFAULT_VISIBILITY_DELAY_SECONDS
                : visibilityDelaySeconds;
        triggerIntervalSeconds = triggerIntervalSeconds == null
                ? DEFAULT_TRIGGER_INTERVAL_SECONDS
                : triggerIntervalSeconds;
    }
}
