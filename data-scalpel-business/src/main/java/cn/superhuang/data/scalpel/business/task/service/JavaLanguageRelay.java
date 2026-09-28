package cn.superhuang.data.scalpel.business.task.service;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded transport only: no Java parsing, document storage or per-message database access. */
@Component
public class JavaLanguageRelay extends TextWebSocketHandler implements SubProtocolCapable {
    public static final String TICKET_ATTRIBUTE = JavaLanguageRelay.class.getName() + ".ticket";
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final TaskEngineProperties properties;
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService expiry = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("java-language-relay-expiry").factory());
    public JavaLanguageRelay(TaskEngineProperties properties) { this.properties = properties; }
    @Override public List<String> getSubProtocols() { return List.of("datascalpel-java"); }

    @Override public void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(MAX_BYTES);
        var ticket = (JavaLanguageTicketService.Ticket) session.getAttributes().get(TICKET_ATTRIBUTE);
        Connection connection = new Connection(new ConcurrentWebSocketSessionDecorator(session, 5000, MAX_BYTES * 2));
        connections.put(session.getId(), connection);
        connection.socket = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + properties.token()).buildAsync(ticket.upstream(), connection);
        connection.socket.whenComplete((socket, error) -> { if (error != null) connection.close(CloseStatus.SERVER_ERROR); });
        // Periodic reconnect re-checks the login and task permission via the normal REST endpoint.
        long lifetime = Math.max(0, Math.min(900, Duration.between(java.time.Instant.now(), ticket.loginExpiresAt()).toSeconds()));
        synchronized (connection) {
            if (!connection.closed) connection.expiration = expiry.schedule(
                    () -> connection.close(new CloseStatus(4001, "Reauthenticate language connection")), lifetime, TimeUnit.SECONDS);
        }
    }

    @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Connection connection = connections.get(session.getId());
        if (connection != null) connection.forward(message.getPayload());
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Connection connection = connections.remove(session.getId());
        if (connection != null) connection.close(CloseStatus.NORMAL);
    }
    @Override public void handleTransportError(WebSocketSession session, Throwable error) { afterConnectionClosed(session, CloseStatus.SERVER_ERROR); }
    @PreDestroy public void shutdown() { connections.values().forEach(c -> c.close(CloseStatus.GOING_AWAY)); expiry.shutdownNow(); client.close(); }

    private final class Connection implements WebSocket.Listener {
        private final WebSocketSession browser;
        private final AtomicInteger pending = new AtomicInteger();
        private final StringBuilder partial = new StringBuilder();
        private CompletableFuture<WebSocket> socket;
        private CompletableFuture<?> sends = CompletableFuture.completedFuture(null);
        private ScheduledFuture<?> expiration;
        private boolean closed;
        Connection(WebSocketSession browser) { this.browser = browser; }

        synchronized void forward(String text) {
            if (closed) return;
            if (pending.incrementAndGet() > 16) { pending.decrementAndGet(); close(CloseStatus.POLICY_VIOLATION); return; }
            sends = sends.thenCompose(ignored -> socket).thenCompose(ws -> ws.sendText(text, true))
                    .orTimeout(15, TimeUnit.SECONDS).whenComplete((unused, error) -> {
                        pending.decrementAndGet(); if (error != null) close(CloseStatus.SERVER_ERROR);
                    });
        }

        @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            if (partial.length() + data.length() > MAX_BYTES) { close(CloseStatus.TOO_BIG_TO_PROCESS); return null; }
            partial.append(data);
            if (last) {
                try { browser.sendMessage(new TextMessage(partial.toString())); partial.setLength(0); }
                catch (Exception ex) { close(CloseStatus.SERVER_ERROR); return null; }
            }
            ws.request(1); return CompletableFuture.completedFuture(null);
        }
        @Override public void onOpen(WebSocket ws) { ws.request(1); }
        @Override public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
            close(new CloseStatus(code == 1013 ? 1013 : 1011, code == 1013 ? "Java language unavailable or busy" : "Java language disconnected")); return null;
        }
        @Override public void onError(WebSocket ws, Throwable error) { close(CloseStatus.SERVER_ERROR); }
        synchronized void close(CloseStatus status) {
            if (closed) return;
            closed = true;
            if (expiration != null) expiration.cancel(false);
            if (socket != null) socket.thenAccept(WebSocket::abort);
            try { browser.close(status); } catch (Exception ignored) { /* Peer already disconnected. */ }
            connections.remove(browser.getId(), this);
        }
    }
}
