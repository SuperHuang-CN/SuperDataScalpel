package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Request that stores or replaces one JDBC data source in an Engine. */
public record EngineDataSourceRegistrationRequest(
        @JsonPropertyDescription("数据源 UUID。")
        @NotNull UUID dataSourceId,
        @JsonPropertyDescription("注册到 Service Engine 的完整 JDBC 数据源连接快照。")
        @NotNull @Valid JdbcDataSourceSnapshot dataSource
) {
}
