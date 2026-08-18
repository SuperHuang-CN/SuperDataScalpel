package cn.superhuang.data.scalpel.dispatcher.web.response;

import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;
import cn.superhuang.data.scalpel.contract.execution.SafeExecutionError;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherExecutionState;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherTaskExecution;

import java.time.Instant;
import java.util.UUID;
import cn.superhuang.data.scalpel.contract.quality.QualitySummary;

public record DispatcherExecutionResponse(
        UUID executionId,
        UUID runId,
        int attempt,
        UUID engineId,
        ExecutionBackendType backendType,
        DispatcherExecutionState state,
        String externalExecutionId,
        String trackingUrl,
        long sequence,
        Instant deadlineAt,
        Instant queuedAt,
        Instant startedAt,
        Instant endedAt,
        Long affectedRows,
        QualitySummary qualitySummary,
        String errorCode,
        String errorMessage,
        SafeExecutionError executionError
) {
    public static DispatcherExecutionResponse from(DispatcherTaskExecution execution) {
        return new DispatcherExecutionResponse(
                execution.getExecutionId(), execution.getRunId(), execution.getAttempt(), execution.getEngineId(),
                execution.getBackendType(), execution.getState(), execution.getExternalExecutionId(),
                execution.getTrackingUrl(), execution.getEventSequence(), execution.getDeadlineAt(),
                execution.getQueuedAt(), execution.getStartedAt(), execution.getEndedAt(),
                execution.getAffectedRows(), execution.getQualitySummary(),
                execution.getSafeErrorCode(), execution.getSafeErrorMessage(),
                execution.getSafeExecutionError()
        );
    }
}
