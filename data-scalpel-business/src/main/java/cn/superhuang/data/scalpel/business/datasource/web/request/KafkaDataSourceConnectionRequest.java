package cn.superhuang.data.scalpel.business.datasource.web.request;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Kafka cluster registration. Topics remain task-level resources rather than connection fields. */
public record KafkaDataSourceConnectionRequest(
        @NotBlank @Size(max = 500) String bootstrapServers,
        @Pattern(regexp = "PLAINTEXT|SSL|SASL_PLAINTEXT|SASL_SSL", message = "Kafka 安全协议不合法") String securityProtocol,
        @Pattern(
                regexp = "PLAIN|SCRAM-SHA-256|SCRAM-SHA-512",
                message = "Kafka SASL 机制不合法"
        ) @Size(max = 64) String saslMechanism,
        @Size(max = 128) String username,
        @Size(max = 512) String password
) implements DataSourceConnectionRequest {
    @Override
    public DataSourceConnectionKind kind() {
        return DataSourceConnectionKind.KAFKA;
    }
}
