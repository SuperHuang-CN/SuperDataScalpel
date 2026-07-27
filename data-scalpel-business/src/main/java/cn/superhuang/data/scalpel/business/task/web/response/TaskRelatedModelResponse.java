package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;

import java.util.List;
import java.util.UUID;

public record TaskRelatedModelResponse(
        UUID modelId,
        String modelCode,
        String modelName,
        DataModelStatus modelStatus,
        PhysicalTableMode physicalTableMode,
        int schemaVersion,
        List<ModelTaskRelationRole> roles,
        List<TaskModelReferenceLocationResponse> locations
) {
}
