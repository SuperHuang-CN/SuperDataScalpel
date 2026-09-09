package cn.superhuang.data.scalpel.business.mcp.web.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveMcpToolRequest(
        @NotBlank @Size(max=64) @Pattern(regexp="[a-z][a-z0-9_-]{1,63}") String code,
        @NotBlank @Size(max=100) String name,
        @Size(max=1000) String description,
        @NotBlank @Size(max=65536) String inputSchemaJson,
        @Size(max=65536) String outputSchemaJson,
        @NotBlank @Size(max=204800) String script,
        @Size(max=65536) String examplesJson,
        boolean enabled,
        @Min(0) @Max(100000) int sortOrder,
        @Min(0) Long expectedRevision
) {}
