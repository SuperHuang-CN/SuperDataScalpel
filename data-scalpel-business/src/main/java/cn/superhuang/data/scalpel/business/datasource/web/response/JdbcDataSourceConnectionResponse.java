package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnection;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "浏览器安全的 JDBC 连接配置；不返回数据库密码")
public record JdbcDataSourceConnectionResponse(
        @Schema(description = "数据库服务器主机名或 IP 地址") String host,
        @Schema(description = "数据库服务端口") Integer port,
        @Schema(description = "数据库或实例名称；界面名称由对应数据源类型的 databaseNameLabel 指定") String databaseName,
        @Schema(description = "连接后的默认 Schema；界面名称由对应数据源类型的 schemaNameLabel 指定") String schemaName,
        @Schema(description = "数据库登录用户名") String username,
        @Schema(description = "当前数据库方言允许的高级连接参数") Map<String, String> options,
        @Schema(description = "是否已保存数据库密码；只表示存在，不泄露密码值") boolean passwordConfigured
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
