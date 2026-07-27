package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.UUID;

public record SubmitExecutionCommand(
        int messageVersion,
        UUID messageId,
        ExecutionMessageType messageType,
        Instant occurredAt,
        UUID engineId,
        UUID executionId,
        UUID runId,
        int attempt,
        UUID taskId,
        ExecutionTaskType taskType,
        int definitionVersion,
        Instant deadlineAt,
        ExecutionArtifactLocation artifacts
) implements ExecutionCommand {
    public SubmitExecutionCommand {
        ExecutionContractValidation.envelope(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId, attempt);
        if (messageType != ExecutionMessageType.SUBMIT_EXECUTION || taskId == null
                || taskType != ExecutionTaskType.SPARK_CANVAS || definitionVersion < 1
                || deadlineAt == null || !deadlineAt.isAfter(occurredAt) || artifacts == null) {
            throw new IllegalArgumentException("提交执行命令无效");
        }
        ExecutionContractValidation.exactArtifactKey(artifacts.manifestKey(), runId, attempt, "manifest.json");
        ExecutionContractValidation.exactArtifactKey(artifacts.resultKey(), runId, attempt, "result.json");
        ExecutionContractValidation.exactArtifactKey(artifacts.logKey(), runId, attempt, "console.log");
    }
}
