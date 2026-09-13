package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

public record TaskExecutionLaunchDescriptor(
        @JsonPropertyDescription("Task Runner 启动描述协议版本；必须等于 CURRENT_VERSION。")
        int launchVersion,
        @JsonPropertyDescription("服务或计算引擎 UUID。")
        UUID engineId,
        @JsonPropertyDescription("外部执行 UUID。")
        UUID executionId,
        @JsonPropertyDescription("任务运行 UUID。")
        UUID runId,
        @JsonPropertyDescription("本次任务运行的执行尝试序号，从 1 开始。")
        int attempt,
        @JsonPropertyDescription("本次执行绝对截止时间；未设置平台截止时间时为空。")
        Instant deadlineAt,
        @JsonPropertyDescription("Runner 的 Spark 执行模式，例如本地、YARN 或 Kubernetes。")
        RunnerSparkMode sparkMode,
        @JsonPropertyDescription("本次不可变执行 Manifest 的受限下载描述。")
        LaunchArtifactDownload manifest,
        @JsonPropertyDescription("Runner 上传 result.json 的受限目标。")
        LaunchArtifactUpload result,
        @JsonPropertyDescription("试运行预览制品上传目标；正式运行时为空。")
        LaunchArtifactUpload trialPreview,
        @JsonPropertyDescription("Runner 向 Dispatcher 发布生命周期事件的 Kafka 通道。")
        RunnerEventChannel runnerEvent,
        @JsonPropertyDescription("Runner 可使用的实时 Checkpoint URI 前缀；批任务为空。")
        String checkpointUriPrefix,
        @JsonPropertyDescription("实时 Runner 接收停止等控制命令的 Kafka 通道；批任务为空。")
        RunnerControlChannel runnerControl,
        @JsonPropertyDescription("每条质量规则失败样本 Parquet 的独立受限上传目标。")
        List<QualitySampleArtifactUpload> qualitySamples,
        @JsonPropertyDescription("本次执行使用的用户 JAR 下载描述；非 Spark JAR 任务为空。")
        LaunchUserJarDownload userJar
) {
    public static final int CURRENT_VERSION = 5;

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
        if (trialPreview != null) {
            ExecutionContractValidation.exactArtifactKey(
                    trialPreview.objectKey(), runId, attempt, "trial-preview.json");
        }
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
                manifest, result, null, runnerEvent, null, null, List.of(), null);
    }

    public TaskExecutionLaunchDescriptor(
            int launchVersion, UUID engineId, UUID executionId, UUID runId, int attempt,
            Instant deadlineAt, RunnerSparkMode sparkMode, LaunchArtifactDownload manifest,
            LaunchArtifactUpload result, RunnerEventChannel runnerEvent,
            String checkpointUriPrefix, RunnerControlChannel runnerControl
    ) {
        this(launchVersion, engineId, executionId, runId, attempt, deadlineAt, sparkMode,
                manifest, result, null, runnerEvent, checkpointUriPrefix, runnerControl, List.of(), null);
    }

    public TaskExecutionLaunchDescriptor(
            int launchVersion, UUID engineId, UUID executionId, UUID runId, int attempt,
            Instant deadlineAt, RunnerSparkMode sparkMode, LaunchArtifactDownload manifest,
            LaunchArtifactUpload result, RunnerEventChannel runnerEvent, String checkpointUriPrefix,
            RunnerControlChannel runnerControl, List<QualitySampleArtifactUpload> qualitySamples
    ) {
        this(launchVersion, engineId, executionId, runId, attempt, deadlineAt, sparkMode,
                manifest, result, null, runnerEvent, checkpointUriPrefix, runnerControl, qualitySamples, null);
    }
}
