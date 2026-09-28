package cn.superhuang.datascalpel.taskengine.language;

import cn.superhuang.datascalpel.taskengine.config.EngineConfiguration;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Internal-only WebSocket listener. Existing JDK HTTP compilation routes are unchanged. */
public final class JavaLanguageServer extends WebSocketServer implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(JavaLanguageServer.class);
    private final LanguageServiceConfiguration configuration;
    private final JavaLanguageIndexCache indexes;
    private final byte[] authorization;
    private final Map<String, JavaLanguageWorkspace> workspaces = new ConcurrentHashMap<>();
    private final Map<WebSocket, JavaLanguageWorkspace> connections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService maintenance = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("java-language-maintenance").factory());

    public JavaLanguageServer(EngineConfiguration engine, LanguageServiceConfiguration language) {
        super(new InetSocketAddress(engine.host(), language.port()), 2,
                List.of(new Draft_6455(List.of(), LspFrames.MAX_BYTES)));
        configuration = language;
        indexes = new JavaLanguageIndexCache(language);
        authorization = ("Bearer " + engine.authToken()).getBytes(StandardCharsets.UTF_8);
        setConnectionLostTimeout(30);
        setReuseAddr(true);
        maintenance.scheduleWithFixedDelay(this::reap, 5, 5, TimeUnit.SECONDS);
    }

    @Override public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(
            WebSocket connection, org.java_websocket.drafts.Draft draft, ClientHandshake request) throws InvalidDataException {
        if (!MessageDigest.isEqual(authorization, request.getFieldValue("Authorization").getBytes(StandardCharsets.UTF_8))
                || !request.getResourceDescriptor().matches("/language/[a-f0-9]{64}"))
            throw new InvalidDataException(1008, "Unauthorized language connection");
        return super.onWebsocketHandshakeReceivedAsServer(connection, draft, request);
    }

    @Override public synchronized void onOpen(WebSocket connection, ClientHandshake handshake) {
        if (configuration.home() == null) { connection.close(1013, "Java language runtime is not configured"); return; }
        String key = handshake.getResourceDescriptor();
        JavaLanguageWorkspace workspace = workspaces.get(key);
        if (workspace != null && workspace.expired(Instant.now())) {
            workspaces.remove(key); workspace.close(); workspace = null;
        }
        try {
            if (workspace == null) {
                if (workspaces.size() >= configuration.maxSessions()) { connection.close(1013, "Java language capacity reached"); return; }
                workspace = new JavaLanguageWorkspace(configuration, indexes::copyToWorkspace);
                workspaces.put(key, workspace);
            }
            if (!workspace.attach(value -> {
                if (!connection.isOpen()) return;
                // Bound queued output as well as each frame: slow clients must reconnect.
                if (connection instanceof org.java_websocket.WebSocketImpl socket && socket.outQueue.size() >= 8) {
                    connection.close(1013, "Language client is too slow"); return;
                }
                connection.send(value);
            })) { connection.close(1008, "Editing session already connected"); return; }
            connections.put(connection, workspace);
        } catch (Exception ex) { connection.close(1011, "Cannot create Java workspace"); }
    }

    @Override public void onMessage(WebSocket connection, String value) {
        JavaLanguageWorkspace workspace = connections.get(connection);
        if (workspace == null) { connection.close(1008); return; }
        if (value.length() > LspFrames.MAX_BYTES) { connection.close(1009); return; }
        try { workspace.accept(value); }
        catch (RejectedExecutionException ex) { connection.close(1013, "Language request queue is full"); }
    }

    @Override public void onMessage(WebSocket connection, ByteBuffer bytes) { connection.close(1003, "Text messages required"); }
    @Override public void onClose(WebSocket connection, int code, String reason, boolean remote) {
        JavaLanguageWorkspace workspace = connections.remove(connection);
        if (workspace != null) workspace.detach();
    }
    @Override public void onError(WebSocket connection, Exception exception) {
        LOG.warn("Java language transport failure: {}", exception.getClass().getSimpleName());
        if (connection != null) connection.close(1011);
    }
    @Override public void onStart() {
        LOG.info("Task Engine Java language listener ready on port {}", getPort());
        indexes.start();
    }

    private synchronized void reap() {
        Instant now = Instant.now();
        workspaces.entrySet().removeIf(entry -> {
            if (!entry.getValue().expired(now)) return false;
            connections.forEach((socket, workspace) -> { if (workspace == entry.getValue()) socket.close(1011, "Language workspace ended"); });
            entry.getValue().close();
            return true;
        });
    }

    @Override public void close() {
        maintenance.shutdownNow();
        indexes.close();
        try { stop(1000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        workspaces.values().forEach(JavaLanguageWorkspace::close);
        workspaces.clear(); connections.clear();
    }
}
