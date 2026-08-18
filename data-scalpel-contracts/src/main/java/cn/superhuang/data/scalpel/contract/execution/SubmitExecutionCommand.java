package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

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
        ExecutionArtifactLocation artifacts,
        List<UUID> qualitySampleRuleIds,
        int qualitySampleLimit,
        ExecutionUserJarArtifact userJar,
        List<SparkConfigurationEntry> sparkConf
) implements ExecutionCommand {
    public SubmitExecutionCommand {
        qualitySampleRuleIds = qualitySampleRuleIds == null ? List.of() : qualitySampleRuleIds.stream().distinct().sorted().toList();
        sparkConf = sparkConf == null ? List.of() : List.copyOf(sparkConf);
        ExecutionContractValidation.envelope(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId, attempt);
        if (messageType != ExecutionMessageType.SUBMIT_EXECUTION || taskId == null
                || taskType != ExecutionTaskType.SPARK_CANVAS
                && taskType != ExecutionTaskType.SPARK_MODEL_QUALITY
                && taskType != ExecutionTaskType.SPARK_JAR || definitionVersion < 1
                || deadlineAt == null || !deadlineAt.isAfter(occurredAt) || artifacts == null) {
            throw new IllegalArgumentException("提交执行命令无效");
        }
        ExecutionContractValidation.exactArtifactKey(artifacts.manifestKey(), runId, attempt, "manifest.json");
        ExecutionContractValidation.exactArtifactKey(artifacts.resultKey(), runId, attempt, "result.json");
        ExecutionContractValidation.exactArtifactKey(artifacts.logKey(), runId, attempt, "console.log");
        if (taskType != ExecutionTaskType.SPARK_MODEL_QUALITY && !qualitySampleRuleIds.isEmpty()
                || qualitySampleRuleIds.stream().anyMatch(java.util.Objects::isNull)
                || qualitySampleLimit < 0 || qualitySampleLimit > 1000
                || !qualitySampleRuleIds.isEmpty() && qualitySampleLimit == 0
                || taskType != ExecutionTaskType.SPARK_MODEL_QUALITY && qualitySampleLimit != 0) {
            throw new IllegalArgumentException("质检样本规则清单无效");
        }
        if (taskType == ExecutionTaskType.SPARK_JAR != (userJar != null)
                || taskType != ExecutionTaskType.SPARK_JAR && !sparkConf.isEmpty()
                || sparkConf.size() > 100
                || sparkConf.stream().map(SparkConfigurationEntry::name).distinct().count() != sparkConf.size()) {
            throw new IllegalArgumentException("Spark JAR 提交载荷无效");
        }
        if (userJar != null) {
            ExecutionContractValidation.exactArtifactKey(userJar.objectKey(), runId, attempt, "user-job.jar");
        }
    }

    public SubmitExecutionCommand(
            int messageVersion, UUID messageId, ExecutionMessageType messageType, Instant occurredAt,
            UUID engineId, UUID executionId, UUID runId, int attempt, UUID taskId,
            ExecutionTaskType taskType, int definitionVersion, Instant deadlineAt,
            ExecutionArtifactLocation artifacts
    ) {
        this(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId,
                attempt, taskId, taskType, definitionVersion, deadlineAt, artifacts, List.of(), 0, null, List.of());
    }

    public SubmitExecutionCommand(
            int messageVersion, UUID messageId, ExecutionMessageType messageType, Instant occurredAt,
            UUID engineId, UUID executionId, UUID runId, int attempt, UUID taskId,
            ExecutionTaskType taskType, int definitionVersion, Instant deadlineAt,
            ExecutionArtifactLocation artifacts, List<UUID> qualitySampleRuleIds
    ) {
        this(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId,
                attempt, taskId, taskType, definitionVersion, deadlineAt, artifacts,
                qualitySampleRuleIds, qualitySampleRuleIds == null || qualitySampleRuleIds.isEmpty() ? 0 : 100,
                null, List.of());
    }

    public SubmitExecutionCommand(
            int messageVersion, UUID messageId, ExecutionMessageType messageType, Instant occurredAt,
            UUID engineId, UUID executionId, UUID runId, int attempt, UUID taskId,
            ExecutionTaskType taskType, int definitionVersion, Instant deadlineAt,
            ExecutionArtifactLocation artifacts, List<UUID> qualitySampleRuleIds, int qualitySampleLimit
    ) {
        this(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId,
                attempt, taskId, taskType, definitionVersion, deadlineAt, artifacts,
                qualitySampleRuleIds, qualitySampleLimit, null, List.of());
    }
}
