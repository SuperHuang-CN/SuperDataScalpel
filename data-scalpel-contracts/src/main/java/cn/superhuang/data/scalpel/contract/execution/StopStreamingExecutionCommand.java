package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.UUID;

/** Requests an orderly stop for the currently active streaming attempt. */
public record StopStreamingExecutionCommand(
        int messageVersion,
        UUID messageId,
        ExecutionMessageType messageType,
        Instant occurredAt,
        UUID engineId,
        UUID executionId,
        UUID runId,
        int attempt,
        UUID deploymentId,
        String reason,
        int gracePeriodSeconds
) implements ExecutionCommand {
    public StopStreamingExecutionCommand {
        ExecutionContractValidation.envelope(
                messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId, attempt);
        if (messageType != ExecutionMessageType.STOP_STREAMING_EXECUTION
                || deploymentId == null || gracePeriodSeconds < 1 || gracePeriodSeconds > 600) {
            throw new IllegalArgumentException("停止实时执行命令无效");
        }
        reason = ExecutionContractValidation.required(reason, 500, "停止原因");
    }
}
