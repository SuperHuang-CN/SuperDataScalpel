package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModel;

import java.util.UUID;

public record TaskModelReferenceResponse(UUID modelId, String modelCode, String modelName, int schemaVersion) {

    public static TaskModelReferenceResponse from(DataModel model) {
        return new TaskModelReferenceResponse(model.getId(), model.getCode(), model.getName(), model.getSchemaVersion());
    }
}
