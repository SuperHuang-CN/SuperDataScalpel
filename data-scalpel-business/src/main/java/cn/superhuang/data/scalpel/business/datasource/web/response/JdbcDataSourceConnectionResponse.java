package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnection;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;

import java.util.Map;

public record JdbcDataSourceConnectionResponse(
        String host,
        Integer port,
        String databaseName,
        String schemaName,
        String username,
        Map<String, String> options,
        boolean passwordConfigured
) implements DataSourceConnectionResponse {
    public static JdbcDataSourceConnectionResponse from(DataSourceConnection connection) {
        return new JdbcDataSourceConnectionResponse(
                connection.getHost(), connection.getPort(), connection.getDatabaseName(), connection.getSchemaName(),
                connection.getUsername(), connection.getOptions(), connection.hasPassword()
        );
    }

    @Override
    public DataSourceConnectionKind kind() {
        return DataSourceConnectionKind.JDBC;
    }
}
