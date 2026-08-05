package cn.superhuang.datascalpel.taskengine.contract;

import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.TaskDefinition;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TaskExecutionManifest(
        Integer manifestVersion,
        Execution execution,
        TaskDefinition task,
        MetadataSnapshot metadataSnapshot,
        List<RuntimeDataSource> runtimeDataSources,
        Streaming streaming,
        RuntimeFileStorage runtimeFileStorage,
        List<RuntimeFileInput> runtimeFileInputs
) {
    public static final int CURRENT_MANIFEST_VERSION = 10;
    public static final int PREVIOUS_MANIFEST_VERSION = 9;

    public TaskExecutionManifest {
        runtimeDataSources = runtimeDataSources == null ? List.of() : List.copyOf(runtimeDataSources);
        runtimeFileInputs = runtimeFileInputs == null ? List.of() : List.copyOf(runtimeFileInputs);
    }

    public TaskExecutionManifest(
            Integer manifestVersion,
            Execution execution,
            TaskDefinition task,
            MetadataSnapshot metadataSnapshot,
            List<RuntimeDataSource> runtimeDataSources
    ) {
        this(manifestVersion, execution, task, metadataSnapshot, runtimeDataSources, null, null, List.of());
    }

    public TaskExecutionManifest(
            Integer manifestVersion,
            Execution execution,
            TaskDefinition task,
            MetadataSnapshot metadataSnapshot,
            List<RuntimeDataSource> runtimeDataSources,
            Streaming streaming
    ) {
        this(manifestVersion, execution, task, metadataSnapshot, runtimeDataSources, streaming, null, List.of());
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
            String checkpointKeyPrefix
    ) {
    }
}
