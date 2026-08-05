package cn.superhuang.data.scalpel.contract.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Result of an Engine data source registration request. */
public record EngineDataSourceRegistrationResponse(
        @NotBlank String engineCode,
        @NotNull UUID dataSourceId,
        @NotNull EngineDataSourceStatus status,
        @NotBlank String message
) {
}
