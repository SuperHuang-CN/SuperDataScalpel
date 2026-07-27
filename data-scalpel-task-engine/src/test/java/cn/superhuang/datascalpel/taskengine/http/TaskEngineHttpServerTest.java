package cn.superhuang.datascalpel.taskengine.http;

import cn.superhuang.datascalpel.taskengine.compiler.TaskCompilationService;
import cn.superhuang.datascalpel.taskengine.config.EngineConfiguration;
import cn.superhuang.datascalpel.taskengine.contract.ProblemResponse;
import cn.superhuang.data.scalpel.contract.task.TaskCompilationResponse;
import cn.superhuang.datascalpel.taskengine.spark.SparkRuntime;
import cn.superhuang.datascalpel.taskengine.spark.SparkCompilationScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskEngineHttpServerTest {
    private static final String TOKEN = "http-test-token";

    private final ObjectMapper objectMapper = JsonSupport.strictObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private SparkRuntime sparkRuntime;
    private TaskCompilationService compilationService;
    private TaskEngineHttpServer httpServer;
    private URI baseUri;

    @BeforeAll
    void startServer() throws Exception {
        EngineConfiguration configuration = configuration();
        sparkRuntime = new SparkRuntime(configuration);
        compilationService = new TaskCompilationService(configuration, sparkRuntime);
        httpServer = new TaskEngineHttpServer(configuration, sparkRuntime, compilationService);
        httpServer.start();
        baseUri = URI.create("http://127.0.0.1:" + httpServer.port());
    }

    @AfterAll
    void stopServer() {
        if (httpServer != null) httpServer.close();
        if (compilationService != null) compilationService.close();
        if (sparkRuntime != null) sparkRuntime.close();
    }

    @Test
    void exposesUnauthenticatedHealthChecks() throws Exception {
        HttpResponse<String> live = send("/health/live", "GET", null, null);
        HttpResponse<String> ready = send("/health/ready", "GET", null, null);

        assertEquals(200, live.statusCode());
        assertEquals(200, ready.statusCode());
        assertEquals("UP", objectMapper.readTree(ready.body()).get("status").asText());
        assertEquals("4.1.1", objectMapper.readTree(ready.body()).get("sparkVersion").asText());
    }

    @Test
    void enforcesBearerAuthenticationAndJsonMediaType() throws Exception {
        String body = Files.readString(examplePath());
        HttpResponse<String> unauthorized = send(
                "/api/v1/task-compilations", "POST", "application/json", body);
        assertProblem(unauthorized, 401, "UNAUTHORIZED");

        HttpResponse<String> unsupported = sendAuthenticated(
                "/api/v1/task-compilations", "POST", "text/plain", body);
        assertProblem(unsupported, 415, "UNSUPPORTED_MEDIA_TYPE");

        HttpResponse<String> jsonPrefix = sendAuthenticated(
                "/api/v1/task-compilations", "POST", "application/json-patch+json", body);
        assertProblem(jsonPrefix, 415, "UNSUPPORTED_MEDIA_TYPE");

        HttpResponse<String> unknownWithoutToken = send("/api/v1/unknown", "GET", null, null);
        assertProblem(unknownWithoutToken, 401, "UNAUTHORIZED");
        HttpResponse<String> unknownWithToken = sendAuthenticated("/api/v1/unknown", "GET", null, null);
        assertProblem(unknownWithToken, 404, "NOT_FOUND");
    }

    @Test
    void compilesValidCanvasAndRejectsUnsafeJson() throws Exception {
        String body = Files.readString(examplePath());
        HttpResponse<String> response = sendAuthenticated(
                "/api/v1/task-compilations", "POST", "application/json", body);

        assertEquals(200, response.statusCode());
        TaskCompilationResponse compilation = objectMapper.readValue(response.body(), TaskCompilationResponse.class);
        assertTrue(compilation.valid());
        assertEquals(4, compilation.nodeResults().size());

        String unknownField = body.replace(
                "\"schemaVersion\": 1",
                "\"schemaVersion\": 1, \"x6Shape\": \"rect\"");
        HttpResponse<String> invalid = sendAuthenticated(
                "/api/v1/task-compilations", "POST", "application/json", unknownField);
        assertProblem(invalid, 400, "INVALID_JSON");

        HttpResponse<String> unknownTask = sendAuthenticated(
                "/api/v1/task-compilations",
                "POST",
                "application/json",
                body.replaceFirst("\"type\": \"CANVAS\"", "\"type\": \"UNKNOWN\"")
        );
        assertProblem(unknownTask, 400, "UNKNOWN_TASK_TYPE");

        HttpResponse<String> unknownNode = sendAuthenticated(
                "/api/v1/task-compilations",
                "POST",
                "application/json",
                body.replaceFirst("JDBC_INPUT", "UNKNOWN_INPUT")
        );
        assertProblem(unknownNode, 400, "UNSUPPORTED_NODE_TYPE");
    }

    @Test
    void returnsBusinessValidationProblemsAsACompilationResult() throws Exception {
        String body = Files.readString(examplePath())
                .replace("\"outputTableName\": \"order_customer\"", "\"outputTableName\": \"\"");
        HttpResponse<String> response = sendAuthenticated(
                "/api/v1/task-compilations", "POST", "application/json", body);

        assertEquals(200, response.statusCode());
        TaskCompilationResponse compilation = objectMapper.readValue(response.body(), TaskCompilationResponse.class);
        assertFalse(compilation.valid());
        assertTrue(compilation.nodeResults().get(2).issues().stream()
                .anyMatch(issue -> issue.code().equals("REQUIRED_CONFIGURATION")));
    }

    @Test
    void noLongerExposesTaskExecutionApi() throws Exception {
        HttpResponse<String> response = sendAuthenticated(
                "/api/v1/task-executions", "POST", "application/json", "{}");

        assertProblem(response, 404, "NOT_FOUND");
    }

    @Test
    void createsIsolatedChildSparkSessions() {
        String key = "datascalpel.test.session.key";
        try (SparkCompilationScope first = sparkRuntime.openCompilation(UUID.randomUUID())) {
            first.session().conf().set(key, "first");
            assertEquals("first", first.session().conf().get(key));
        }
        try (SparkCompilationScope second = sparkRuntime.openCompilation(UUID.randomUUID())) {
            assertEquals("missing", second.session().conf().get(key, "missing"));
        }
    }

    private HttpResponse<String> sendAuthenticated(
            String path,
            String method,
            String contentType,
            String body
    ) throws Exception {
        HttpRequest.Builder builder = request(path, method, contentType, body)
                .header("Authorization", "Bearer " + TOKEN);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String path, String method, String contentType, String body) throws Exception {
        return httpClient.send(request(path, method, contentType, body).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path, String method, String contentType, String body) {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(Duration.ofSeconds(30))
                .method(method, publisher);
        if (contentType != null) builder.header("Content-Type", contentType);
        return builder;
    }

    private void assertProblem(HttpResponse<String> response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("")
                .startsWith("application/problem+json"));
        ProblemResponse problem = objectMapper.readValue(response.body(), ProblemResponse.class);
        assertEquals(code, problem.code());
    }

    private static EngineConfiguration configuration() {
        return new EngineConfiguration(
                "127.0.0.1",
                0,
                TOKEN,
                4,
                10 * 1024 * 1024,
                2,
                Duration.ofSeconds(1),
                Duration.ofSeconds(30),
                Map.of(
                        "spark.master", "local[1]",
                        "spark.app.name", "task-engine-http-test",
                        "spark.ui.enabled", "false",
                        "spark.driver.host", "127.0.0.1",
                        "spark.driver.bindAddress", "127.0.0.1",
                        "spark.sql.caseSensitive", "true",
                        "spark.sql.ansi.enabled", "true",
                        "spark.sql.session.timeZone", "UTC"
                )
        );
    }

    private static Path examplePath() {
        return Path.of("examples/valid-canvas-compilation.json");
    }
}
