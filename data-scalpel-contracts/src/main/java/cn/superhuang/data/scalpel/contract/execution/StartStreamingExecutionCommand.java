package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Starts or resumes a streaming deployment. There is intentionally no deadline:
 * the application remains active until an explicit stop command or a failure.
 */
public record StartStreamingExecutionCommand(
        int messageVersion,
        UUID messageId,
        ExecutionMessageType messageType,
        Instant occurredAt,
        UUID engineId,
        UUID executionId,
        UUID runId,
        int attempt,
        UUID taskId,
        UUID deploymentId,
        int definitionVersion,
        String checkpointKeyPrefix,
        ExecutionTaskType taskType,
        ExecutionArtifactLocation artifacts,
        ExecutionUserJarArtifact userJar,
        List<SparkConfigurationEntry> sparkConf,
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
