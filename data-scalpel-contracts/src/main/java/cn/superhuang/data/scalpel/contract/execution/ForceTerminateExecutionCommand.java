package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.UUID;

public record ForceTerminateExecutionCommand(
        int messageVersion,
        UUID messageId,
        ExecutionMessageType messageType,
        Instant occurredAt,
        UUID engineId,
        UUID executionId,
        UUID runId,
        int attempt,
        String reason
) implements ExecutionCommand {
    public ForceTerminateExecutionCommand {
        ExecutionContractValidation.envelope(
                messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId, attempt);
        if (messageType != ExecutionMessageType.FORCE_TERMINATE_EXECUTION) {
            throw new IllegalArgumentException("强制终止执行命令类型无效");
        }
        reason = ExecutionContractValidation.required(reason, 500, "强制终止原因");
    }
}
