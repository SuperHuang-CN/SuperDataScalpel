package cn.superhuang.data.scalpel.contract.execution;

/**
 * Kafka channel used by a dispatcher to address control commands to one
 * long-running Runner attempt.
 */
public record RunnerControlChannel(
        String bootstrapServers,
        String topic,
        RunnerKafkaSecurityProtocol securityProtocol,
        String clientId,
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
