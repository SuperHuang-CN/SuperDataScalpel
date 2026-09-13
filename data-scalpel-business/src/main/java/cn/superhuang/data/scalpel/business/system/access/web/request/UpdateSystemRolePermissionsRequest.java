package cn.superhuang.data.scalpel.business.system.access.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

@Schema(description = "整体替换自定义角色的权限集合；内置角色由系统维护，不能修改。")

public record UpdateSystemRolePermissionsRequest(
        @Schema(description = "替换后的完整有效权限 UUID 集合；空集合表示移除自定义角色的全部权限。任一 UUID 不存在或已失效时整次修改失败，内置角色不能提交。普通 JWT 重新登录后生效，系统 MCP 令牌下次请求生效。")
        @NotNull Set<UUID> permissionIds
) {
}
