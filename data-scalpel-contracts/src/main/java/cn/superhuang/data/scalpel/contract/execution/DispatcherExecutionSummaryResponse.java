package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.UUID;

/** Read-only execution row used by Dispatcher and Admin operational lists. */
public record DispatcherExecutionSummaryResponse(
        UUID executionId,
        UUID runId,
        UUID taskId,
        ExecutionTaskType taskType,
        int definitionVersion,
        DispatcherExecutionState state,
        String externalExecutionId,
        String trackingUrl,
        Instant deadlineAt,
        Instant queuedAt,
        Instant submissionStartedAt,
        Instant submittedAt,
        Instant startedAt,
        Instant endedAt,
        Instant lastObservedAt,
        String errorCode,
        String errorMessage,
        Long queuePosition
) {
}
