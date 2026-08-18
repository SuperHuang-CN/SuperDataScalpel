package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DataSourceRelatedTaskResponse(
        UUID taskId,
        String taskName,
        TaskType taskType,
        TaskStatus taskStatus,
        int definitionVersion,
        List<DataSourceTaskRelationRole> roles,
        List<DataSourceRelationKind> relationKinds,
        List<DataSourceTaskReferenceLocationResponse> locations,
        Instant updatedAt
) {
}
