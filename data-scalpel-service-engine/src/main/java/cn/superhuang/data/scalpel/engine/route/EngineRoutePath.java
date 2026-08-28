package cn.superhuang.data.scalpel.engine.route;

import java.util.Locale;
import java.util.regex.Pattern;

/** Runtime-side guard for the Engine-private dynamic-route namespace. */
final class EngineRoutePath {

    private static final Pattern PATTERN = Pattern.compile("/runtime/v1/services/[0-9a-f-]{36}");

    private EngineRoutePath() {
    }

    static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("服务路径不能为空");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!PATTERN.matcher(normalized).matches() || normalized.endsWith("/") || normalized.contains("//")) {
            throw new IllegalArgumentException("服务路径必须是 /runtime/v1/services/{serviceId}");
        }
        return normalized;
    }
}
