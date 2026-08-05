package cn.superhuang.data.scalpel.contract.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Unsaved Groovy draft executed by a Service Engine through API Studio. */
public record ScriptDraftExecutionRequest(
        @NotNull UUID dataSourceId,
        @NotBlank @Size(max = 255) String routePath,
        @NotBlank @Size(max = 500_000) String script,
        Object body,
        Map<String, Object> query,
        Map<String, String> headers,
        Map<String, String> pathVariables,
        Map<String, Object> cookies,
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
