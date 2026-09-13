package cn.superhuang.datascalpel.taskengine.contract;

import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.TaskDefinition;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType;
import cn.superhuang.data.scalpel.contract.execution.CanvasTrialSpec;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityExecutionPayload;
import cn.superhuang.data.scalpel.contract.execution.SparkJarExecutionPayload;
import cn.superhuang.data.scalpel.contract.execution.SparkStreamingJarExecutionPayload;
import com.fasterxml.jackson.annotation.JsonProperty;

public record TaskExecutionManifest(
        Integer manifestVersion,
        Execution execution,
        TaskDefinition task,
        MetadataSnapshot metadataSnapshot,
        List<RuntimeDataSource> runtimeDataSources,
        Streaming streaming,
        RuntimeFileStorage runtimeFileStorage,
        List<RuntimeFileInput> runtimeFileInputs,
        SnapshotSyncLimits snapshotSyncLimits,
        @JsonProperty("taskType") ExecutionTaskType executionTaskType,
        ModelQualityExecutionPayload modelQuality,
        SparkJarExecutionPayload sparkJarJob,
        SparkStreamingJarExecutionPayload streamingSparkJarJob,
        CanvasTrialSpec canvasTrial
) {
    public static final int CURRENT_MANIFEST_VERSION = 28;
    /** Retained as a symbolic value for diagnostics/tests; Runner does not accept it. */
    public static final int PREVIOUS_MANIFEST_VERSION = 27;

    public TaskExecutionManifest {
        runtimeDataSources = runtimeDataSources == null ? List.of() : List.copyOf(runtimeDataSources);
        runtimeFileInputs = runtimeFileInputs == null ? List.of() : List.copyOf(runtimeFileInputs);
        snapshotSyncLimits = snapshotSyncLimits == null ? SnapshotSyncLimits.defaults() : snapshotSyncLimits;
        executionTaskType = executionTaskType == null
                ? task != null && task.executionMode() == cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode.STREAMING
                ? ExecutionTaskType.SPARK_STREAMING_CANVAS : ExecutionTaskType.SPARK_CANVAS
                : executionTaskType;
        if (executionTaskType == ExecutionTaskType.SPARK_JAR) {
            if (sparkJarJob == null || streamingSparkJarJob != null
                    || task != null || streaming != null || modelQuality != null) {
                throw new IllegalArgumentException("Spark JAR Manifest 载荷无效");
            }
        } else if (executionTaskType == ExecutionTaskType.SPARK_STREAMING_JAR) {
            if (streamingSparkJarJob == null || sparkJarJob != null
                    || task != null || streaming != null || modelQuality != null) {
                throw new IllegalArgumentException("Spark Streaming JAR Manifest 载荷无效");
            }
        } else if (sparkJarJob != null || streamingSparkJarJob != null) {
            throw new IllegalArgumentException("非 Spark JAR Manifest 不得包含 JAR 载荷");
        }
        if (canvasTrial != null && (executionTaskType != ExecutionTaskType.SPARK_CANVAS
                || task == null
                || task.executionMode() != cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode.BATCH)) {
            throw new IllegalArgumentException("Canvas 试运行 Manifest 载荷无效");
        }
    }

    public TaskExecutionManifest(
            Integer manifestVersion, Execution execution, TaskDefinition task,
            MetadataSnapshot metadataSnapshot, List<RuntimeDataSource> runtimeDataSources,
            Streaming streaming, RuntimeFileStorage runtimeFileStorage,
            List<RuntimeFileInput> runtimeFileInputs, SnapshotSyncLimits snapshotSyncLimits,
            ExecutionTaskType executionTaskType, ModelQualityExecutionPayload modelQuality,
            SparkJarExecutionPayload sparkJarJob, SparkStreamingJarExecutionPayload streamingSparkJarJob
    ) {
        this(manifestVersion, execution, task, metadataSnapshot, runtimeDataSources, streaming,
                runtimeFileStorage, runtimeFileInputs, snapshotSyncLimits, executionTaskType,
                modelQuality, sparkJarJob, streamingSparkJarJob, null);
    }

    public TaskExecutionManifest(
            Integer manifestVersion,
            Execution execution,
            TaskDefinition task,
            MetadataSnapshot metadataSnapshot,
            List<RuntimeDataSource> runtimeDataSources
    ) {
        this(manifestVersion, execution, task, metadataSnapshot, runtimeDataSources,
                null, null, List.of(), SnapshotSyncLimits.defaults(), null, null, null, null, null);
    }

    public TaskExecutionManifest(
            Integer manifestVersion,
            Execution execution,
            TaskDefinition task,
            MetadataSnapshot metadataSnapshot,
            List<RuntimeDataSource> runtimeDataSources,
            Streaming streaming
    ) {
        this(manifestVersion, execution, task, metadataSnapshot, runtimeDataSources,
                streaming, null, List.of(), SnapshotSyncLimits.defaults(), null, null, null, null, null);
    }

    public TaskExecutionManifest(
            Integer manifestVersion,
            Execution execution,
            TaskDefinition task,
            MetadataSnapshot metadataSnapshot,
            List<RuntimeDataSource> runtimeDataSources,
            Streaming streaming,
            RuntimeFileStorage runtimeFileStorage,
            List<RuntimeFileInput> runtimeFileInputs
    ) {
        this(manifestVersion, execution, task, metadataSnapshot, runtimeDataSources,
                streaming, runtimeFileStorage, runtimeFileInputs, SnapshotSyncLimits.defaults(), null, null, null, null, null);
    }

    public TaskExecutionManifest(
            Integer manifestVersion,
            Execution execution,
            TaskDefinition task,
            MetadataSnapshot metadataSnapshot,
            List<RuntimeDataSource> runtimeDataSources,
            Streaming streaming,
            RuntimeFileStorage runtimeFileStorage,
            List<RuntimeFileInput> runtimeFileInputs,
            SnapshotSyncLimits snapshotSyncLimits
    ) {
        this(manifestVersion, execution, task, metadataSnapshot, runtimeDataSources, streaming,
                runtimeFileStorage, runtimeFileInputs, snapshotSyncLimits, null, null, null, null, null);
    }

    public TaskExecutionManifest(
            Integer manifestVersion, Execution execution, TaskDefinition task,
            MetadataSnapshot metadataSnapshot, List<RuntimeDataSource> runtimeDataSources,
            Streaming streaming, RuntimeFileStorage runtimeFileStorage,
            List<RuntimeFileInput> runtimeFileInputs, SnapshotSyncLimits snapshotSyncLimits,
            ExecutionTaskType executionTaskType, ModelQualityExecutionPayload modelQuality
    ) {
        this(manifestVersion, execution, task, metadataSnapshot, runtimeDataSources, streaming,
                runtimeFileStorage, runtimeFileInputs, snapshotSyncLimits, executionTaskType,
                modelQuality, null, null, null);
    }

    public record Execution(
            UUID executionId,
            UUID runId,
            UUID taskId,
            Integer attempt,
            Integer definitionVersion,
            Instant createdAt,
            Instant deadlineAt,
            UUID deploymentId
    ) {
        public Execution(
                UUID executionId,
                UUID runId,
                UUID taskId,
                Integer attempt,
                Integer definitionVersion,
                Instant createdAt,
                Instant deadlineAt
        ) {
            this(executionId, runId, taskId, attempt, definitionVersion, createdAt, deadlineAt, null);
        }
    }

    public record Streaming(
            int triggerIntervalSeconds,
            String checkpointKeyPrefix,
            String sourceNodeId,
            String sourceSignature,
            String initialSourceOffset
    ) {
        public Streaming(int triggerIntervalSeconds, String checkpointKeyPrefix) {
            this(triggerIntervalSeconds, checkpointKeyPrefix, null, null, null);
        }
    }
}
