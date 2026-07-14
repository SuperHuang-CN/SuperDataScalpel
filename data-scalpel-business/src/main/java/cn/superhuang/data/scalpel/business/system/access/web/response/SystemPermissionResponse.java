package cn.superhuang.data.scalpel.business.system.access.web.response;

import cn.superhuang.data.scalpel.business.system.access.domain.SystemPermission;

import java.time.Instant;
import java.util.UUID;

public record SystemPermissionResponse(
        UUID id,
        String code,
        String module,
        String name,
        String description,
        int sortOrder,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static SystemPermissionResponse from(SystemPermission permission) {
        return new SystemPermissionResponse(
                permission.getId(), permission.getCode(), permission.getModule(), permission.getName(), permission.getDescription(),
                permission.getSortOrder(), permission.isActive(), permission.getCreatedAt(), permission.getUpdatedAt()
        );
    }
}
