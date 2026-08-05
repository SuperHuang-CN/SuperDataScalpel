package cn.superhuang.data.scalpel.contract.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Result of testing the API Studio data source currently stored in an Engine. */
public record EngineDataSourceTestResponse(
        @NotBlank String engineCode,
        @NotNull UUID dataSourceId,
        @NotBlank String databaseType
) {
}
