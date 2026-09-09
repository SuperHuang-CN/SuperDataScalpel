package cn.superhuang.data.scalpel.business.compute.client;

import java.time.Instant;
import java.util.UUID;

public record DispatcherExecutionLogResponse(
        UUID engineId,
        UUID executionId,
        UUID runId,
        int attempt,
        Status status,
        String content,
        Instant collectedAt,
        int sizeBytes,
        boolean truncated,
        String message
) {
    public enum Status {
        WAITING,
        AVAILABLE,
        UNAVAILABLE
    }
}
