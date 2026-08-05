package cn.superhuang.data.scalpel.business.service.accesslog.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@ConditionalOnProperty(
        prefix = "data-scalpel.gateway-access",
        name = "enabled",
        havingValue = "true"
)
public class GatewayAccessKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(GatewayAccessKafkaListener.class);
    private static final int MAX_REPORTED_REASONS = 5;

    private final GatewayAccessProperties properties;
    private final GatewayAccessEventDecoder decoder;
    private final GatewayAccessLogStore logStore;

    public GatewayAccessKafkaListener(
            GatewayAccessProperties properties,
            GatewayAccessEventDecoder decoder,
            GatewayAccessLogStore logStore
    ) {
        this.properties = properties;
        this.decoder = decoder;
        this.logStore = logStore;
    }

    @KafkaListener(
            topics = "${data-scalpel.gateway-access.topic:datascalpel.gateway.access.v1}",
            groupId = "${data-scalpel.gateway-access.consumer-group:data-scalpel-admin-gateway-access-v1}",
            concurrency = "${data-scalpel.gateway-access.concurrency:2}",
            containerFactory = "gatewayAccessKafkaListenerContainerFactory",
            properties = "max.poll.records=${data-scalpel.gateway-access.max-poll-records:500}"
    )
    public void receive(List<ConsumerRecord<String, String>> sourceRecords) {
        Instant receivedAt = Instant.now();
        List<GatewayAccessRecord> accepted = new ArrayList<>(sourceRecords.size());
        Set<String> rejectedReasons = new LinkedHashSet<>();
        int rejected = 0;

        for (ConsumerRecord<String, String> source : sourceRecords) {
            try {
                accepted.add(decoder.decode(source, receivedAt, properties.rawRetention()));
            } catch (RuntimeException exception) {
                rejected++;
                if (rejectedReasons.size() < MAX_REPORTED_REASONS) {
                    rejectedReasons.add(safeReason(exception));
                }
            }
        }

        logStore.append(accepted);
        if (rejected > 0) {
            log.warn(
                    "已丢弃 {} 条非法网关访问日志，本批接收 {} 条，原因摘要：{}",
                    rejected,
                    sourceRecords.size(),
                    rejectedReasons
            );
        }
    }

    private static String safeReason(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "无法解析规范化事件";
        }
        String normalized = message
                .replaceAll("(?i)(password|secret|token|authorization|api[-_]?key)=[^\\s,;]+", "$1=***")
                .replaceAll("[\\r\\n\\t]+", " ");
        return normalized.substring(0, Math.min(300, normalized.length()));
    }
}
