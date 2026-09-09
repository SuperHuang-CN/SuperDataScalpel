package cn.superhuang.data.scalpel.business.mcp.web.resource;

import cn.superhuang.data.scalpel.business.mcp.config.McpPlatformProperties;
import cn.superhuang.data.scalpel.business.mcp.domain.McpInvocationStatus;
import cn.superhuang.data.scalpel.business.mcp.security.McpInvocationAuthenticationService.AuthenticatedServer;
import cn.superhuang.data.scalpel.business.mcp.security.McpTokenAuthenticationFilter;
import cn.superhuang.data.scalpel.business.mcp.service.McpInvocationLogService;
import cn.superhuang.data.scalpel.business.mcp.service.McpPayloadService;
import cn.superhuang.data.scalpel.business.mcp.service.McpProtocolService;
import cn.superhuang.data.scalpel.business.mcp.service.McpScriptExecutionService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@RestController
public class McpProtocolResource {
    private static final Logger LOG = LoggerFactory.getLogger(McpProtocolResource.class);
    private final McpProtocolService protocol;
    private final McpInvocationLogService logs;
    private final McpPlatformProperties properties;
    private final McpPayloadService payloads;
    private final ObjectMapper mapper;

    public McpProtocolResource(McpProtocolService protocol, McpInvocationLogService logs,
                               McpPlatformProperties properties, McpPayloadService payloads, ObjectMapper mapper) {
        this.protocol = protocol; this.logs = logs; this.properties = properties;
        this.payloads = payloads; this.mapper = mapper;
    }

    @PostMapping(path = "/mcp/{serverCode}", consumes = {MediaType.APPLICATION_JSON_VALUE, "application/*+json"})
    public ResponseEntity<String> invoke(HttpServletRequest request) throws IOException {
        AuthenticatedServer auth = (AuthenticatedServer) request.getAttribute(McpTokenAuthenticationFilter.AUTHENTICATED_SERVER_ATTRIBUTE);
        if (auth == null) throw new org.springframework.security.authentication.BadCredentialsException("Invalid MCP access token");
        if (!auth.callable()) throw new ResponseStatusException(HttpStatus.CONFLICT, "MCP Server 尚未发布启用");
        Instant startedAt = Instant.now();
        long started = System.nanoTime();
        long requestBytes = 0;
        JsonNode json = null;
        String requestId = null;
        String version = request.getHeader("MCP-Protocol-Version");
        if (version == null) version = "2025-03-26";
        McpProtocolService.ProtocolResult result;
        try {
            if (request.getContentLengthLong() > properties.maxPayloadSize().toBytes()) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "MCP 请求超过大小限制");
            }
            String body = payloads.read(request.getInputStream());
            requestBytes = body.getBytes(StandardCharsets.UTF_8).length;
            try {
                json = mapper.readTree(body);
            } catch (RuntimeException exception) {
                // Only JSON parsing failures map to -32700.
            }
            if (json == null) {
                result = McpProtocolService.ProtocolResult.error(null, -32700, "Parse error", null, version);
            } else {
                if (!"initialize".equals(json.path("method").asText()) && !McpProtocolService.PROTOCOLS.contains(version)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持该 MCP-Protocol-Version");
                }
                JsonNode id = json.get("id");
                requestId = id == null ? null : id.isTextual() ? id.asText() : id.toString();
                result = protocol.handle(auth, json, version);
            }
            if (result.notification()) {
                record(auth, result, requestId, startedAt, started, requestBytes, 0, request, McpInvocationStatus.SUCCESS);
                return ResponseEntity.accepted().build();
            }
            String response;
            try {
                response = payloads.write(result.body());
            } catch (McpScriptExecutionService.McpToolExecutionException exception) {
                result = McpProtocolService.ProtocolResult.error(json == null ? null : json.get("id"),
                        -32603, "Response too large", result.method(), version);
                response = payloads.write(result.body());
            }
            record(auth, result, requestId, startedAt, started, requestBytes, response.getBytes(StandardCharsets.UTF_8).length,
                    request, result.auditCode() == null ? McpInvocationStatus.SUCCESS : McpInvocationStatus.ERROR);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
        } catch (ResponseStatusException exception) {
            result = new McpProtocolService.ProtocolResult(java.util.Map.of(), false,
                    json == null ? null : json.path("method").asText(null), null, version, "HTTP_" + exception.getStatusCode().value());
            record(auth, result, requestId, startedAt, started, requestBytes, 0, request, McpInvocationStatus.REJECTED);
            // Transport errors use the shared ProblemDetail handler, including 413 and 429.
            throw exception;
        }
    }

    private void record(AuthenticatedServer auth, McpProtocolService.ProtocolResult result, String requestId,
                        Instant startedAt, long startNanos, long requestBytes, long responseBytes,
                        HttpServletRequest request, McpInvocationStatus status) {
        try {
            logs.record(auth.serverId(), auth.releaseId(), auth.serverCode(), auth.releaseVersion(),
                    result.toolCode(), result.method() == null ? "unknown" : result.method(), result.protocolVersion(),
                    requestId, startedAt, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos), status,
                    requestBytes, responseBytes, request.getRemoteAddr(), request.getHeader("User-Agent"),
                    auth.accessTokenId(), auth.accessTokenName(), auth.tokenRevision(), result.auditCode());
        } catch (RuntimeException exception) {
            // Persistence diagnostics must not dump an invocation or arbitrary JDBC bind values.
            LOG.warn("MCP audit write failed server={} exceptionType={}", auth.serverId(), exception.getClass().getName());
        }
    }
}
