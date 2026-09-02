package cn.superhuang.data.scalpel.business.service.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** V1 service Context Path policy shared by Admin validation and Engine deployment. */
public final class ServiceRoutePath {

    private static final String PREFIX = "/open-api/v1/";
    private static final Pattern PATTERN = Pattern.compile("/open-api/v1/[a-z0-9][a-z0-9/_-]*");

    private ServiceRoutePath() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("服务路径不能为空");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith(PREFIX) || normalized.endsWith("/") || normalized.contains("//")
                || !PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("服务路径必须是 /open-api/v1/ 下的小写静态路径");
        }
        return normalized;
    }
}
