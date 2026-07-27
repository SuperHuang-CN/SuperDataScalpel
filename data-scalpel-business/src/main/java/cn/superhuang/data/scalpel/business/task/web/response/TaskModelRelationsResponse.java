package cn.superhuang.data.scalpel.business.task.web.response;

import java.util.List;
import java.util.UUID;

public record TaskModelRelationsResponse(
        UUID taskId,
        boolean configured,
        Integer definitionVersion,
        List<TaskRelatedModelResponse> models
) {
}
