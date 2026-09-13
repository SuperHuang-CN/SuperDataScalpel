package cn.superhuang.data.scalpel.business.system.access.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.system.access.domain.SystemRole;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Schema(description = "系统角色及其当前权限集合。")

public record SystemRoleResponse(
        @Schema(description = "角色 UUID。")
        UUID id,
        @Schema(description = "创建时规范化为小写且不可修改的角色稳定编码；用于 ROLE_ 授权以及 super_admin 等角色级限制。")
        String code,
        @Schema(description = "角色显示名称。")
        String name,
        @Schema(description = "角色职责说明；未填写时为空。")
        String description,
        @Schema(description = "是否为系统内置角色；内置 super_admin 不能删除或手工修改权限，应用启动时为其同步全部 active 权限。")
        boolean builtIn,
        @Schema(description = "角色当前保存的权限 UUID 映射集合；实际授权只采用其中 active=true 的权限。自定义角色通过权限更新接口整体替换该集合。")
        Set<UUID> permissionIds,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
    public static SystemRoleResponse from(SystemRole role, Set<UUID> permissionIds) {
        return new SystemRoleResponse(
                role.getId(), role.getCode(), role.getName(), role.getDescription(), role.isBuiltIn(), Set.copyOf(permissionIds),
                role.getCreatedAt(), role.getUpdatedAt()
        );
    }
}
