package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.UUID;

public record CancelExecutionCommand(
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
    public CancelExecutionCommand {
        ExecutionContractValidation.envelope(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId, attempt);
        if (messageType != ExecutionMessageType.CANCEL_EXECUTION) {
            throw new IllegalArgumentException("取消执行命令类型无效");
        }
        reason = ExecutionContractValidation.required(reason, 500, "取消原因");
    }
}
