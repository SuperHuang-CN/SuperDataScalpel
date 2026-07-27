package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.UUID;

public record RunnerResultAvailableEvent(
        int messageVersion,
        UUID messageId,
        ExecutionMessageType messageType,
        Instant occurredAt,
        UUID engineId,
        UUID executionId,
        UUID runId,
        int attempt,
        String resultKey,
        String resultSha256
) implements RunnerExecutionEvent {
    public RunnerResultAvailableEvent {
        ExecutionContractValidation.envelope(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId, attempt);
        if (messageType != ExecutionMessageType.RUNNER_RESULT_AVAILABLE) throw new IllegalArgumentException("Runner 事件类型无效");
        resultKey = ExecutionContractValidation.objectKey(resultKey);
        ExecutionContractValidation.exactArtifactKey(resultKey, runId, attempt, "result.json");
        resultSha256 = ExecutionContractValidation.sha256(resultSha256);
    }
}
