package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;

import java.time.Instant;
import java.util.UUID;

public record DataTaskResponse(
        UUID id,
        String code,
        String name,
        UUID directoryId,
        TaskType type,
        TaskStatus status,
        String description,
        boolean definitionConfigured,
        Integer definitionVersion,
        UUID outputModelId,
        String outputModelName,
        Instant createdAt,
        Instant updatedAt
) {

    public static DataTaskResponse from(
            DataTask task,
            boolean definitionConfigured,
            Integer definitionVersion,
            UUID outputModelId,
            String outputModelName
    ) {
        return new DataTaskResponse(
                task.getId(), task.getCode(), task.getName(), task.getDirectoryId(), task.getType(), task.getStatus(),
                task.getDescription(), definitionConfigured, definitionVersion, outputModelId, outputModelName,
                task.getCreatedAt(), task.getUpdatedAt()
        );
    }
}
