package cn.superhuang.data.scalpel.admin.task;

import cn.superhuang.data.scalpel.business.task.service.TaskEngineClient;
import cn.superhuang.data.scalpel.business.task.service.TaskEngineProperties;
import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.TaskCompilationRequest;
import cn.superhuang.data.scalpel.contract.task.TaskDefinition;
import cn.superhuang.data.scalpel.contract.task.TaskType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskEngineClientTests {

    private HttpServer engine;

    @AfterEach
    void tearDown() {
        if (engine != null) engine.stop(0);
    }

    @Test
    void mapsReadTimeoutToGatewayTimeout() throws Exception {
        startEngine(exchange -> {
            try {
                Thread.sleep(500);
                respond(exchange, 200, validResponse());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        TaskEngineClient client = new TaskEngineClient(
                new TaskEngineProperties("token", Duration.ofSeconds(1), Duration.ofMillis(50)),
                new ObjectMapper());

        assertThatThrownBy(() -> client.compile(baseUrl(), emptyRequest()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(exception.getReason()).isEqualTo("Task Engine 响应超时");
                });
    }

    @Test
    void mapsConnectionFailureToBadGateway() {
        TaskEngineClient client = new TaskEngineClient(
                new TaskEngineProperties("token", Duration.ofMillis(100), Duration.ofSeconds(1)),
                new ObjectMapper());

        assertThatThrownBy(() -> client.compile("http://127.0.0.1:1", emptyRequest()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getReason()).isEqualTo("无法连接 Task Engine");
                });
    }

    private void startEngine(EngineHandler handler) throws IOException {
        engine = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        engine.createContext("/", handler::handle);
        engine.start();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + engine.getAddress().getPort();
    }

    private static TaskCompilationRequest emptyRequest() {
        return new TaskCompilationRequest(
                UUID.fromString("a39bb068-bbb9-40b0-8136-1b1adecc3953"),
                new TaskDefinition(
                        TaskType.CANVAS,
                        new CanvasDefinition(1, 1, List.of(), List.of())),
                new MetadataSnapshot(List.of(), List.of()));
    }

    private static String validResponse() {
        return """
                {"requestId":"a39bb068-bbb9-40b0-8136-1b1adecc3953","taskType":"CANVAS","valid":true,
                 "durationMs":1,"sparkApplicationId":"local-test","canvasIssues":[],"nodeResults":[]}
                """;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface EngineHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
