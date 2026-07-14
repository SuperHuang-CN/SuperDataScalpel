package cn.superhuang.data.scalpel.business.system.configuration.web.response;

import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfiguration;
import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfigurationValueType;

import java.time.Instant;
import java.util.UUID;

public record SystemConfigurationResponse(
        UUID id,
        String configKey,
        String name,
        String configValue,
        SystemConfigurationValueType valueType,
        String description,
        Integer sortOrder,
        Instant createdAt,
        Instant updatedAt
) {
    public static SystemConfigurationResponse from(SystemConfiguration configuration) {
        return new SystemConfigurationResponse(
                configuration.getId(),
                configuration.getConfigKey(),
                configuration.getName(),
                configuration.getConfigValue(),
                configuration.getValueType(),
                configuration.getDescription(),
                configuration.getSortOrder(),
                configuration.getCreatedAt(),
                configuration.getUpdatedAt()
        );
    }
}
