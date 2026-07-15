package cn.superhuang.data.scalpel.contract.service;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** Idempotent request that removes an Engine-local JDBC data source snapshot. */
public record EngineDataSourceRemovalRequest(
        @NotNull UUID dataSourceId,
        @Positive long revision
) {
}
