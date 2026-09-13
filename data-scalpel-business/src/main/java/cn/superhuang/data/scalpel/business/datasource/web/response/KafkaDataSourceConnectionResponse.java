package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnection;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "浏览器安全的 Kafka 集群连接配置；不返回 SASL 密码")
public record KafkaDataSourceConnectionResponse(
        @Schema(description = "Kafka Bootstrap 服务器，多个地址用逗号分隔") String bootstrapServers,
        @Schema(description = "Kafka 安全协议") String securityProtocol,
        @Schema(description = "SASL 认证机制；未启用 SASL 时为空") String saslMechanism,
        @Schema(description = "SASL 用户名；未启用 SASL 时为空") String username,
        @Schema(description = "是否已保存 SASL 密码；只表示存在，不泄露密码值") boolean passwordConfigured
) implements DataSourceConnectionResponse {
    public static KafkaDataSourceConnectionResponse from(DataSourceConnection connection) {
        return new KafkaDataSourceConnectionResponse(
                connection.getEndpoint(), connection.getOptions().get("securityProtocol"),
                connection.getOptions().get("saslMechanism"), connection.getPrincipal(), connection.hasPassword()
        );
    }

    @Override
    public DataSourceConnectionKind kind() {
        return DataSourceConnectionKind.KAFKA;
    }
}
