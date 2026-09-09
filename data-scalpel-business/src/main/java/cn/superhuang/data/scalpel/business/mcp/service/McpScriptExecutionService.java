package cn.superhuang.data.scalpel.business.mcp.service;

import cn.superhuang.data.scalpel.business.mcp.config.McpPlatformProperties;
import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import groovy.transform.ThreadInterrupt;
import jakarta.annotation.PreDestroy;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class McpScriptExecutionService {
    private static final AtomicLong THREAD_SEQUENCE = new AtomicLong();
    private static final int CACHE_LIMIT = 64;
    private final McpPlatformProperties properties;
    private final McpSchemaService schemas;
    private final ObjectMapper mapper;
    private final McpPayloadService payloads;
    private final ThreadPoolExecutor executor;
    private final Map<String, CompiledScript> compiled = new LinkedHashMap<>(16, .75f, true);

    public McpScriptExecutionService(McpPlatformProperties properties, McpSchemaService schemas,
                                    ObjectMapper mapper, McpPayloadService payloads) {
        this.properties = properties;
        this.schemas = schemas;
        this.mapper = mapper;
        this.payloads = payloads;
        executor = new ThreadPoolExecutor(properties.executionConcurrency(), properties.executionConcurrency(),
                60, TimeUnit.SECONDS, properties.executionQueueCapacity() == 0 ? new SynchronousQueue<>()
                    : new ArrayBlockingQueue<>(properties.executionQueueCapacity()), runnable -> {
            Thread thread = new Thread(runnable, "mcp-groovy-" + THREAD_SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    public void validateDefinition(String inputSchema, String outputSchema, String script) {
        controlled(() -> {
            validateSchemas(inputSchema, outputSchema, script);
            CompiledScript entry = acquire(script);
            release(entry);
            return null;
        });
    }

    private void validateSchemas(String inputSchema, String outputSchema, String script) {
        enforceSize(inputSchema, properties.maxSchemaSize().toBytes(), "Input Schema");
        enforceSize(outputSchema, properties.maxSchemaSize().toBytes(), "Output Schema");
        enforceSize(script, properties.maxScriptSize().toBytes(), "Groovy 脚本");
        schemas.parseSchema(inputSchema, true);
        schemas.parseSchema(outputSchema, false);
    }

    public ExecutionResult execute(String inputSchema, String outputSchema, String source, JsonNode arguments,
                                   Map<String, Object> context) {
        long started = System.nanoTime();
        return controlled(() -> {
            validateSchemas(inputSchema, outputSchema, source);
            if (arguments == null || !arguments.isObject()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tool 参数必须是 JSON 对象");
            }
            schemas.validate(schemas.parseSchema(inputSchema, true), arguments, "输入参数");
            CompiledScript entry = acquire(source);
            try {
                Script script = entry.type.getDeclaredConstructor().newInstance();
                Binding binding = new Binding();
                binding.setVariable("args", mapper.convertValue(arguments, Map.class));
                binding.setVariable("context", Map.copyOf(context));
                ScriptLogger logger = new ScriptLogger(Boolean.TRUE.equals(context.get("draft")));
                binding.setVariable("log", logger);
                binding.setVariable("json", new JsonHelper());
                script.setBinding(binding);
                Object raw;
                try {
                    raw = script.run();
                } catch (Exception exception) {
                    throw new McpToolExecutionException("SCRIPT_FAILED", "Tool 脚本执行失败", exception);
                }
                Map<String, Object> wrapped = new LinkedHashMap<>();
                wrapped.put("value", raw);
                // Serialize once while inside the worker. Only plain JSON escapes this boundary,
                // never user getters, lazy iterators or Groovy objects.
                String json;
                JsonNode output;
                try {
                    json = payloads.write(raw instanceof Map<?, ?> ? raw : wrapped);
                    output = mapper.readTree(json);
                    schemas.validate(schemas.parseSchema(outputSchema, false), output, "输出结果");
                } catch (McpToolExecutionException exception) {
                    throw exception;
                } catch (ResponseStatusException exception) {
                    throw new McpToolExecutionException("OUTPUT_SCHEMA_MISMATCH", "Tool 输出不符合 Schema", exception);
                } catch (RuntimeException exception) {
                    throw new McpToolExecutionException("OUTPUT_INVALID", "Tool 返回了无法转换为 JSON 的结果", exception);
                }
                Object normalized = mapper.convertValue(output, Object.class);
                String text = raw instanceof CharSequence ? output.path("value").asText() : json;
                // Include both content representations and envelope overhead in the limit.
                payloads.write(Map.of("jsonrpc", "2.0", "id", "x".repeat(128), "result",
                        Map.of("content", List.of(Map.of("type", "text", "text", text)),
                                "structuredContent", normalized, "isError", false)));
                return new ExecutionResult(normalized, text, elapsed(started), logger.snapshot());
            } finally {
                release(entry);
            }
        });
    }

    private <T> T controlled(Callable<T> task) {
        Future<T> future = null;
        try {
            future = executor.submit(task);
            return future.get(properties.executionTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            cancel(future);
            throw new McpToolExecutionException("EXECUTION_TIMEOUT", "Tool 执行超时", exception);
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "MCP 执行队列已满，请稍后重试");
        } catch (InterruptedException exception) {
            cancel(future);
            Thread.currentThread().interrupt();
            throw new McpToolExecutionException("EXECUTION_INTERRUPTED", "Tool 执行被中断", exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ResponseStatusException status) throw status;
            if (cause instanceof McpToolExecutionException execution) throw execution;
            throw new McpToolExecutionException("EXECUTION_FAILED", "Tool 执行失败", cause);
        }
    }

    private void cancel(Future<?> future) {
        if (future != null) future.cancel(true);
        executor.purge();
    }

    @SuppressWarnings("unchecked")
    private synchronized CompiledScript acquire(String source) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        String digest = sha256(source);
        CompiledScript cached = compiled.get(digest);
        if (cached != null) { cached.users++; return cached; }
        CompilerConfiguration config = new CompilerConfiguration();
        config.addCompilationCustomizers(new ASTTransformationCustomizer(ThreadInterrupt.class));
        GroovyClassLoader loader = new GroovyClassLoader(getClass().getClassLoader(), config);
        try {
            Class<?> type = loader.parseClass(source, "McpTool_" + digest + ".groovy");
            if (!Script.class.isAssignableFrom(type)) throw new IllegalArgumentException("必须提供 Groovy 脚本");
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            CompiledScript entry = new CompiledScript(loader, (Class<? extends Script>) type);
            compiled.put(digest, entry);
            while (compiled.size() > CACHE_LIMIT) {
                CompiledScript retired = compiled.remove(compiled.keySet().iterator().next());
                retired.retired = true;
                if (retired.users == 0) retired.close();
            }
            return entry;
        } catch (Exception exception) {
            loader.clearCache();
            try { loader.close(); } catch (java.io.IOException ignored) { /* No open source files. */ }
            if (exception instanceof InterruptedException interrupted) throw interrupted;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Groovy 脚本编译失败：" + debugMessage(exception));
        }
    }

    private synchronized void release(CompiledScript entry) {
        entry.users--;
        if (entry.retired && entry.users == 0) entry.close();
    }

    private static final class CompiledScript {
        final GroovyClassLoader loader;
        final Class<? extends Script> type;
        int users = 1;
        boolean retired;
        CompiledScript(GroovyClassLoader loader, Class<? extends Script> type) { this.loader = loader; this.type = type; }
        void close() {
            loader.clearCache();
            try { loader.close(); } catch (java.io.IOException ignored) { /* Sources are in memory. */ }
        }
    }

    private static void enforceSize(String value, long maxBytes, String label) {
        if (value != null && value.getBytes(StandardCharsets.UTF_8).length > maxBytes)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "超过大小限制");
    }
    private static long elapsed(long start) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start); }
    private static String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    public static String debugMessage(Throwable error) {
        String value = error.getMessage();
        return value == null || value.isBlank() ? error.getClass().getSimpleName() : value.substring(0, Math.min(800, value.length()));
    }
    @PreDestroy
    public synchronized void close() {
        executor.shutdownNow();
        compiled.values().forEach(entry -> { entry.retired = true; if (entry.users == 0) entry.close(); });
        compiled.clear();
    }

    public record ExecutionResult(Object structuredContent, String text, long durationMillis, List<String> logs) {}
    public static final class McpToolExecutionException extends RuntimeException {
        private final String code;
        public McpToolExecutionException(String code, String message, Throwable cause) { super(message, cause); this.code = code; }
        public String code() { return code; }
    }
    public static final class JsonHelper {
        public Object parse(String json) { return new JsonSlurper().parseText(json); }
        public String stringify(Object value) { return JsonOutput.toJson(value); }
    }
    public static final class ScriptLogger {
        private final boolean capture;
        private final List<String> lines = new ArrayList<>();
        ScriptLogger(boolean capture) { this.capture = capture; }
        public void info(Object message) { append("INFO", message); }
        public void warn(Object message) { append("WARN", message); }
        public void error(Object message) { append("ERROR", message); }
        private synchronized void append(String level, Object message) {
            if (!capture || lines.size() >= 100) return;
            String text = String.valueOf(message);
            lines.add(level + " " + text.substring(0, Math.min(500, text.length())));
        }
        synchronized List<String> snapshot() { return List.copyOf(lines); }
    }
}
