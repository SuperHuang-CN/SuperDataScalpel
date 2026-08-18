package cn.superhuang.data.scalpel.business.assistant.web.response;

import cn.superhuang.data.scalpel.business.assistant.domain.LlmModelConfiguration;
import cn.superhuang.data.scalpel.business.assistant.domain.LlmModelTestStatus;
import cn.superhuang.data.scalpel.business.assistant.domain.LlmProtocol;

import java.time.Instant;
import java.util.UUID;

public record LlmModelConfigurationResponse(
        UUID id,
        String name,
        LlmProtocol protocol,
        String baseUrl,
        String modelName,
        boolean apiKeyConfigured,
        String extraRequestParameters,
        boolean enabled,
        boolean defaultModel,
        LlmModelTestStatus testStatus,
        Instant lastTestedAt,
        String testMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static LlmModelConfigurationResponse from(LlmModelConfiguration model) {
        return new LlmModelConfigurationResponse(
                model.getId(), model.getName(), model.getProtocol(), model.getBaseUrl(), model.getModelName(),
                model.getApiKeyCiphertext() != null, model.getExtraRequestParameters(),
                model.isEnabled(), model.isDefaultModel(), model.getTestStatus(),
                model.getLastTestedAt(), model.getTestMessage(), model.getCreatedAt(), model.getUpdatedAt()
        );
    }
}
