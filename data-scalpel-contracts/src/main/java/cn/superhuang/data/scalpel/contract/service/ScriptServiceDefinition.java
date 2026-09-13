package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Immutable Groovy source deployed to API Studio through a Service Engine. */
public record ScriptServiceDefinition(
        @JsonPropertyDescription("受平台约束执行的工具或数据服务脚本文本。")
        @NotBlank @Size(max = 500_000) String script
) {

    public ScriptServiceDefinition {
        if (script == null || script.isBlank()) {
            throw new IllegalArgumentException("Script is required");
        }
        if (script.length() > 500_000) {
            throw new IllegalArgumentException("Script exceeds 500000 characters");
        }
    }
}
