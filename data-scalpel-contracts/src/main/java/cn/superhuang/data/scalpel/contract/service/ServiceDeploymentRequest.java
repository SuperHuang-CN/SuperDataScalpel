package cn.superhuang.data.scalpel.contract.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Request sent by the Admin control plane to create or replace an Engine deployment. */
public record ServiceDeploymentRequest(
        @NotNull UUID serviceId,
        @NotBlank String serviceCode,
        @NotBlank String routePath,
        @NotBlank String definitionDigest,
        @NotNull @Valid ServiceDefinitionSnapshot definition,
        @NotNull UUID dataSourceId
) {
}
