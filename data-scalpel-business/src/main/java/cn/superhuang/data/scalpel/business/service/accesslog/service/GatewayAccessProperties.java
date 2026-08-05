package cn.superhuang.data.scalpel.business.service.accesslog.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "data-scalpel.gateway-access")
public record GatewayAccessProperties(
        boolean enabled,
        String topic,
        String consumerGroup,
        int concurrency,
        int maxPollRecords,
        Duration rawRetention,
        Duration hourlyRetention,
        int archiveMaxHours,
        int cleanupBatchSize,
        int cleanupMaxBatches
) {

    public GatewayAccessProperties {
        topic = blank(topic) ? "datascalpel.gateway.access.v1" : topic.trim();
        consumerGroup = blank(consumerGroup)
                ? "data-scalpel-admin-gateway-access-v1"
                : consumerGroup.trim();
        concurrency = concurrency < 1 ? 2 : concurrency;
        maxPollRecords = maxPollRecords < 1 ? 500 : maxPollRecords;
        rawRetention = rawRetention == null ? Duration.ofDays(7) : rawRetention;
        hourlyRetention = hourlyRetention == null ? Duration.ofDays(180) : hourlyRetention;
        archiveMaxHours = archiveMaxHours < 1 ? 24 : archiveMaxHours;
        cleanupBatchSize = cleanupBatchSize < 1 ? 10_000 : cleanupBatchSize;
        cleanupMaxBatches = cleanupMaxBatches < 1 ? 100 : cleanupMaxBatches;
        if (rawRetention.isNegative() || rawRetention.isZero()) {
            throw new IllegalArgumentException("网关访问日志原始保留期必须大于 0");
        }
        if (hourlyRetention.isNegative() || hourlyRetention.isZero()) {
            throw new IllegalArgumentException("网关访问日志小时汇总保留期必须大于 0");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
