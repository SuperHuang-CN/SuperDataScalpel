package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnection;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;

public record KafkaDataSourceConnectionResponse(
        String bootstrapServers,
        String securityProtocol,
        String saslMechanism,
        String username,
        boolean passwordConfigured
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
