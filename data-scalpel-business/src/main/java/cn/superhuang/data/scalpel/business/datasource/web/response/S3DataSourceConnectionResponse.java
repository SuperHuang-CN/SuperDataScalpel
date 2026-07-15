package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnection;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;

public record S3DataSourceConnectionResponse(
        String endpoint,
        String region,
        String bucket,
        String rootPrefix,
        String accessKey,
        boolean secretKeyConfigured,
        boolean pathStyleAccess
) implements DataSourceConnectionResponse {
    public static S3DataSourceConnectionResponse from(DataSourceConnection connection) {
        return new S3DataSourceConnectionResponse(
                connection.getEndpoint(), connection.getOptions().get("region"), connection.getTarget(),
                connection.getNamespace(), connection.getPrincipal(), connection.hasPassword(),
                Boolean.parseBoolean(connection.getOptions().getOrDefault("pathStyleAccess", "true"))
        );
    }

    @Override
    public DataSourceConnectionKind kind() {
        return DataSourceConnectionKind.S3;
    }
}
