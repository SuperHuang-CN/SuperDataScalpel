package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.web.response.JavaLanguageTicketResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/** Short-lived handshake credentials only. Workspace/process ownership stays in TaskEngine. */
@Service
public class JavaLanguageTicketService {
    public static final String PATH = "/api/v1/java-language/connection";
    public record Ticket(URI upstream, Instant expiresAt, Instant loginExpiresAt) {}
    private final SparkJarTaskDefinitionService definitions;
    private final TaskCompilationService compilation;
    private final Map<String, Ticket> tickets = new HashMap<>();

    public JavaLanguageTicketService(SparkJarTaskDefinitionService definitions, TaskCompilationService compilation) {
        this.definitions = definitions; this.compilation = compilation;
    }

    public JavaLanguageTicketResponse create(UUID taskId, UUID editorId, String owner, Instant loginExpiresAt) {
        if (loginExpiresAt == null || !loginExpiresAt.isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已过期，请重新登录");
        definitions.getOnlineSource(taskId); // Validate task existence and Spark JAR mode; no mutation.
        URI engine = URI.create(compilation.taskEngineBaseUrl());
        int port = (engine.getPort() == -1 ? ("https".equals(engine.getScheme()) ? 443 : 80) : engine.getPort()) + 100;
        String key;
        try { key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                (owner + "\n" + taskId + "\n" + editorId).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
        URI upstream;
        try { upstream = new URI("https".equals(engine.getScheme()) ? "wss" : "ws", null, engine.getHost(), port, "/language/" + key, null, null); }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TaskEngine 语言服务地址无效"); }
        Instant expiry = Instant.now().plusSeconds(30);
        String token = "ticket." + UUID.randomUUID();
        synchronized (tickets) {
            tickets.values().removeIf(ticket -> !ticket.expiresAt().isAfter(Instant.now()));
            if (tickets.size() >= 256) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "编辑连接申请繁忙，请稍后重试");
            tickets.put(token, new Ticket(upstream, expiry, loginExpiresAt));
        }
        return new JavaLanguageTicketResponse(PATH, token, expiry);
    }

    public Ticket consume(String token) {
        synchronized (tickets) {
            Ticket ticket = tickets.remove(token);
            if (ticket == null || !ticket.expiresAt().isAfter(Instant.now()) || !ticket.loginExpiresAt().isAfter(Instant.now()))
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "代码提示连接凭证已失效，请重新连接");
            return ticket;
        }
    }
}
