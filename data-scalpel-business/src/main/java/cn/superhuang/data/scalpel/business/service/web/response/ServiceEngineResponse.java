package cn.superhuang.data.scalpel.business.service.web.response;

import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineType;

import java.time.Instant;
import java.util.UUID;

public record ServiceEngineResponse(
        UUID id,
        ServiceEngineType type,
        String code,
        String name,
        String adminUrl,
        String runtimeUrl,
        boolean managementTokenConfigured,
        String geoServerUsername,
        String geoServerWorkspace,
        boolean geoServerCredentialConfigured,
        boolean enabled,
        String description,
        Instant createdAt,
        Instant updatedAt
) {

    public static ServiceEngineResponse from(ServiceEngine engine) {
        return new ServiceEngineResponse(
                engine.getId(), engine.getType(), engine.getCode(), engine.getName(), engine.getAdminUrl(), engine.getRuntimeUrl(),
                engine.getManagementTokenCiphertext() != null && !engine.getManagementTokenCiphertext().isBlank(),
                engine.getGeoServerUsername(), engine.getGeoServerWorkspace(),
                engine.getGeoServerPasswordCiphertext() != null && !engine.getGeoServerPasswordCiphertext().isBlank(),
                engine.isEnabled(), engine.getDescription(), engine.getCreatedAt(), engine.getUpdatedAt()
        );
    }
}
