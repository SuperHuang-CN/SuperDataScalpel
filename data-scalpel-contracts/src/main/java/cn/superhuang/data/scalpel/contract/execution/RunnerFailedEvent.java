package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.UUID;

public record RunnerFailedEvent(
        int messageVersion,
        UUID messageId,
        ExecutionMessageType messageType,
        Instant occurredAt,
        UUID engineId,
        UUID executionId,
        UUID runId,
        int attempt,
        SafeExecutionError error
) implements RunnerExecutionEvent {
    public RunnerFailedEvent {
        ExecutionContractValidation.envelope(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId, attempt);
        if (messageType != ExecutionMessageType.RUNNER_FAILED || error == null) throw new IllegalArgumentException("Runner 失败事件无效");
    }
}
