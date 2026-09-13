package cn.superhuang.data.scalpel.business.system.access.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "创建系统登录用户并绑定一个现有角色。")

public record CreateSystemUserRequest(
        @Schema(description = "登录用户名；保存时去除首尾空白并转为小写，创建后不可修改。")
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.-]{2,63}", message = "用户名以字母开头，只能包含字母、数字、点、下划线和连字符")
        String username,
        @Schema(description = "用户显示名称，最长 100 字符；保存时去除首尾空白。")
        @NotBlank @Size(max = 100) String displayName,
        @Schema(description = "初始登录密码，仅用于本次创建；服务端保存哈希且响应不返回明文。", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(min = 8, max = 128) String password,
        @Schema(description = "要绑定的现有角色 UUID；一个用户同时只能属于一个角色。")
        @NotNull UUID roleId,
        @Schema(description = "是否允许登录；为空时默认 true。false 会阻止用户名密码登录和绑定该用户的系统 MCP 令牌认证。")
        Boolean enabled
) {
}
