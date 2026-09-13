package cn.superhuang.data.scalpel.business.system.access.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "修改用户显示名、角色和启用状态；用户名与密码不由此接口改变。")

public record UpdateSystemUserRequest(
        @Schema(description = "新的用户显示名称，最长 100 字符；保存时去除首尾空白。")
        @NotBlank @Size(max = 100) String displayName,
        @Schema(description = "新的现有角色 UUID；替换用户当前唯一角色，不叠加多角色。普通 JWT 要重新登录才取得新权限，系统 MCP 令牌下次请求即读取新角色。")
        @NotNull UUID roleId,
        @Schema(description = "是否允许后续认证；当前登录用户不能通过此接口停用自己。停用不会建立普通 JWT 黑名单，但绑定该用户的系统 MCP 令牌下次请求立即失效。")
        @NotNull Boolean enabled
) {
}
