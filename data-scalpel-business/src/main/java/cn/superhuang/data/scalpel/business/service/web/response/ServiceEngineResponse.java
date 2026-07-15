package cn.superhuang.data.scalpel.business.service.web.response;

import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;

import java.time.Instant;
import java.util.UUID;

public record ServiceEngineResponse(
        UUID id,
        String code,
        String name,
        String adminUrl,
        String publicUrl,
        boolean enabled,
        String description,
        Instant createdAt,
        Instant updatedAt
) {

    public static ServiceEngineResponse from(ServiceEngine engine) {
        return new ServiceEngineResponse(
                engine.getId(), engine.getCode(), engine.getName(), engine.getAdminUrl(), engine.getPublicUrl(),
                engine.isEnabled(), engine.getDescription(), engine.getCreatedAt(), engine.getUpdatedAt()
        );
    }
}
