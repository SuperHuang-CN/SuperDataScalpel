package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.UUID;

public record RunnerStreamingStoppedEvent(
        int messageVersion,
        UUID messageId,
        ExecutionMessageType messageType,
        Instant occurredAt,
        UUID engineId,
        UUID executionId,
        UUID runId,
        int attempt,
        UUID deploymentId,
        Instant stoppedAt,
        String message
) implements RunnerExecutionEvent {
    public RunnerStreamingStoppedEvent {
        ExecutionContractValidation.envelope(
                messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId, attempt);
        if (messageType != ExecutionMessageType.RUNNER_STREAMING_STOPPED
                || deploymentId == null || stoppedAt == null) {
            throw new IllegalArgumentException("Runner 实时停止事件无效");
        }
        message = ExecutionContractValidation.optional(message, 1000, "停止消息");
    }
}
