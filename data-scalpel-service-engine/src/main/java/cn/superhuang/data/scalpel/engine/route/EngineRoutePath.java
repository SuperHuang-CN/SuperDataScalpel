package cn.superhuang.data.scalpel.engine.route;

import java.util.Locale;
import java.util.regex.Pattern;

/** Runtime-side guard for the fixed V1 dynamic-route namespace. */
final class EngineRoutePath {

    private static final Pattern PATTERN = Pattern.compile("/open-api/v1/[a-z0-9][a-z0-9/_-]*");

    private EngineRoutePath() {
    }

    static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("服务路径不能为空");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!PATTERN.matcher(normalized).matches() || normalized.endsWith("/") || normalized.contains("//")) {
            throw new IllegalArgumentException("服务路径必须是 /open-api/v1/ 下的小写静态路径");
        }
        return normalized;
    }
}
