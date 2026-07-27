package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ModelRelatedTaskResponse(
        UUID taskId,
        String taskName,
        TaskType taskType,
        TaskStatus taskStatus,
        int definitionVersion,
        List<ModelTaskRelationRole> roles,
        List<TaskModelReferenceLocationResponse> locations,
        Instant updatedAt
) {
}
