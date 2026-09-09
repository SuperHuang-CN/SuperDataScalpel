package cn.superhuang.data.scalpel.business.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

@ConfigurationProperties("data-scalpel.mcp")
public record McpPlatformProperties(
        String publicBaseUrl,
        String credentialKey,
        Duration executionTimeout,
        Integer executionConcurrency,
        Integer executionQueueCapacity,
        DataSize maxScriptSize,
        DataSize maxSchemaSize,
        DataSize maxPayloadSize,
        Integer maxToolsPerServer,
        Duration invocationRetention
) {
    public McpPlatformProperties {
        publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
        executionTimeout = executionTimeout == null ? Duration.ofSeconds(10) : executionTimeout;
        executionConcurrency = executionConcurrency == null ? 8 : Math.max(1, executionConcurrency);
        executionQueueCapacity = executionQueueCapacity == null ? 32 : Math.max(0, executionQueueCapacity);
        maxScriptSize = maxScriptSize == null ? DataSize.ofKilobytes(200) : maxScriptSize;
        maxSchemaSize = maxSchemaSize == null ? DataSize.ofKilobytes(64) : maxSchemaSize;
        maxPayloadSize = maxPayloadSize == null ? DataSize.ofMegabytes(1) : maxPayloadSize;
        maxToolsPerServer = maxToolsPerServer == null ? 100 : Math.max(1, maxToolsPerServer);
        invocationRetention = invocationRetention == null ? Duration.ofDays(30) : invocationRetention;
        if (executionTimeout.isZero() || executionTimeout.isNegative() || invocationRetention.isNegative()
                || invocationRetention.isZero() || maxPayloadSize.toBytes() < 1024
                || maxPayloadSize.toBytes() >= Integer.MAX_VALUE
                || maxScriptSize.toBytes() < 1 || maxSchemaSize.toBytes() < 1) {
            throw new IllegalArgumentException("MCP 超时、保留时间和大小限制必须为有效正值；Payload 至少 1KB");
        }
    }
    public String endpoint(String serverCode) { return (publicBaseUrl == null ? "" : publicBaseUrl) + "/mcp/" + serverCode; }
    private static String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}
