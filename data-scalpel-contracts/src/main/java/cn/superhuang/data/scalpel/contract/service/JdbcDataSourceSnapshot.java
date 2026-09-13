package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * Connection information delivered from the control plane to an Engine.
 * The Engine encrypts the password before persisting this snapshot.
 */
public record JdbcDataSourceSnapshot(
        @JsonPropertyDescription("数据源 UUID。")
        @NotNull UUID dataSourceId,
        @JsonPropertyDescription("JDBC 数据库产品类型，决定驱动、方言和连接校验。")
        @NotBlank String databaseType,
        @JsonPropertyDescription("数据库主机名或 IP 地址。")
        @NotBlank String host,
        @JsonPropertyDescription("数据库 TCP 端口。")
        @Min(1) @Max(65535) int port,
        @JsonPropertyDescription("数据库名称。")
        @NotBlank String databaseName,
        @JsonPropertyDescription("默认数据库 Schema；产品不支持或未指定时为空。")
        String schemaName,
        @JsonPropertyDescription("建立 JDBC 连接使用的用户名。")
        @NotBlank String username,
        @JsonPropertyDescription("用户密码明文，仅用于本次创建或重置，不会在响应中返回。")
        String password,
        @JsonPropertyDescription("经过白名单校验的额外 JDBC 连接选项。")
        Map<String, String> options
) {

    public JdbcDataSourceSnapshot {
        options = options == null ? Map.of() : Map.copyOf(options);
    }
}
