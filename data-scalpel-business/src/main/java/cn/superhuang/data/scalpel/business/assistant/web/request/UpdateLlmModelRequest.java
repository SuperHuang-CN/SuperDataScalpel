package cn.superhuang.data.scalpel.business.assistant.web.request;

import cn.superhuang.data.scalpel.business.assistant.domain.LlmProtocol;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateLlmModelRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull LlmProtocol protocol,
        @NotBlank @Size(max = 1000) String baseUrl,
        @NotBlank @Size(max = 200) String modelName,
        @Size(max = 4000) String apiKey,
        boolean clearApiKey,
        @Size(max = 16_000) String extraRequestParameters
) {
    @AssertTrue(message = "不能同时填写新 API Key 和清除 API Key")
    public boolean isApiKeyChangeValid() {
        return !clearApiKey || apiKey == null || apiKey.isBlank();
    }
}
