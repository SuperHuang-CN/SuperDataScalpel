package cn.superhuang.data.scalpel.business.compute.client;

import cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComputeEngineDispatcherClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/v1/dispatcher/info", exchange -> respond(exchange, """
                {
                  "protocolVersion":1,
                  "dispatcherInstanceId":"dispatcher-local",
                  "backendType":"LOCAL_DOCKER",
                  "version":"0.1.0-SNAPSHOT",
                  "capabilities":{
                    "cancellation":true,
                    "logCollection":true,
                    "restartReconciliation":true,
                    "streaming":true,
                    "durableCheckpoint":true
                  },
                  "dependencies":[]
                }
                """));
        server.createContext("/api/v1/dispatcher/registration/actions/activate", exchange -> respond(exchange, """
                {
                  "protocolVersion":1,
                  "engineId":"80f6dbd2-84a8-47d6-90ef-a9bcd4dd8928",
                  "dispatcherInstanceId":"dispatcher-local",
                  "backendType":"LOCAL_DOCKER",
                  "configRevision":3,
                  "state":"ACTIVE",
                  "topics":{"commandTopic":"commands.local","runnerEventTopic":"runner.local","adminEventTopic":"admin.events"},
                  "effectiveAdmissionPolicy":{"maxQueuedExecutions":20,"maxConcurrentSubmissions":2,"maxInFlightApplications":2},
                  "lastError":null
                }
                """));
        server.start();
        baseUrl = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsBearerTokenAndTypedRegistrationContract() {
        ComputeEngineDispatcherClient client = new ComputeEngineDispatcherClient(new ComputeEngineProperties(
                null, Duration.ofSeconds(1), Duration.ofSeconds(2)
        ));

        DispatcherInfoResponse info = client.info(baseUrl, "secret-token");
        assertEquals("dispatcher-local", info.dispatcherInstanceId());
        assertEquals(ComputeBackendType.LOCAL_DOCKER, info.backendType());
        assertTrue(info.capabilities().streaming());
        assertTrue(info.capabilities().durableCheckpoint());
        assertEquals("Bearer secret-token", authorization.get());

        UUID engineId = UUID.fromString("80f6dbd2-84a8-47d6-90ef-a9bcd4dd8928");
        DispatcherRegistrationResponse response = client.activate(baseUrl, "secret-token", new DispatcherRegistrationRequest(
                1, engineId, 3,
                new DispatcherTopics("commands.local", "runner.local", "admin.events"),
                new DispatcherAdmissionPolicy(20, 2, 2)
        ));
        assertEquals(DispatcherRegistrationState.ACTIVE, response.state());
        assertEquals(engineId, response.engineId());
        assertTrue(requestBody.get().contains("\"configRevision\":3"));
        assertTrue(requestBody.get().contains("\"commandTopic\":\"commands.local\""));
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
