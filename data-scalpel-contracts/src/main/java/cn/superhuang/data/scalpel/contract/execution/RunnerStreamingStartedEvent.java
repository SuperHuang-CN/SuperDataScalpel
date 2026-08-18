package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.List;
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
        String sparkApplicationId,
        List<StreamingQueryDescriptor> queries
) implements RunnerExecutionEvent {
    public RunnerStreamingStartedEvent {
        queries = queries == null ? List.of() : List.copyOf(queries);
        ExecutionContractValidation.envelope(
                messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId, attempt);
        if (messageType != ExecutionMessageType.RUNNER_STREAMING_STARTED || deploymentId == null) {
            throw new IllegalArgumentException("Runner 实时启动事件无效");
        }
        sparkApplicationId = ExecutionContractValidation.optional(sparkApplicationId, 200, "Spark Application ID");
    }

    public RunnerStreamingStartedEvent(
            int messageVersion, UUID messageId, ExecutionMessageType messageType, Instant occurredAt,
            UUID engineId, UUID executionId, UUID runId, int attempt, UUID deploymentId,
            String sparkApplicationId
    ) {
        this(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId,
                attempt, deploymentId, sparkApplicationId, List.of());
    }
}
