package cn.superhuang.datascalpel.taskengine.language;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/** Deployment-owned settings. No browser-supplied executable, JVM arguments or dependencies. */
public record LanguageServiceConfiguration(Path home, Path workRoot, int port, int maxSessions,
                                           int heapMiB, Duration retention) {
    public static LanguageServiceConfiguration environment(int httpPort) {
        return from(System.getenv(), httpPort);
    }

    static LanguageServiceConfiguration from(Map<String, String> env, int httpPort) {
        String home = env.get("DATASCALPEL_JDTLS_HOME");
        return new LanguageServiceConfiguration(home == null || home.isBlank() ? null : Path.of(home).toAbsolutePath(),
                Path.of(env.getOrDefault("DATASCALPEL_LANGUAGE_WORK_ROOT",
                        System.getProperty("java.io.tmpdir") + "/datascalpel-language")).toAbsolutePath(),
                integer(Map.of(), "language port (HTTP + 100)", httpPort + 100, 1, 65535),
                integer(env, "DATASCALPEL_LANGUAGE_MAX_SESSIONS", 4, 1, 64),
                integer(env, "DATASCALPEL_LANGUAGE_HEAP_MIB", 512, 256, 4096),
                Duration.ofSeconds(integer(env, "DATASCALPEL_LANGUAGE_RETENTION_SECONDS", 180, 5, 1800)));
    }

    private static int integer(Map<String, String> env, String key, int fallback, int min, int max) {
        int value = Integer.parseInt(env.getOrDefault(key, Integer.toString(fallback)));
        if (value < min || value > max) throw new IllegalArgumentException("Invalid " + key);
        return value;
    }
}
