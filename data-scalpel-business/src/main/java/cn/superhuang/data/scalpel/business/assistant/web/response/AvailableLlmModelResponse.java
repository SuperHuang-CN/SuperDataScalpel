package cn.superhuang.data.scalpel.business.assistant.web.response;

import cn.superhuang.data.scalpel.business.assistant.domain.LlmModelConfiguration;

import java.util.UUID;

public record AvailableLlmModelResponse(UUID id, String name, String modelName, boolean defaultModel) {
    public static AvailableLlmModelResponse from(LlmModelConfiguration model) {
        return new AvailableLlmModelResponse(
                model.getId(), model.getName(), model.getModelName(), model.isDefaultModel()
        );
    }
}
