package cn.superhuang.data.scalpel.contract.service;

import java.util.UUID;

/** Deployment acknowledgement emitted by an Engine. */
public record ServiceDeploymentResponse(
        UUID serviceId,
        EngineDeploymentStatus status,
        String message
) {
}
