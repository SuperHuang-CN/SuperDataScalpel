package cn.superhuang.data.scalpel.business.compute.client;

import cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineProperties;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionScope;
import cn.superhuang.data.scalpel.contract.execution.DispatcherRuntimeOverviewResponse;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                  "engineId":"80f6dbd2-84a8-47d6-90ef-a9bcd4dd8928",
                  "dispatcherInstanceId":"dispatcher-local",
                  "backendType":"LOCAL_DOCKER",
                  "state":"ACTIVE",
                  "topics":{"commandTopic":"commands.local","runnerEventTopic":"runner.local","adminEventTopic":"admin.events"},
                  "effectiveAdmissionPolicy":{"maxQueuedExecutions":20,"maxConcurrentSubmissions":2,"maxInFlightApplications":2},
                  "lastError":null
                }
                """));
        server.createContext("/api/v1/dispatcher/runtime-overview", exchange -> respond(exchange, """
                {
                  "engineId":"80f6dbd2-84a8-47d6-90ef-a9bcd4dd8928",
                  "dispatcherInstanceId":"dispatcher-local",
                  "backendType":"LOCAL_DOCKER",
                  "version":"0.1.0-SNAPSHOT",
                  "registrationState":"ACTIVE",
                  "dependencies":[],
                  "admissionCapacity":{"maxQueuedExecutions":20,"maxConcurrentSubmissions":2,"maxInFlightApplications":2},
                  "admissionUsage":{"queued":0,"submitting":0,"submitted":0,"running":0,"cancelRequested":0,"inFlight":0},
                  "resourceConfiguration":{"backendType":"LOCAL_DOCKER","image":"eclipse-temurin:21-jdk","containerCpuLimit":"2","containerMemoryLimit":"4g","runnerJvmHeap":"-Xmx3g","queue":null,"namespace":null,"driverMemory":null,"executorMemory":null,"executorCores":null,"executorInstances":null},
                  "collectedAt":"2026-08-26T00:00:00Z"
                }
                """));
        server.createContext("/api/v1/task-executions", exchange -> respond(exchange, """
                {"content":[],"totalElements":0,"totalPages":0,"page":0,"size":20}
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
                engineId,
                new DispatcherTopics("commands.local", "runner.local", "admin.events"),
                new DispatcherAdmissionPolicy(20, 2, 2)
        , cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourcePolicy.defaultsFor(cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType.LOCAL_DOCKER)));
        assertEquals(DispatcherRegistrationState.ACTIVE, response.state());
        assertEquals(engineId, response.engineId());
        assertFalse(requestBody.get().contains("configRevision"));
        assertFalse(requestBody.get().contains("protocolVersion"));
        assertTrue(requestBody.get().contains("\"commandTopic\":\"commands.local\""));

        DispatcherRuntimeOverviewResponse overview = client.runtimeOverview(baseUrl, "secret-token");
        assertEquals("ACTIVE", overview.registrationState());
        assertEquals("2", overview.resourceConfiguration().containerCpuLimit());
        assertTrue(client.executions(baseUrl, "secret-token", DispatcherExecutionScope.QUEUED, 0, 20).content().isEmpty());
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
