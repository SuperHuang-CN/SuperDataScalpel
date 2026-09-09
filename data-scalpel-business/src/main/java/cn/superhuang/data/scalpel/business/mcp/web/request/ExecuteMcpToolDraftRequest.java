package cn.superhuang.data.scalpel.business.mcp.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExecuteMcpToolDraftRequest(
        @NotBlank @Size(max=65536) String inputSchemaJson,
        @Size(max=65536) String outputSchemaJson,
        @NotBlank @Size(max=204800) String script,
        @NotBlank @Size(max=1048576) String argumentsJson
) {}
