package cn.superhuang.data.scalpel.contract.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** Result of testing the snapshot currently stored in an Engine. */
public record EngineDataSourceTestResponse(
        @NotBlank String engineCode,
        @NotNull UUID dataSourceId,
        @Positive long revision,
        @NotBlank String databaseType
) {
}
