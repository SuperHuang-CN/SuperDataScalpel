package cn.superhuang.data.scalpel.business.assistant.gateway;

import cn.superhuang.data.scalpel.business.assistant.domain.LlmProtocol;
import cn.superhuang.data.scalpel.business.assistant.service.AssistantProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleLlmGateway implements LlmGateway {

    private final AssistantProperties properties;

    public OpenAiCompatibleLlmGateway(AssistantProperties properties) {
        this.properties = properties;
    }

    @Override
    public Completion complete(RuntimeModel model, CompletionRequest request) {
        if (model.protocol() != LlmProtocol.OPENAI_COMPATIBLE) {
            throw new LlmGatewayException("暂不支持该模型协议", false, null);
        }
        try {
            JsonNode root = client(model).post()
                    .uri(endpoint(model.baseUrl()))
                    .body(requestBody(model, request))
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null) throw new LlmGatewayException("模型服务返回空响应", false, null);
            JsonNode message = root.path("choices").path(0).path("message");
            if (message.isMissingNode()) throw new LlmGatewayException("模型响应缺少 message", false, null);
            String content = message.path("content").isTextual() ? message.path("content").asText() : null;
            List<ToolCall> calls = new ArrayList<>();
            for (JsonNode call : message.path("tool_calls")) {
                String id = call.path("id").asText();
                String name = call.path("function").path("name").asText();
                String arguments = call.path("function").path("arguments").asText("{}");
                if (!id.isBlank() && !name.isBlank()) calls.add(new ToolCall(id, name, arguments));
            }
            JsonNode usage = root.path("usage");
            Integer promptTokens = usage.path("prompt_tokens").canConvertToInt()
                    ? usage.path("prompt_tokens").asInt() : null;
            Integer completionTokens = usage.path("completion_tokens").canConvertToInt()
                    ? usage.path("completion_tokens").asInt() : null;
            return new Completion(content, calls, promptTokens, completionTokens);
        } catch (LlmGatewayException exception) {
            throw exception;
        } catch (RestClientException exception) {
            boolean timeout = isTimeout(exception);
            throw new LlmGatewayException(describeFailure(exception, timeout), timeout, exception);
        } catch (RuntimeException exception) {
            throw new LlmGatewayException("无法解析模型服务响应", false, exception);
        }
    }

    private RestClient client(RuntimeModel model) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.requestTimeout());
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        if (model.apiKey() != null && !model.apiKey().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + model.apiKey());
        }
        return builder.build();
    }

    private Map<String, Object> requestBody(RuntimeModel model, CompletionRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.putAll(model.extraRequestParameters());
        body.put("model", model.modelName());
        body.put("temperature", properties.temperature());
        body.put("max_tokens", properties.maxOutputTokens());
        body.put("messages", request.messages().stream().map(this::messageBody).toList());
        if (!request.tools().isEmpty()) {
            body.put("tools", request.tools().stream().map(tool -> Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", tool.name(),
                            "description", tool.description(),
                            "parameters", tool.parameters()
                    )
            )).toList());
        }
        if (request.requiredToolName() != null) {
            body.put("tool_choice", Map.of(
                    "type", "function",
                    "function", Map.of("name", request.requiredToolName())
            ));
        }
        return body;
    }

    private Map<String, Object> messageBody(Message message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("role", message.role());
        if (message.content() != null) body.put("content", message.content());
        if (message.toolCallId() != null) body.put("tool_call_id", message.toolCallId());
        if (!message.toolCalls().isEmpty()) {
            body.put("tool_calls", message.toolCalls().stream().map(call -> Map.of(
                    "id", call.id(),
                    "type", "function",
                    "function", Map.of("name", call.name(), "arguments", call.argumentsJson())
            )).toList());
        }
        return body;
    }

    private static String endpoint(String baseUrl) {
        return baseUrl.replaceAll("/+$", "") + "/chat/completions";
    }

    private static boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof HttpTimeoutException) return true;
            if (current instanceof ResourceAccessException && current.getMessage() != null
                    && current.getMessage().toLowerCase(java.util.Locale.ROOT).contains("timed out")) return true;
            current = current.getCause();
        }
        return false;
    }

    private static String describeFailure(RestClientException exception, boolean timeout) {
        if (timeout) return "模型服务响应超时，请检查网络或适当增加请求超时时间";
        if (exception instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            return switch (status) {
                case 400 -> "模型服务拒绝了请求，请检查模型标识和 OpenAI Compatible 工具调用能力";
                case 401 -> "模型服务认证失败，请检查 API Key";
                case 403 -> "模型服务拒绝访问，请检查 API Key 权限、业务空间和区域";
                case 404 -> "模型接口或模型不存在，请检查 Base URL 和模型标识";
                case 408 -> "模型服务请求超时，请稍后重试";
                case 429 -> "模型服务请求受限，请检查调用额度或频率限制";
                default -> status >= 500
                        ? "模型服务暂时异常（HTTP " + status + "），请稍后重试"
                        : "模型服务拒绝请求（HTTP " + status + "），请检查服务配置";
            };
        }
        if (hasCause(exception, UnknownHostException.class)) {
            return "无法解析模型服务域名，请检查 Base URL 和 DNS";
        }
        if (hasCause(exception, SSLException.class)) {
            return "模型服务 TLS 连接或证书校验失败";
        }
        if (hasCause(exception, NoRouteToHostException.class)) {
            return "无法访问模型服务网络，请检查服务器出口和路由";
        }
        if (hasCause(exception, ConnectException.class)) {
            return "无法连接模型服务，请检查 Base URL、端口和网络出口";
        }
        return "模型服务调用失败，请检查服务地址、网络和 API Key";
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) return true;
            current = current.getCause();
        }
        return false;
    }
}
