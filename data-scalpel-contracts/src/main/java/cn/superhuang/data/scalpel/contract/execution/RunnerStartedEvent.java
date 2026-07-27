package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.UUID;

public record RunnerStartedEvent(
        int messageVersion,
        UUID messageId,
        ExecutionMessageType messageType,
        Instant occurredAt,
        UUID engineId,
        UUID executionId,
        UUID runId,
        int attempt,
        String sparkApplicationId
) implements RunnerExecutionEvent {
    public RunnerStartedEvent {
        ExecutionContractValidation.envelope(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId, attempt);
        if (messageType != ExecutionMessageType.RUNNER_STARTED) throw new IllegalArgumentException("Runner 事件类型无效");
        sparkApplicationId = ExecutionContractValidation.optional(sparkApplicationId, 200, "Spark Application ID");
    }
}
