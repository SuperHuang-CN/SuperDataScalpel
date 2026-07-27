package cn.superhuang.datascalpel.taskengine.http;

import cn.superhuang.datascalpel.taskengine.compiler.TaskCompilationService;
import cn.superhuang.datascalpel.taskengine.config.EngineConfiguration;
import cn.superhuang.datascalpel.taskengine.contract.CancellationResponse;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.datascalpel.taskengine.contract.HealthResponse;
import cn.superhuang.datascalpel.taskengine.contract.ProblemResponse;
import cn.superhuang.data.scalpel.contract.task.TaskCompilationRequest;
import cn.superhuang.data.scalpel.contract.task.TaskCompilationResponse;
import cn.superhuang.datascalpel.taskengine.spark.SparkRuntime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class TaskEngineHttpServer implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TaskEngineHttpServer.class);
    private static final String COMPILATIONS_PATH = "/api/v1/task-compilations";
    private static final String JSON = "application/json; charset=utf-8";
    private static final String PROBLEM_JSON = "application/problem+json; charset=utf-8";

    private final EngineConfiguration configuration;
    private final SparkRuntime sparkRuntime;
    private final TaskCompilationService compilationService;
    private final ObjectMapper objectMapper;
    private final HttpServer server;
    private final ExecutorService httpExecutor;
    private final byte[] expectedToken;
    private final AtomicBoolean closed = new AtomicBoolean();

    public TaskEngineHttpServer(
            EngineConfiguration configuration,
            SparkRuntime sparkRuntime,
            TaskCompilationService compilationService
    ) throws IOException {
        this.configuration = configuration;
        this.sparkRuntime = sparkRuntime;
        this.compilationService = compilationService;
        this.objectMapper = JsonSupport.strictObjectMapper();
        this.expectedToken = configuration.authToken().getBytes(StandardCharsets.UTF_8);
        this.server = HttpServer.create(new InetSocketAddress(configuration.host(), configuration.port()), 128);
        this.httpExecutor = Executors.newFixedThreadPool(
                configuration.httpExecutorThreads(), namedThreads("task-engine-http-"));
        server.setExecutor(httpExecutor);
        server.createContext("/health/live", this::handleLive);
        server.createContext("/health/ready", this::handleReady);
        server.createContext(COMPILATIONS_PATH, this::handleCompilations);
        server.createContext("/api/v1", this::handleApiNotFound);
    }

    public void start() {
        server.start();
        log.info("Task Engine HTTP server listening on {}:{}", configuration.host(), port());
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        server.stop(0);
        httpExecutor.shutdownNow();
        try {
            if (!httpExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("Task Engine HTTP executor did not terminate within 10 seconds");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleLive(HttpExchange exchange) throws IOException {
        execute(exchange, () -> {
            requireExactPath(exchange, "/health/live");
            requireMethod(exchange, "GET");
            sendJson(exchange, 200, HealthResponse.live());
        });
    }

    private void handleReady(HttpExchange exchange) throws IOException {
        execute(exchange, () -> {
            requireExactPath(exchange, "/health/ready");
            requireMethod(exchange, "GET");
            boolean ready = sparkRuntime.ready();
            sendJson(exchange, ready ? 200 : 503, ready ? sparkRuntime.health()
                    : new HealthResponse("DOWN", sparkRuntime.health().sparkVersion(),
                    sparkRuntime.health().sparkApplicationId(), sparkRuntime.health().master()));
        });
    }

    private void handleCompilations(HttpExchange exchange) throws IOException {
        execute(exchange, () -> {
            requireAuthentication(exchange);
            String path = exchange.getRequestURI().getPath();
            if (COMPILATIONS_PATH.equals(path)) {
                requireMethod(exchange, "POST");
                requireJson(exchange);
                TaskCompilationRequest request = readRequest(exchange, TaskCompilationRequest.class);
                TaskCompilationResponse response = compilationService.compile(request);
                int issueCount = response.canvasIssues().size()
                        + response.nodeResults().stream().mapToInt(result -> result.issues().size()).sum();
                log.info("Compiled task request {}: valid={}, nodes={}, issues={}, durationMs={}",
                        response.requestId(), response.valid(), response.nodeResults().size(),
                        issueCount, response.durationMs());
                sendJson(exchange, 200, response);
                return;
            }
            String suffix = path.substring(COMPILATIONS_PATH.length());
            String[] segments = suffix.split("/");
            if (segments.length == 4 && segments[0].isEmpty()
                    && "actions".equals(segments[2]) && "cancel".equals(segments[3])) {
                requireMethod(exchange, "POST");
                UUID requestId;
                try {
                    requestId = UUID.fromString(segments[1]);
                } catch (IllegalArgumentException exception) {
                    throw problem(400, "INVALID_REQUEST", "请求无效", "requestId 必须是 UUID");
                }
                if (!compilationService.cancel(requestId)) {
                    throw problem(404, "COMPILATION_NOT_FOUND", "编译请求不存在", "没有找到活动的编译请求");
                }
                sendJson(exchange, 202, new CancellationResponse(requestId, "CANCEL_REQUESTED"));
                return;
            }
            throw problem(404, "NOT_FOUND", "接口不存在", "请求路径不存在");
        });
    }

    private void handleApiNotFound(HttpExchange exchange) throws IOException {
        execute(exchange, () -> {
            requireAuthentication(exchange);
            throw problem(404, "NOT_FOUND", "接口不存在", "请求路径不存在");
        });
    }

    private void execute(HttpExchange exchange, ExchangeAction action) throws IOException {
        try {
            action.execute();
        } catch (TaskEngineException exception) {
            sendProblem(exchange, exception);
        } catch (JsonProcessingException exception) {
            sendProblem(exchange, invalidJson(exception));
        } catch (Exception exception) {
            log.error("Unexpected HTTP request failure for {} {}",
                    exchange.getRequestMethod(), exchange.getRequestURI(), exception);
            sendProblem(exchange, problem(500, "INTERNAL_ERROR", "任务引擎内部错误", "请求处理失败"));
        } finally {
            exchange.close();
        }
    }

    private <T> T readRequest(HttpExchange exchange, Class<T> type) throws IOException {
        byte[] body = readLimited(exchange.getRequestBody(), configuration.maxRequestBytes());
        if (body.length == 0) throw problem(400, "INVALID_JSON", "JSON 请求无效", "请求体不能为空");
        return objectMapper.readValue(body, type);
    }

    private void requireAuthentication(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw unauthorized();
        }
        byte[] actual = authorization.substring("Bearer ".length()).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedToken, actual)) throw unauthorized();
    }

    private static TaskEngineException unauthorized() {
        return problem(401, "UNAUTHORIZED", "未授权", "访问令牌无效");
    }

    private static void requireJson(HttpExchange exchange) {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        String mediaType = contentType == null ? "" : contentType.split(";", 2)[0].trim();
        if (!mediaType.equalsIgnoreCase("application/json")) {
            throw problem(415, "UNSUPPORTED_MEDIA_TYPE", "媒体类型不受支持", "Content-Type 必须是 application/json");
        }
    }

    private static void requireMethod(HttpExchange exchange, String method) {
        if (!method.equals(exchange.getRequestMethod())) {
            throw problem(405, "METHOD_NOT_ALLOWED", "请求方法不受支持", "该接口只支持 " + method);
        }
    }

    private static void requireExactPath(HttpExchange exchange, String path) {
        if (!path.equals(exchange.getRequestURI().getPath())) {
            throw problem(404, "NOT_FOUND", "接口不存在", "请求路径不存在");
        }
    }

    private static byte[] readLimited(InputStream input, int maximumBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximumBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > maximumBytes) {
                throw problem(413, "REQUEST_TOO_LARGE", "请求体过大", "请求体超过允许大小");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        send(exchange, status, JSON, objectMapper.writeValueAsBytes(body));
    }

    private void sendProblem(HttpExchange exchange, TaskEngineException exception) throws IOException {
        if (exception.status() == 401) {
            exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
        }
        if (exception.status() == 429) {
            exchange.getResponseHeaders().set("Retry-After", "1");
        }
        ProblemResponse problem = new ProblemResponse(
                "urn:datascalpel:task-engine:problem:" + exception.code().toLowerCase(Locale.ROOT).replace('_', '-'),
                exception.title(),
                exception.status(),
                exception.getMessage(),
                exchange.getRequestURI().getPath(),
                exception.code(),
                Instant.now()
        );
        send(exchange, exception.status(), PROBLEM_JSON, objectMapper.writeValueAsBytes(problem));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private static TaskEngineException invalidJson(JsonProcessingException exception) {
        if (exception instanceof InvalidTypeIdException invalidType
                && invalidType.getBaseType().getRawClass() == CanvasNodeDefinition.class) {
            return problem(400, "UNSUPPORTED_NODE_TYPE", "节点类型不受支持", "Canvas 包含未知节点类型");
        }
        if (exception instanceof InvalidFormatException invalidFormat
                && invalidFormat.getPath().stream().anyMatch(reference -> "type".equals(reference.getFieldName()))) {
            return problem(400, "UNKNOWN_TASK_TYPE", "任务类型不受支持", "task.type 不受支持");
        }
        return problem(400, "INVALID_JSON", "JSON 请求无效", "请求体不符合 Task Engine 协议");
    }

    private static TaskEngineException problem(int status, String code, String title, String detail) {
        return new TaskEngineException(status, code, title, detail);
    }

    private static ThreadFactory namedThreads(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> new Thread(runnable, prefix + sequence.incrementAndGet());
    }

    @FunctionalInterface
    private interface ExchangeAction {
        void execute() throws Exception;
    }
}
