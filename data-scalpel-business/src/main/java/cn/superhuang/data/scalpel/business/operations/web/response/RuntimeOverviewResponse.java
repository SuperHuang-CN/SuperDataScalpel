package cn.superhuang.data.scalpel.business.operations.web.response;
import java.time.Instant;
public record RuntimeOverviewResponse(Instant from, Instant to, Instant collectedAt, RuntimeTaskMetrics tasks,
    RuntimeEngineMetrics engines, long openAlerts, Long pendingSignals, Long failedDeliveries) {}
