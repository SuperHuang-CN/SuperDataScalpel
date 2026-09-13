package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

public record SubmitExecutionCommand(
        @JsonPropertyDescription("Kafka 执行消息协议版本；发送方必须使用接收方支持的版本。")
        int messageVersion,
        @JsonPropertyDescription("本条命令或事件的唯一 UUID，用于去重和审计。")
        UUID messageId,
        @JsonPropertyDescription("消息类型判别值，必须与当前命令或事件结构一致。")
        ExecutionMessageType messageType,
        @JsonPropertyDescription("事件发生时间。")
        Instant occurredAt,
        @JsonPropertyDescription("服务或计算引擎 UUID。")
        UUID engineId,
        @JsonPropertyDescription("外部执行 UUID。")
        UUID executionId,
        @JsonPropertyDescription("任务运行 UUID。")
        UUID runId,
        @JsonPropertyDescription("本次任务运行的执行尝试序号，从 1 开始。")
        int attempt,
        @JsonPropertyDescription("任务 UUID。")
        UUID taskId,
        @JsonPropertyDescription("任务类型，决定定义和执行协议。")
        ExecutionTaskType taskType,
        @JsonPropertyDescription("任务定义版本。")
        int definitionVersion,
        @JsonPropertyDescription("本次执行绝对截止时间；未设置平台截止时间时为空。")
        Instant deadlineAt,
        @JsonPropertyDescription("本次执行下载 Manifest、上传结果及其他制品的位置。")
        ExecutionArtifactLocation artifacts,
        @JsonPropertyDescription("需要为其输出失败样本的质量规则 UUID 列表。")
        List<UUID> qualitySampleRuleIds,
        @JsonPropertyDescription("每条质量规则最多写入的失败样本行数。")
        int qualitySampleLimit,
        @JsonPropertyDescription("本次执行使用的用户 JAR 下载描述；非 Spark JAR 任务为空。")
        ExecutionUserJarArtifact userJar,
        @JsonPropertyDescription("经平台白名单校验后传给 Spark 的名称/值配置快照。")
        List<SparkConfigurationEntry> sparkConf,
        @JsonPropertyDescription("本次 Runner 的 Driver/Executor CPU、内存和实例数资源规格。")
        SparkExecutionResourceSpec executionResources
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
            ExecutionArtifactLocation artifacts, List<UUID> qualitySampleRuleIds, int qualitySampleLimit,
            ExecutionUserJarArtifact userJar, List<SparkConfigurationEntry> sparkConf
    ) {
        this(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId,
                attempt, taskId, taskType, definitionVersion, deadlineAt, artifacts,
                qualitySampleRuleIds, qualitySampleLimit, userJar, sparkConf, null);
    }

    public SubmitExecutionCommand(
            int messageVersion, UUID messageId, ExecutionMessageType messageType, Instant occurredAt,
            UUID engineId, UUID executionId, UUID runId, int attempt, UUID taskId,
            ExecutionTaskType taskType, int definitionVersion, Instant deadlineAt,
            ExecutionArtifactLocation artifacts
    ) {
        this(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId,
                attempt, taskId, taskType, definitionVersion, deadlineAt, artifacts, List.of(), 0, null, List.of(), null);
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
                null, List.of(), null);
    }

    public SubmitExecutionCommand(
            int messageVersion, UUID messageId, ExecutionMessageType messageType, Instant occurredAt,
            UUID engineId, UUID executionId, UUID runId, int attempt, UUID taskId,
            ExecutionTaskType taskType, int definitionVersion, Instant deadlineAt,
            ExecutionArtifactLocation artifacts, List<UUID> qualitySampleRuleIds, int qualitySampleLimit
    ) {
        this(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId,
                attempt, taskId, taskType, definitionVersion, deadlineAt, artifacts,
                qualitySampleRuleIds, qualitySampleLimit, null, List.of(), null);
    }
}
