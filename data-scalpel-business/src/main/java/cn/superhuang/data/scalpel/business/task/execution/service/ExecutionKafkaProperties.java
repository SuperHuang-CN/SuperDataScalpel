package cn.superhuang.data.scalpel.business.task.execution.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "data-scalpel.execution.kafka")
public record ExecutionKafkaProperties(
        String consumerGroup,
        List<String> adminEventTopics,
        String invalidMessageTopic,
        int outboxBatchSize,
        Duration outboxPollInterval,
        Duration sendTimeout,
        Duration claimTimeout
) {
    public ExecutionKafkaProperties {
        consumerGroup = blank(consumerGroup) ? "data-scalpel-admin-execution-v1" : consumerGroup.trim();
        adminEventTopics = adminEventTopics == null || adminEventTopics.isEmpty()
                ? List.of("datascalpel.execution.event") : adminEventTopics.stream().map(String::trim).filter(v -> !v.isBlank()).toList();
        invalidMessageTopic = blank(invalidMessageTopic) ? "datascalpel.execution.invalid-message.v1" : invalidMessageTopic.trim();
        outboxBatchSize = outboxBatchSize < 1 ? 50 : outboxBatchSize;
        outboxPollInterval = outboxPollInterval == null ? Duration.ofMillis(500) : outboxPollInterval;
        sendTimeout = sendTimeout == null ? Duration.ofSeconds(10) : sendTimeout;
        claimTimeout = claimTimeout == null ? Duration.ofMinutes(1) : claimTimeout;
    }

    public boolean listensTo(String topic) {
        return !blank(topic) && adminEventTopics.contains(topic.trim());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
