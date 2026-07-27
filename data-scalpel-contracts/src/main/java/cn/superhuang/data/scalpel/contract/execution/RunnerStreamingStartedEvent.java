package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.UUID;

public record RunnerStreamingStartedEvent(
        int messageVersion,
        UUID messageId,
        ExecutionMessageType messageType,
        Instant occurredAt,
        UUID engineId,
        UUID executionId,
        UUID runId,
        int attempt,
        UUID deploymentId,
        String sparkApplicationId
) implements RunnerExecutionEvent {
    public RunnerStreamingStartedEvent {
        ExecutionContractValidation.envelope(
                messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId, attempt);
        if (messageType != ExecutionMessageType.RUNNER_STREAMING_STARTED || deploymentId == null) {
            throw new IllegalArgumentException("Runner 实时启动事件无效");
        }
        sparkApplicationId = ExecutionContractValidation.optional(sparkApplicationId, 200, "Spark Application ID");
    }
}
