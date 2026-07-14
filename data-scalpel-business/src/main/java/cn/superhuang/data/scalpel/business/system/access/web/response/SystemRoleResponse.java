package cn.superhuang.data.scalpel.business.system.access.web.response;

import cn.superhuang.data.scalpel.business.system.access.domain.SystemRole;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record SystemRoleResponse(
        UUID id,
        String code,
        String name,
        String description,
        boolean builtIn,
        Set<UUID> permissionIds,
        Instant createdAt,
        Instant updatedAt
) {
    public static SystemRoleResponse from(SystemRole role, Set<UUID> permissionIds) {
        return new SystemRoleResponse(
                role.getId(), role.getCode(), role.getName(), role.getDescription(), role.isBuiltIn(), Set.copyOf(permissionIds),
                role.getCreatedAt(), role.getUpdatedAt()
        );
    }
}
