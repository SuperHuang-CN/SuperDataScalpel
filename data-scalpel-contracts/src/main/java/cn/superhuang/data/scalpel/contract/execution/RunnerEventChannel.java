package cn.superhuang.data.scalpel.contract.execution;

public record RunnerEventChannel(
        String bootstrapServers,
        String topic,
        RunnerKafkaSecurityProtocol securityProtocol,
        String clientId
) {
    public RunnerEventChannel {
        bootstrapServers = ExecutionContractValidation.required(bootstrapServers, 1000, "Kafka Bootstrap Servers");
        if (bootstrapServers.contains("\r") || bootstrapServers.contains("\n")) {
            throw new IllegalArgumentException("Kafka Bootstrap Servers 不合法");
        }
        topic = ExecutionContractValidation.topic(topic);
        if (securityProtocol == null) throw new IllegalArgumentException("Kafka Security Protocol 不能为空");
        clientId = ExecutionContractValidation.required(clientId, 200, "Kafka Client ID");
        if (!clientId.matches("[a-zA-Z0-9._-]+")) throw new IllegalArgumentException("Kafka Client ID 不合法");
    }
}
