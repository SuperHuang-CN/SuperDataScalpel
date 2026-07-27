package cn.superhuang.datascalpel.taskengine.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public record EngineConfiguration(
        String host,
        int port,
        String authToken,
        int httpExecutorThreads,
        int maxRequestBytes,
        int maxCompileConcurrency,
        Duration acquireTimeout,
        Duration compileTimeout,
        Map<String, String> sparkProperties
) {
    private static final Map<String, String> ENVIRONMENT_KEYS = Map.ofEntries(
            Map.entry("DATASCALPEL_TASK_ENGINE_HOST", "task.engine.host"),
            Map.entry("DATASCALPEL_TASK_ENGINE_PORT", "task.engine.port"),
            Map.entry("DATASCALPEL_TASK_ENGINE_MAX_CONCURRENCY", "task.engine.compile.max-concurrency"),
            Map.entry("DATASCALPEL_TASK_ENGINE_ACQUIRE_TIMEOUT_SECONDS", "task.engine.compile.acquire-timeout-seconds"),
            Map.entry("DATASCALPEL_TASK_ENGINE_COMPILE_TIMEOUT_SECONDS", "task.engine.compile.timeout-seconds")
    );

    public EngineConfiguration {
        sparkProperties = Map.copyOf(sparkProperties);
    }

    public static EngineConfiguration load(String[] args) throws IOException {
        Properties properties = defaults();
        Path configuredPath = configurationPath(args);
        if (configuredPath != null) {
            try (InputStream input = Files.newInputStream(configuredPath)) {
                properties.load(input);
            }
        }
        ENVIRONMENT_KEYS.forEach((environmentKey, propertyKey) -> {
            String value = System.getenv(environmentKey);
            if (value != null && !value.isBlank()) {
                properties.setProperty(propertyKey, value.trim());
            }
        });

        String token = System.getenv("DATASCALPEL_TASK_ENGINE_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("DATASCALPEL_TASK_ENGINE_TOKEN must be configured");
        }
        Map<String, String> sparkProperties = new LinkedHashMap<>();
        properties.stringPropertyNames().stream()
                .filter(name -> name.startsWith("spark."))
                .sorted()
                .forEach(name -> sparkProperties.put(name, properties.getProperty(name)));

        return new EngineConfiguration(
                required(properties, "task.engine.host"),
                integer(properties, "task.engine.port", 1, 65535),
                token,
                integer(properties, "task.engine.http.executor-threads", 1, 1024),
                integer(properties, "task.engine.http.max-request-bytes", 1024, 100 * 1024 * 1024),
                integer(properties, "task.engine.compile.max-concurrency", 1, 128),
                Duration.ofSeconds(integer(properties, "task.engine.compile.acquire-timeout-seconds", 0, 3600)),
                Duration.ofSeconds(integer(properties, "task.engine.compile.timeout-seconds", 1, 86400)),
                sparkProperties
        );
    }

    private static Properties defaults() {
        Properties properties = new Properties();
        properties.setProperty("task.engine.host", "0.0.0.0");
        properties.setProperty("task.engine.port", "8091");
        properties.setProperty("task.engine.http.executor-threads", "8");
        properties.setProperty("task.engine.http.max-request-bytes", "10485760");
        properties.setProperty("task.engine.compile.max-concurrency", "2");
        properties.setProperty("task.engine.compile.acquire-timeout-seconds", "10");
        properties.setProperty("task.engine.compile.timeout-seconds", "30");
        properties.setProperty("spark.master", "local[*]");
        properties.setProperty("spark.app.name", "DataScalpel Task Engine");
        properties.setProperty("spark.ui.enabled", "false");
        properties.setProperty("spark.sql.shuffle.partitions", "4");
        properties.setProperty("spark.sql.caseSensitive", "true");
        properties.setProperty("spark.sql.ansi.enabled", "true");
        properties.setProperty("spark.sql.session.timeZone", "UTC");
        return properties;
    }

    private static Path configurationPath(String[] args) {
        String environmentPath = System.getenv("DATASCALPEL_TASK_ENGINE_CONFIG");
        String value = environmentPath != null && !environmentPath.isBlank()
                ? environmentPath.trim()
                : args.length == 0 ? null : args[0];
        return value == null || value.isBlank() ? null : Path.of(value).toAbsolutePath().normalize();
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing configuration: " + key);
        }
        return value.trim();
    }

    private static int integer(Properties properties, String key, int minimum, int maximum) {
        String value = required(properties, key);
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < minimum || parsed > maximum) {
                throw new IllegalArgumentException("Configuration %s must be between %d and %d"
                        .formatted(key, minimum, maximum));
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Configuration %s must be an integer".formatted(key), exception);
        }
    }

}
