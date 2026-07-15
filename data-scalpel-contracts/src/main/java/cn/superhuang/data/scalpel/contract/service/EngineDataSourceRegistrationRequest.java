package cn.superhuang.data.scalpel.contract.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** Idempotent request that stores one JDBC data source snapshot in an Engine. */
public record EngineDataSourceRegistrationRequest(
        @NotNull UUID dataSourceId,
        @Positive long revision,
        @NotNull @Valid JdbcDataSourceSnapshot dataSource
) {
}
