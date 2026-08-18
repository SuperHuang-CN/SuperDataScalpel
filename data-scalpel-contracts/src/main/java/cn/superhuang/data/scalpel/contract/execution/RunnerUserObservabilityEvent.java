package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.UUID;

public record RunnerUserObservabilityEvent(
        int messageVersion,
        UUID messageId,
        ExecutionMessageType messageType,
        Instant occurredAt,
        UUID engineId,
        UUID executionId,
        UUID runId,
        int attempt,
        UserJobObservabilitySnapshot observability
) implements RunnerExecutionEvent {
    public RunnerUserObservabilityEvent {
        ExecutionContractValidation.envelope(
                messageVersion, messageId, messageType, occurredAt,
                engineId, executionId, runId, attempt);
        if (messageType != ExecutionMessageType.RUNNER_USER_OBSERVABILITY || observability == null) {
            throw new IllegalArgumentException("Runner 用户作业观测事件无效");
        }
    }
}
