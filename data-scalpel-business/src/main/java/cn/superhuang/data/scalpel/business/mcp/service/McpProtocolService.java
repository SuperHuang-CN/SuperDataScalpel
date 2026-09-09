package cn.superhuang.data.scalpel.business.mcp.service;

import cn.superhuang.data.scalpel.business.mcp.config.McpPlatformProperties;
import cn.superhuang.data.scalpel.business.mcp.domain.McpToolRelease;
import cn.superhuang.data.scalpel.business.mcp.repository.McpServerReleaseRepository;
import cn.superhuang.data.scalpel.business.mcp.repository.McpToolReleaseRepository;
import cn.superhuang.data.scalpel.business.mcp.security.McpInvocationAuthenticationService.AuthenticatedServer;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator.ValidationResponse;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class McpProtocolService {
    private static final Logger LOG = LoggerFactory.getLogger(McpProtocolService.class);
    public static final List<String> PROTOCOLS = List.of("2024-11-05", "2025-03-26", "2025-06-18");
    private static final long CACHE_BYTES = 16 * 1024 * 1024;
    private final McpServerReleaseRepository releases;
    private final McpToolReleaseRepository tools;
    private final McpScriptExecutionService scripts;
    private final McpPlatformProperties properties;
    private final ObjectMapper mapper;
    private final McpJsonMapper sdkMapper = McpJsonMapper.createDefault();
    private final Map<UUID, RuntimeBundle> cache = new LinkedHashMap<>(16, .75f, true);
    private long cachedBytes;

    public McpProtocolService(McpServerReleaseRepository releases, McpToolReleaseRepository tools,
                              McpScriptExecutionService scripts, McpPlatformProperties properties, ObjectMapper mapper) {
        this.releases = releases; this.tools = tools; this.scripts = scripts;
        this.properties = properties; this.mapper = mapper;
    }

    public ProtocolResult handle(AuthenticatedServer auth, JsonNode request, String protocolVersion) {
        JsonNode id = request == null ? null : request.get("id");
        if (request == null || !request.isObject() || !"2.0".equals(request.path("jsonrpc").asText())
                || !request.path("method").isTextual() || request.path("method").asText().isBlank()
                || (id != null && !(id.isTextual() || id.isIntegralNumber()))
                || (id != null && id.toString().length() > 128)
                || (request.has("params") && !request.get("params").isObject())) {
            return ProtocolResult.error(null, -32600, "Invalid Request", null, protocolVersion);
        }
        String method = request.path("method").asText();
        if (id == null) {
            // JSON-RPC notifications never receive responses and never enter tool execution.
            return new ProtocolResult(Map.of(), true, method, null, protocolVersion, null);
        }
        if ("initialize".equals(method)) {
            JsonNode params = request.path("params");
            if (!params.path("protocolVersion").isTextual() || !params.path("capabilities").isObject()
                    || !params.path("clientInfo").isObject()) {
                return ProtocolResult.error(id, -32602, "Invalid params", method, protocolVersion);
            }
        }
        if ("tools/call".equals(method) && (!request.path("params").path("name").isTextual()
                || (request.path("params").has("arguments") && !request.path("params").get("arguments").isObject()))) {
            return ProtocolResult.error(id, -32602, "Invalid params", method, protocolVersion);
        }
        RuntimeBundle bundle = acquire(auth.releaseId());
        InvocationState state = new InvocationState();
        try {
            var rpc = new McpSchema.JSONRPCRequest("2.0", method, mapper.convertValue(id, Object.class),
                    request.has("params") ? mapper.convertValue(request.get("params"), Object.class) : null);
            var response = bundle.transport.handler.handleRequest(McpTransportContext.create(Map.of("invocation", state)), rpc)
                    .block(properties.executionTimeout().plusSeconds(2));
            if (state.httpError != null) throw state.httpError;
            if (response == null) return ProtocolResult.error(id, -32603, "Internal error", method, protocolVersion);
            if (response.error() != null) {
                int code = response.error().code();
                String message = code == -32601 ? "Method not found" : code == -32602 ? "Invalid params" : "Internal error";
                return ProtocolResult.error(id, code, message, method, protocolVersion);
            }
            Map<String, Object> body = sdkMapper.convertValue(response, new TypeRef<Map<String, Object>>() {});
            if ("tools/list".equals(method)) restoreSchemas(body, bundle);
            if ("initialize".equals(method)) {
                JsonNode result = mapper.valueToTree(body.get("result"));
                protocolVersion = result.path("protocolVersion").asText(protocolVersion);
            }
            return new ProtocolResult(body, false, method, state.toolCode, protocolVersion, state.errorCode);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // Do not log arbitrary SDK/script exception messages that may embed arguments.
            LOG.error("MCP protocol failure release={} exceptionType={}", auth.releaseId(), exception.getClass().getName());
            return ProtocolResult.error(id, -32603, "Internal error", method, protocolVersion);
        } finally {
            release(bundle);
        }
    }

    private McpSchema.CallToolResult call(McpTransportContext context, ToolDefinition tool, RuntimeBundle bundle,
                                        McpSchema.CallToolRequest request) {
        InvocationState state = (InvocationState) context.get("invocation");
        state.toolCode = tool.code();
        try {
            var execution = scripts.execute(tool.input(), tool.output(), tool.script(),
                    mapper.valueToTree(request.arguments() == null ? Map.of() : request.arguments()),
                    Map.of("serverCode", bundle.code, "releaseVersion", bundle.version, "toolCode", tool.code()));
            return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(execution.text())),
                    false, execution.structuredContent(), null);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 429) state.httpError = exception;
            state.errorCode = exception.getStatusCode().value() == 429 ? "EXECUTION_BUSY" : "INPUT_INVALID";
            return new McpSchema.CallToolResult("输入参数不符合 Tool 定义", true);
        } catch (McpScriptExecutionService.McpToolExecutionException exception) {
            state.errorCode = exception.code();
            return new McpSchema.CallToolResult(exception.getMessage(), true);
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreSchemas(Map<String, Object> body, RuntimeBundle bundle) {
        // SDK 0.17's typed input schema omits root-level JSON Schema keywords. Preserve
        // the complete saved schema in discovery; execution validates the same original.
        Map<String, Object> result = (Map<String, Object>) body.get("result");
        List<Map<String, Object>> definitions = (List<Map<String, Object>>) result.get("tools");
        for (Map<String, Object> definition : definitions) {
            ToolDefinition tool = bundle.definitions.get(String.valueOf(definition.get("name")));
            definition.put("inputSchema", mapper.readValue(tool.input(), Map.class));
            if (tool.output() == null) definition.remove("outputSchema");
            else definition.put("outputSchema", mapper.readValue(tool.output(), Map.class));
        }
    }

    private synchronized RuntimeBundle acquire(UUID id) {
        RuntimeBundle bundle = cache.get(id);
        if (bundle == null) {
            var release = releases.findById(id).orElseThrow();
            bundle = new RuntimeBundle(release.getServerCode(), release.getVersion());
            var builder = McpServer.sync(bundle.transport).jsonMapper(sdkMapper).immediateExecution(true)
                    .serverInfo(release.getServerName(), String.valueOf(release.getVersion()))
                    .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                    .requestTimeout(properties.executionTimeout().plusSeconds(2));
            if (release.getInstructions() != null) builder.instructions(release.getInstructions());
            // Our bounded worker already validates the full output. Avoid the SDK's
            // duplicate unbounded validator/cache and diagnostics containing result data.
            builder.jsonSchemaValidator((schema, content) -> ValidationResponse.asValid(null));
            RuntimeBundle target = bundle;
            try {
                for (McpToolRelease source : tools.findAllByReleaseIdOrderBySortOrderAscCodeAsc(id)) {
                    ToolDefinition tool = new ToolDefinition(source.getCode(), source.getInputSchemaJson(), source.getOutputSchemaJson(), source.getScript());
                    bundle.definitions.put(tool.code(), tool);
                    bundle.bytes += tool.input().getBytes(StandardCharsets.UTF_8).length
                            + tool.script().getBytes(StandardCharsets.UTF_8).length
                            + (tool.output() == null ? 0 : tool.output().getBytes(StandardCharsets.UTF_8).length);
                    // Minimal internal descriptor; full original schemas are restored on tools/list.
                    var descriptor = McpSchema.Tool.builder().name(source.getCode()).title(source.getName())
                            .description(source.getDescription())
                            .inputSchema(new McpSchema.JsonSchema("object", Map.of(), List.of(), true, null, null))
                            .outputSchema(Map.of("type", "object")).build();
                    builder.toolCall(descriptor, (context, request) -> call(context, tool, target, request));
                }
                bundle.server = builder.build();
            } catch (RuntimeException exception) {
                if (bundle.server != null) bundle.server.close();
                throw exception;
            }
            cache.put(id, bundle);
            cachedBytes += bundle.bytes;
            while (cache.size() > 32 || cachedBytes > CACHE_BYTES) {
                RuntimeBundle retired = cache.remove(cache.keySet().iterator().next());
                cachedBytes -= retired.bytes;
                retired.retired = true;
                // The newly built bundle is about to be acquired, even if too big to cache.
                if (retired != bundle && retired.users == 0) retired.server.close();
            }
        }
        bundle.users++;
        return bundle;
    }

    private synchronized void release(RuntimeBundle bundle) {
        bundle.users--;
        if (bundle.retired && bundle.users == 0) bundle.server.close();
    }

    @PreDestroy
    public synchronized void close() {
        cache.values().forEach(bundle -> { bundle.retired = true; if (bundle.users == 0) bundle.server.close(); });
        cache.clear(); cachedBytes = 0;
    }

    private static final class DynamicTransport implements McpStatelessServerTransport {
        private McpStatelessServerHandler handler;
        @Override public void setMcpHandler(McpStatelessServerHandler handler) { this.handler = handler; }
        @Override public Mono<Void> closeGracefully() { return Mono.empty(); }
        @Override public List<String> protocolVersions() { return PROTOCOLS; }
    }
    private static final class RuntimeBundle {
        final String code;
        final int version;
        final DynamicTransport transport = new DynamicTransport();
        final Map<String, ToolDefinition> definitions = new LinkedHashMap<>();
        McpStatelessSyncServer server;
        long bytes;
        int users;
        boolean retired;
        RuntimeBundle(String code, int version) { this.code = code; this.version = version; }
    }
    private static final class InvocationState {
        String toolCode;
        String errorCode;
        ResponseStatusException httpError;
    }
    private record ToolDefinition(String code, String input, String output, String script) {}

    public record ProtocolResult(Map<String, Object> body, boolean notification, String method,
                                 String toolCode, String protocolVersion, String auditCode) {
        public static ProtocolResult error(JsonNode id, int code, String message, String method, String protocol) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("jsonrpc", "2.0"); body.put("id", id);
            body.put("error", Map.of("code", code, "message", message));
            return new ProtocolResult(body, false, method, null, protocol, "JSON_RPC_" + code);
        }
    }
}
