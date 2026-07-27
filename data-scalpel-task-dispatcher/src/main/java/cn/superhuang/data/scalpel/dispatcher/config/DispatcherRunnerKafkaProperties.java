package cn.superhuang.data.scalpel.dispatcher.config;

import cn.superhuang.data.scalpel.contract.execution.RunnerKafkaSecurityProtocol;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "data-scalpel.dispatcher.runner-kafka")
public record DispatcherRunnerKafkaProperties(
        String bootstrapServers,
        RunnerKafkaSecurityProtocol securityProtocol,
        String clientIdPrefix
) {
    public DispatcherRunnerKafkaProperties {
        bootstrapServers = textOrDefault(bootstrapServers, "127.0.0.1:9092");
        securityProtocol = securityProtocol == null ? RunnerKafkaSecurityProtocol.PLAINTEXT : securityProtocol;
        clientIdPrefix = textOrDefault(clientIdPrefix, "datascalpel-runner");
        if (bootstrapServers.contains("\r") || bootstrapServers.contains("\n")
                || !clientIdPrefix.matches("[a-zA-Z0-9._-]+")) {
            throw new IllegalArgumentException("Runner Kafka 配置无效");
        }
    }

    private static String textOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
