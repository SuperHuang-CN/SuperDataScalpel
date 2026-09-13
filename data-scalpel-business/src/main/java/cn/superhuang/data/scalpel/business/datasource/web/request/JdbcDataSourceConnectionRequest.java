package cn.superhuang.data.scalpel.business.datasource.web.request;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** A null password on update retains the saved password. */
@Schema(description = "JDBC 数据库连接配置，kind 固定为 JDBC")
public record JdbcDataSourceConnectionRequest(
        @Schema(description = "数据库主机名或 IP；不包含协议和端口", example = "db.internal.example")
        @NotBlank @Size(max = 255) String host,
        @Schema(description = "数据库 TCP 端口", example = "5432")
        @NotNull @Min(1) @Max(65535) Integer port,
        @Schema(description = "数据库、实例或服务名；界面名称由对应数据源类型的 databaseNameLabel 指定")
        @NotBlank @Size(max = 128) String databaseName,
        @Schema(description = "默认 Schema；是否可空及默认值以数据源类型的 namespaceMode/defaultSchema 为准")
        @Size(max = 128) String schemaName,
        @Schema(description = "连接数据库的用户名")
        @NotBlank @Size(max = 128) String username,
        @Schema(description = "数据库密码，只写不返回；创建和未保存连接测试时按数据库要求提供。保持相同数据源产品类型更新时传 null 保留原密码，切换类型时不能沿用", accessMode = Schema.AccessMode.WRITE_ONLY)
        @Size(max = 512) String password,
        @Schema(description = "方言声明的非敏感高级连接参数；键和值必须来自数据源类型返回的 connectionOptions。更新时传 null 保留原参数，传空对象清空参数")
        @Size(max = 20) Map<@NotBlank @Size(max = 64) String, @NotNull @Size(max = 512) String> options
) implements DataSourceConnectionRequest {
    @Override
    public DataSourceConnectionKind kind() {
        return DataSourceConnectionKind.JDBC;
    }
}
