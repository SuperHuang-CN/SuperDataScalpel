package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

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
        RunnerControlChannel runnerControl,
        List<QualitySampleArtifactUpload> qualitySamples,
        LaunchUserJarDownload userJar
) {
    public static final int CURRENT_VERSION = 4;

    public TaskExecutionLaunchDescriptor {
        qualitySamples = qualitySamples == null ? List.of() : List.copyOf(qualitySamples);
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
        String samplePrefix = "task-runs/%s/attempts/%d/quality/samples/".formatted(runId, attempt);
        if (qualitySamples.stream().map(QualitySampleArtifactUpload::ruleId).distinct().count() != qualitySamples.size()
                || qualitySamples.stream().anyMatch(sample -> !sample.objectKey().equals(
                samplePrefix + sample.ruleId() + ".parquet"))) {
            throw new IllegalArgumentException("质检样本上传对象无效");
        }
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
                manifest, result, runnerEvent, null, null, List.of(), null);
    }

    public TaskExecutionLaunchDescriptor(
            int launchVersion, UUID engineId, UUID executionId, UUID runId, int attempt,
            Instant deadlineAt, RunnerSparkMode sparkMode, LaunchArtifactDownload manifest,
            LaunchArtifactUpload result, RunnerEventChannel runnerEvent,
            String checkpointUriPrefix, RunnerControlChannel runnerControl
    ) {
        this(launchVersion, engineId, executionId, runId, attempt, deadlineAt, sparkMode,
                manifest, result, runnerEvent, checkpointUriPrefix, runnerControl, List.of(), null);
    }

    public TaskExecutionLaunchDescriptor(
            int launchVersion, UUID engineId, UUID executionId, UUID runId, int attempt,
            Instant deadlineAt, RunnerSparkMode sparkMode, LaunchArtifactDownload manifest,
            LaunchArtifactUpload result, RunnerEventChannel runnerEvent, String checkpointUriPrefix,
            RunnerControlChannel runnerControl, List<QualitySampleArtifactUpload> qualitySamples
    ) {
        this(launchVersion, engineId, executionId, runId, attempt, deadlineAt, sparkMode,
                manifest, result, runnerEvent, checkpointUriPrefix, runnerControl, qualitySamples, null);
    }
}
