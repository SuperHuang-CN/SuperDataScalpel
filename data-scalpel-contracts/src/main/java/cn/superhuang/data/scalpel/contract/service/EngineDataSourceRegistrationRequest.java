package cn.superhuang.data.scalpel.contract.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Request that stores or replaces one JDBC data source in an Engine. */
public record EngineDataSourceRegistrationRequest(
        @NotNull UUID dataSourceId,
        @NotNull @Valid JdbcDataSourceSnapshot dataSource
) {
}
