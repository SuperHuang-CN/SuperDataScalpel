package cn.superhuang.data.scalpel.contract.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** Idempotent request sent by the Admin control plane to an Engine. */
public record ServiceDeploymentRequest(
        @NotNull UUID serviceId,
        @Positive long revision,
        @NotBlank String serviceCode,
        @NotBlank String routePath,
        @NotBlank String definitionDigest,
        @NotNull @Valid ServiceDefinitionSnapshot definition,
        @NotNull UUID dataSourceId
) {
}
