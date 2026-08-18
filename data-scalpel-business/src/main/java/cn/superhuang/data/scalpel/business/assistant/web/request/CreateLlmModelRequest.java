package cn.superhuang.data.scalpel.business.assistant.web.request;

import cn.superhuang.data.scalpel.business.assistant.domain.LlmProtocol;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLlmModelRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull LlmProtocol protocol,
        @NotBlank @Size(max = 1000) String baseUrl,
        @NotBlank @Size(max = 200) String modelName,
        @Size(max = 4000) String apiKey,
        @Size(max = 16_000) String extraRequestParameters
) {
}
