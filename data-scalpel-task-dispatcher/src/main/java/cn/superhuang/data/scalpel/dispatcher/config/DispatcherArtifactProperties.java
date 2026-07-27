package cn.superhuang.data.scalpel.dispatcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "data-scalpel.dispatcher.artifact")
public record DispatcherArtifactProperties(
        String endpoint,
        String runnerEndpoint,
        String region,
        String bucket,
        String rootPrefix,
        String accessKey,
        String secretKey,
        boolean pathStyleAccess,
        Duration urlLifetime,
        long maximumManifestBytes,
        int maximumResultBytes,
        Duration resultAvailabilityGrace
) {
    public DispatcherArtifactProperties {
        region = textOrDefault(region, "us-east-1");
        bucket = textOrDefault(bucket, "datascalpel");
        rootPrefix = normalizePrefix(rootPrefix);
        urlLifetime = urlLifetime == null ? Duration.ofHours(2) : urlLifetime;
        maximumManifestBytes = maximumManifestBytes < 1 ? 10L * 1024 * 1024 : maximumManifestBytes;
        maximumResultBytes = maximumResultBytes < 1 ? 5 * 1024 * 1024 : maximumResultBytes;
        resultAvailabilityGrace = resultAvailabilityGrace == null ? Duration.ofSeconds(30) : resultAvailabilityGrace;
        if (maximumManifestBytes > 20L * 1024 * 1024 || maximumResultBytes > 20 * 1024 * 1024
                || resultAvailabilityGrace.isNegative() || resultAvailabilityGrace.isZero()) {
            throw new IllegalArgumentException("任务制品大小或等待时间配置无效");
        }
    }

    public String effectiveRunnerEndpoint() {
        return runnerEndpoint == null || runnerEndpoint.isBlank() ? endpoint : runnerEndpoint.trim();
    }

    public boolean configured() {
        return hasText(endpoint) && hasText(effectiveRunnerEndpoint()) && hasText(bucket)
                && hasText(accessKey) && hasText(secretKey) && !urlLifetime.isNegative() && !urlLifetime.isZero();
    }

    private static String textOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalizePrefix(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.trim();
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}
