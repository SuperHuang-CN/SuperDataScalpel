package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Idempotent request that removes an API Studio data source from an Engine. */
public record EngineDataSourceRemovalRequest(
        @JsonPropertyDescription("数据源 UUID。")
        @NotNull UUID dataSourceId
) {
}
