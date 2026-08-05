package cn.superhuang.data.scalpel.contract.service;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Idempotent request that removes an API Studio data source from an Engine. */
public record EngineDataSourceRemovalRequest(
        @NotNull UUID dataSourceId
) {
}
