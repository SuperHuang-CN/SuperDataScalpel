package cn.superhuang.data.scalpel.business.compute.client;

import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;
import cn.superhuang.data.scalpel.contract.execution.SafeExecutionError;

import java.time.Instant;
import java.util.UUID;

public record DispatcherExecutionResponse(
        UUID executionId,
        UUID runId,
        int attempt,
        UUID engineId,
        ExecutionBackendType backendType,
        String state,
        long sequence,
        String externalExecutionId,
        String trackingUrl,
        Instant deadlineAt,
        Instant queuedAt,
        Instant startedAt,
        Instant endedAt,
        Long affectedRows,
        String errorCode,
        String errorMessage,
        SafeExecutionError executionError
) {
}
