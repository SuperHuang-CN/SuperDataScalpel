package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Starts or resumes a streaming deployment. There is intentionally no deadline:
 * the application remains active until an explicit stop command or a failure.
 */
public record StartStreamingExecutionCommand(
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
        @JsonPropertyDescription("实时任务部署 UUID，用于关联启动、进度、停止和 Checkpoint。")
        UUID deploymentId,
        @JsonPropertyDescription("任务定义版本。")
        int definitionVersion,
        @JsonPropertyDescription("该实时部署在对象存储中的受控 Checkpoint Key 前缀。")
        String checkpointKeyPrefix,
        @JsonPropertyDescription("任务类型，决定定义和执行协议。")
        ExecutionTaskType taskType,
        @JsonPropertyDescription("本次执行下载 Manifest、上传结果及其他制品的位置。")
        ExecutionArtifactLocation artifacts,
        @JsonPropertyDescription("本次执行使用的用户 JAR 下载描述；非 Spark JAR 任务为空。")
        ExecutionUserJarArtifact userJar,
        @JsonPropertyDescription("经平台白名单校验后传给 Spark 的名称/值配置快照。")
        List<SparkConfigurationEntry> sparkConf,
        @JsonPropertyDescription("本次 Runner 的 Driver/Executor CPU、内存和实例数资源规格。")
        SparkExecutionResourceSpec executionResources
) implements ExecutionCommand {
    public StartStreamingExecutionCommand {
        sparkConf = sparkConf == null ? List.of() : List.copyOf(sparkConf);
        checkpointKeyPrefix = ExecutionContractValidation.required(
                checkpointKeyPrefix, 500, "Checkpoint Key Prefix");
        ExecutionContractValidation.envelope(
                messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId, attempt);
        if (messageType != ExecutionMessageType.START_STREAMING_EXECUTION
                || taskId == null || deploymentId == null || definitionVersion < 1 || artifacts == null
                || checkpointKeyPrefix.contains("..") || checkpointKeyPrefix.startsWith("/")
                || taskType != ExecutionTaskType.SPARK_STREAMING_CANVAS
                && taskType != ExecutionTaskType.SPARK_STREAMING_JAR
                || taskType == ExecutionTaskType.SPARK_STREAMING_JAR != (userJar != null)
                || taskType != ExecutionTaskType.SPARK_STREAMING_JAR && !sparkConf.isEmpty()) {
            throw new IllegalArgumentException("启动实时执行命令无效");
        }
        ExecutionContractValidation.exactArtifactKey(artifacts.manifestKey(), runId, attempt, "manifest.json");
        ExecutionContractValidation.exactArtifactKey(artifacts.resultKey(), runId, attempt, "result.json");
        ExecutionContractValidation.exactArtifactKey(artifacts.logKey(), runId, attempt, "console.log");
        if (userJar != null) {
            ExecutionContractValidation.exactArtifactKey(userJar.objectKey(), runId, attempt, "user-job.jar");
        }
    }

    public StartStreamingExecutionCommand(
            int messageVersion, UUID messageId, ExecutionMessageType messageType, Instant occurredAt,
            UUID engineId, UUID executionId, UUID runId, int attempt, UUID taskId,
            UUID deploymentId, int definitionVersion, String checkpointKeyPrefix,
            ExecutionTaskType taskType, ExecutionArtifactLocation artifacts,
            ExecutionUserJarArtifact userJar, List<SparkConfigurationEntry> sparkConf
    ) {
        this(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId,
                attempt, taskId, deploymentId, definitionVersion, checkpointKeyPrefix,
                taskType, artifacts, userJar, sparkConf, null);
    }

    public StartStreamingExecutionCommand(
            int messageVersion, UUID messageId, ExecutionMessageType messageType, Instant occurredAt,
            UUID engineId, UUID executionId, UUID runId, int attempt, UUID taskId,
            UUID deploymentId, int definitionVersion, ExecutionArtifactLocation artifacts
    ) {
        this(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId,
                attempt, taskId, deploymentId, definitionVersion, "deployments/" + deploymentId,
                ExecutionTaskType.SPARK_STREAMING_CANVAS, artifacts, null, List.of(), null);
    }
}
