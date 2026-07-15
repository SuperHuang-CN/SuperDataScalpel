package cn.superhuang.data.scalpel.contract.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** Result of an Engine data source registration request. */
public record EngineDataSourceRegistrationResponse(
        @NotBlank String engineCode,
        @NotNull UUID dataSourceId,
        @Positive long revision,
        @NotNull EngineDataSourceStatus status,
        @NotBlank String message
) {
}
