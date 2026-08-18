package cn.superhuang.data.scalpel.business.assistant.gateway;

import cn.superhuang.data.scalpel.business.assistant.domain.LlmProtocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface LlmGateway {

    Completion complete(RuntimeModel model, CompletionRequest request);

    record RuntimeModel(
            UUID id,
            String displayName,
            LlmProtocol protocol,
            String baseUrl,
            String modelName,
            String apiKey,
            Map<String, Object> extraRequestParameters
    ) {
        public RuntimeModel {
            extraRequestParameters = extraRequestParameters == null
                    ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(extraRequestParameters));
        }
    }

    record CompletionRequest(
            List<Message> messages,
            List<ToolDefinition> tools,
            String requiredToolName
    ) {
        public CompletionRequest {
            messages = List.copyOf(messages);
            tools = List.copyOf(tools);
        }
    }

    record Message(
            String role,
            String content,
            String toolCallId,
            List<ToolCall> toolCalls
    ) {
        public static Message system(String content) { return new Message("system", content, null, List.of()); }
        public static Message user(String content) { return new Message("user", content, null, List.of()); }
        public static Message assistant(String content, List<ToolCall> calls) {
            return new Message("assistant", content, null, calls == null ? List.of() : List.copyOf(calls));
        }
        public static Message tool(String toolCallId, String content) {
            return new Message("tool", content, toolCallId, List.of());
        }
    }

    record ToolDefinition(String name, String description, Map<String, Object> parameters) {
    }

    record ToolCall(String id, String name, String argumentsJson) {
    }

    record Completion(String content, List<ToolCall> toolCalls, Integer promptTokens, Integer completionTokens) {
        public Completion {
            toolCalls = List.copyOf(toolCalls);
        }
    }
}
