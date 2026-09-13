package cn.superhuang.data.scalpel.business.system.access.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.system.access.domain.SystemRole;
import cn.superhuang.data.scalpel.business.system.access.domain.SystemUser;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "系统用户及其当前绑定角色；不包含密码哈希。")

public record SystemUserResponse(
        @Schema(description = "用户 UUID。")
        UUID id,
        @Schema(description = "规范化为小写且创建后不可修改的登录用户名。")
        String username,
        @Schema(description = "用户显示名称。")
        String displayName,
        @Schema(description = "用户当前唯一绑定角色 UUID。")
        UUID roleId,
        @Schema(description = "用户所属角色名称。")
        String roleName,
        @Schema(description = "是否允许后续认证；false 时用户名密码登录和系统 MCP 令牌认证失败。已签发普通 JWT 不维护服务端黑名单，会保留至过期。")
        boolean enabled,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
    public static SystemUserResponse from(SystemUser user, SystemRole role) {
        return new SystemUserResponse(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getRoleId(), role.getName(), user.isEnabled(),
                user.getCreatedAt(), user.getUpdatedAt()
        );
    }
}
