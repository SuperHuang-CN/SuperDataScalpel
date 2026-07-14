package cn.superhuang.data.scalpel.business.system.access.web.response;

import cn.superhuang.data.scalpel.business.system.access.domain.SystemRole;
import cn.superhuang.data.scalpel.business.system.access.domain.SystemUser;

import java.time.Instant;
import java.util.UUID;

public record SystemUserResponse(
        UUID id,
        String username,
        String displayName,
        UUID roleId,
        String roleName,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public static SystemUserResponse from(SystemUser user, SystemRole role) {
        return new SystemUserResponse(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getRoleId(), role.getName(), user.isEnabled(),
                user.getCreatedAt(), user.getUpdatedAt()
        );
    }
}
