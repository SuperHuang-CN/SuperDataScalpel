package cn.superhuang.data.scalpel.contract.service;

import java.util.List;
import java.util.UUID;

/** Read-only, bounded in-memory diagnostics. SQL previews are normalized by API Studio. */
public record EngineDataSourcePoolMonitorResponse(
        String engineCode,
        UUID dataSourceId,
        JdbcPoolMonitorStatus status,
        String capturedAt,
        JdbcPoolSummary pool,
        List<SqlConsumer> topConsumers,
        List<ActiveConnection> activeConnections,
        List<RecentSql> recentSql,
        List<SaturationIncident> incidents
) {
    public record SqlConsumer(
            String fingerprint, String sqlPreview, String operation, Integer connectionCount,
            Long totalHeldMs, Long maxHeldMs, Long maxExecutingMs
    ) {
    }

    public record ActiveConnection(
            String connectionId, String state, String borrowedAt, Long heldMs, String threadName,
            boolean transactionActive, String fingerprint, String sqlPreview, String operation,
            String sqlStartedAt, Long executingMs, String lastSqlPreview,
            String apiId, String apiPath, String executionId, boolean longRunning, boolean longHeld
    ) {
    }

    /** Aggregates over the retained recent execution window, not lifetime counters. */
    public record RecentSql(
            String fingerprint, String sqlPreview, String operation, Integer executions, Integer failures,
            Long totalDurationMs, Long maxDurationMs, String lastExecutedAt
    ) {
    }

    public record SaturationIncident(
            String occurredAt, String reason, String requestedSqlPreview, String requestedExecutionId,
            JdbcPoolSummary pool, List<SqlConsumer> topConsumers
    ) {
    }
}
