package cn.superhuang.datascalpel.taskengine.language;

import cn.superhuang.datascalpel.taskengine.config.EngineConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class JavaLanguageServerTest {
    @TempDir Path work;

    @Test void authenticationCapacityDuplicateAndRetentionAreEnforced() throws Exception {
        var engine = new EngineConfiguration("127.0.0.1", 0, "test-internal-only", 2, 1024,
                1, Duration.ofSeconds(1), Duration.ofSeconds(1), Map.of());
        var configuration = new LanguageServiceConfiguration(work, work.resolve("sessions"), 0, 1, 512, Duration.ZERO);
        try (var server = new JavaLanguageServer(engine, configuration); var client = HttpClient.newHttpClient()) {
            server.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (server.getPort() == 0 && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(server.getPort() > 0);
            URI first = URI.create("ws://127.0.0.1:" + server.getPort() + "/language/" + "a".repeat(64));
            assertThrows(Exception.class, () -> client.newWebSocketBuilder().buildAsync(first, new Listener()).get(5, TimeUnit.SECONDS));
            var a = new Listener(); var wsA = connect(client, first, a);
            var duplicate = new Listener(); connect(client, first, duplicate);
            assertEquals(1008, duplicate.closed.get(5, TimeUnit.SECONDS));
            var full = new Listener();
            connect(client, URI.create(first.toString().replace("a".repeat(64), "b".repeat(64))), full);
            assertEquals(1013, full.closed.get(5, TimeUnit.SECONDS));
            wsA.sendClose(1000, "test done").get(5, TimeUnit.SECONDS);
            a.closed.get(5, TimeUnit.SECONDS);
            // Reopening the same detached key reclaims its expired workspace synchronously.
            var resumed = new Listener(); var wsB = connect(client, first, resumed);
            wsB.sendText("{}", true).get(5, TimeUnit.SECONDS);
            assertTrue(resumed.message.get(5, TimeUnit.SECONDS).contains("JAVA_LANGUAGE_PROTOCOL_FAILED"));
            wsB.abort();
        }
    }

    private WebSocket connect(HttpClient client, URI uri, Listener listener) throws Exception {
        return client.newWebSocketBuilder().header("Authorization", "Bearer test-internal-only")
                .buildAsync(uri, listener).get(5, TimeUnit.SECONDS);
    }
    private static final class Listener implements WebSocket.Listener {
        final CompletableFuture<Integer> closed = new CompletableFuture<>();
        final CompletableFuture<String> message = new CompletableFuture<>();
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public java.util.concurrent.CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            message.complete(data.toString()); socket.request(1); return null;
        }
        @Override public java.util.concurrent.CompletionStage<?> onClose(WebSocket socket, int code, String reason) {
            closed.complete(code); return null;
        }
        @Override public void onError(WebSocket socket, Throwable error) { closed.completeExceptionally(error); }
    }
}
