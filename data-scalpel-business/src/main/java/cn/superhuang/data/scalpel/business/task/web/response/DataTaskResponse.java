package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;

import java.time.Instant;
import java.util.UUID;

public record DataTaskResponse(
        UUID id,
        String name,
        UUID directoryId,
        TaskType type,
        TaskStatus status,
        String description,
        UUID computeEngineId,
        String computeEngineName,
        boolean definitionConfigured,
        Integer definitionVersion,
        UUID outputModelId,
        String outputModelName,
        UUID qualityTargetModelId,
        String qualityTargetModelName,
        Instant createdAt,
        Instant updatedAt
) {

    public static DataTaskResponse from(
            DataTask task,
            String computeEngineName,
            boolean definitionConfigured,
            Integer definitionVersion,
            UUID outputModelId,
            String outputModelName,
            UUID qualityTargetModelId,
            String qualityTargetModelName
    ) {
        return new DataTaskResponse(
                task.getId(), task.getName(), task.getDirectoryId(), task.getType(), task.getStatus(),
                task.getDescription(), task.getComputeEngineId(), computeEngineName,
                definitionConfigured, definitionVersion, outputModelId, outputModelName,
                qualityTargetModelId, qualityTargetModelName,
                task.getCreatedAt(), task.getUpdatedAt()
        );
    }

    public static DataTaskResponse from(
            DataTask task,
            String computeEngineName,
            boolean definitionConfigured,
            Integer definitionVersion,
            UUID outputModelId,
            String outputModelName
    ) {
        return from(task, computeEngineName, definitionConfigured, definitionVersion,
                outputModelId, outputModelName, null, null);
    }

    public static DataTaskResponse from(
            DataTask task,
            boolean definitionConfigured,
            Integer definitionVersion,
            UUID outputModelId,
            String outputModelName
    ) {
        return from(task, null, definitionConfigured, definitionVersion, outputModelId, outputModelName,
                null, null);
    }
}
