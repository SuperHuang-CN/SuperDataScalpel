package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.web.response.ModelTaskReferenceType;
import cn.superhuang.data.scalpel.business.task.web.response.ModelTaskRelationRole;

import java.util.UUID;

public record DataModelReferenceTaskResponse(
        UUID id,
        String name,
        TaskType type,
        TaskStatus status,
        ModelTaskRelationRole role,
        ModelTaskReferenceType referenceType,
        UUID nodeId,
        String nodeName
) {
}
