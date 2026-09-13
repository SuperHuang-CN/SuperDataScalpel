package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Unsaved Groovy draft executed by a Service Engine through API Studio. */
public record ScriptDraftExecutionRequest(
        @JsonPropertyDescription("数据源 UUID。")
        @NotNull UUID dataSourceId,
        @JsonPropertyDescription("服务对外路由路径。")
        @NotBlank @Size(max = 255) String routePath,
        @JsonPropertyDescription("受平台约束执行的工具或数据服务脚本文本。")
        @NotBlank @Size(max = 500_000) String script,
        @JsonPropertyDescription("JSON 请求体。")
        Object body,
        @JsonPropertyDescription("HTTP 查询参数映射。")
        Map<String, Object> query,
        @JsonPropertyDescription("HTTP 请求头映射；认证头由平台控制。")
        Map<String, String> headers,
        @JsonPropertyDescription("脚本试运行模拟请求的路径变量映射。")
        Map<String, String> pathVariables,
        @JsonPropertyDescription("脚本试运行模拟请求的 Cookie 映射，仅供受限请求上下文读取。")
        Map<String, Object> cookies,
        @JsonPropertyDescription("脚本试运行模拟的受限会话属性映射。")
        Map<String, Object> session
) {

    public ScriptDraftExecutionRequest {
        query = immutableCopy(query);
        headers = immutableCopy(headers);
        pathVariables = immutableCopy(pathVariables);
        cookies = immutableCopy(cookies);
        session = immutableCopy(session);
    }

    private static <K, V> Map<K, V> immutableCopy(Map<K, V> source) {
        return source == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(source));
    }
}
