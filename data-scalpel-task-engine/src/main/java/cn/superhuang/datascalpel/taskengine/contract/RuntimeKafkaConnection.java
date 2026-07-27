package cn.superhuang.datascalpel.taskengine.contract;



public record RuntimeKafkaConnection(
        String bootstrapServers,
        KafkaSecurityProtocol securityProtocol,
        KafkaSaslMechanism saslMechanism,
        String username,
        String password
) {
}
