package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnection;

import java.util.Map;

/** Safe connection view. The saved password is deliberately absent. */
public record DataSourceConnectionResponse(
        String host,
        Integer port,
        String databaseName,
        String schemaName,
        String username,
        Map<String, String> options,
        boolean passwordConfigured
) {
    static DataSourceConnectionResponse from(DataSourceConnection connection) {
        return new DataSourceConnectionResponse(
                connection.getHost(),
                connection.getPort(),
                connection.getDatabaseName(),
                connection.getSchemaName(),
                connection.getUsername(),
                connection.getOptions(),
                connection.hasPassword()
        );
    }
}
