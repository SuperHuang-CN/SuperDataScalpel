package cn.superhuang.data.scalpel.business.service.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record ExecuteScriptDraftRequest(
        @NotNull UUID engineId,
        @NotNull UUID dataSourceId,
        @NotBlank @Size(max = 255) String routePath,
        @NotBlank @Size(max = 500_000) String script,
        Object body,
        Map<String, Object> query,
        Map<String, String> headers
) {
}
