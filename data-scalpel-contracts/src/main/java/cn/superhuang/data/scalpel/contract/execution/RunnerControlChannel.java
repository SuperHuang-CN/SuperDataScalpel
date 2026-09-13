package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/**
 * Kafka channel used by a dispatcher to address control commands to one
 * long-running Runner attempt.
 */
public record RunnerControlChannel(
        @JsonPropertyDescription("Runner 连接执行 Kafka 通道使用的 Bootstrap Servers。")
        String bootstrapServers,
        @JsonPropertyDescription("Runner 发布事件或接收控制命令的 Kafka Topic。")
        String topic,
        @JsonPropertyDescription("Kafka 安全协议，例如 PLAINTEXT、SASL_PLAINTEXT、SSL 或 SASL_SSL。")
        RunnerKafkaSecurityProtocol securityProtocol,
        @JsonPropertyDescription("Runner 使用的稳定 Kafka client.id。")
        String clientId,
        @JsonPropertyDescription("Runner 控制消息消费者使用的隔离 Kafka Group ID。")
        String groupId
) {
    public RunnerControlChannel {
        bootstrapServers = ExecutionContractValidation.required(
                bootstrapServers, 1000, "Kafka Bootstrap Servers");
        if (bootstrapServers.contains("\r") || bootstrapServers.contains("\n")) {
            throw new IllegalArgumentException("Kafka Bootstrap Servers 不合法");
        }
        topic = ExecutionContractValidation.topic(topic);
        if (securityProtocol == null) {
            throw new IllegalArgumentException("Kafka Security Protocol 不能为空");
        }
        clientId = identifier(clientId, "Kafka Client ID");
        groupId = identifier(groupId, "Kafka Group ID");
    }

    private static String identifier(String value, String name) {
        String normalized = ExecutionContractValidation.required(value, 200, name);
        if (!normalized.matches("[a-zA-Z0-9._-]+")) {
            throw new IllegalArgumentException(name + " 不合法");
        }
        return normalized;
    }
}
