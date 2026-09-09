package cn.superhuang.data.scalpel.contract.service;

import java.util.List;
import java.util.UUID;

/** Read-only pool summaries; contains neither connection configuration nor SQL text. */
public record EngineDataSourcePoolSummariesResponse(
        String engineCode,
        String capturedAt,
        List<Entry> dataSources
) {
    public record Entry(UUID dataSourceId, JdbcPoolMonitorStatus status, JdbcPoolSummary pool) {
    }
}
