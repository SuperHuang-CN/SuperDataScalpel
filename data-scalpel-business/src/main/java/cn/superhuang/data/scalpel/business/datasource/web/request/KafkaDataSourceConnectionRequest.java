package cn.superhuang.data.scalpel.business.datasource.web.request;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Kafka cluster registration. Topics remain task-level resources rather than connection fields. */
@Schema(description = "Kafka 集群连接配置；这里只登记集群，Topic 在任务资源中选择")
public record KafkaDataSourceConnectionRequest(
        @Schema(description = "Kafka Bootstrap Servers，多个地址用逗号分隔", example = "kafka-1:9092,kafka-2:9092")
        @NotBlank @Size(max = 500) String bootstrapServers,
        @Schema(description = "Kafka 安全协议；省略或空白时使用 PLAINTEXT", allowableValues = {"PLAINTEXT", "SSL", "SASL_PLAINTEXT", "SASL_SSL"})
        @Pattern(regexp = "PLAINTEXT|SSL|SASL_PLAINTEXT|SASL_SSL", message = "Kafka 安全协议不合法") String securityProtocol,
        @Schema(description = "SASL 认证机制；仅 SASL_* 安全协议需要", allowableValues = {"PLAIN", "SCRAM-SHA-256", "SCRAM-SHA-512"})
        @Pattern(
                regexp = "PLAIN|SCRAM-SHA-256|SCRAM-SHA-512",
                message = "Kafka SASL 机制不合法"
        ) @Size(max = 64) String saslMechanism,
        @Schema(description = "SASL 用户名；仅启用 SASL 时使用")
        @Size(max = 128) String username,
        @Schema(description = "SASL 密码，只写不返回；保持 KAFKA 类型更新时传 null 保留原密码，创建或从其他类型切换到 SASL Kafka 时必须提供", accessMode = Schema.AccessMode.WRITE_ONLY)
        @Size(max = 512) String password
) implements DataSourceConnectionRequest {
    @Override
    public DataSourceConnectionKind kind() {
        return DataSourceConnectionKind.KAFKA;
    }
}
