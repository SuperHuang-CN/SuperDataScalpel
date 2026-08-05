package cn.superhuang.data.scalpel.contract.service;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Idempotent request to remove a previously deployed service route. */
public record ServiceUndeploymentRequest(
        @NotNull UUID serviceId
) {
}
