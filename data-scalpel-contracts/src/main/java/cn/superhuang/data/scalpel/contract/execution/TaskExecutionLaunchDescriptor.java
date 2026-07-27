package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.UUID;

public record TaskExecutionLaunchDescriptor(
        int launchVersion,
        UUID engineId,
        UUID executionId,
        UUID runId,
        int attempt,
        Instant deadlineAt,
        RunnerSparkMode sparkMode,
        LaunchArtifactDownload manifest,
        LaunchArtifactUpload result,
        RunnerEventChannel runnerEvent,
        String checkpointUriPrefix,
        RunnerControlChannel runnerControl
) {
    public static final int CURRENT_VERSION = 2;

    public TaskExecutionLaunchDescriptor {
        if (launchVersion != CURRENT_VERSION || engineId == null || executionId == null || runId == null
                || attempt < 1 || sparkMode == null || manifest == null
                || result == null || runnerEvent == null) {
            throw new IllegalArgumentException("Task Runner 启动描述无效");
        }
        if (checkpointUriPrefix != null) {
            checkpointUriPrefix = checkpointUriPrefix.trim();
            if (checkpointUriPrefix.isEmpty() || checkpointUriPrefix.length() > 2000
                    || checkpointUriPrefix.contains("\r") || checkpointUriPrefix.contains("\n")) {
                throw new IllegalArgumentException("Checkpoint URI 前缀无效");
            }
        }
        ExecutionContractValidation.exactArtifactKey(result.objectKey(), runId, attempt, "result.json");
    }

    public TaskExecutionLaunchDescriptor(
            int launchVersion,
            UUID engineId,
            UUID executionId,
            UUID runId,
            int attempt,
            Instant deadlineAt,
            RunnerSparkMode sparkMode,
            LaunchArtifactDownload manifest,
            LaunchArtifactUpload result,
            RunnerEventChannel runnerEvent
    ) {
        this(launchVersion, engineId, executionId, runId, attempt, deadlineAt, sparkMode,
                manifest, result, runnerEvent, null, null);
    }
}
