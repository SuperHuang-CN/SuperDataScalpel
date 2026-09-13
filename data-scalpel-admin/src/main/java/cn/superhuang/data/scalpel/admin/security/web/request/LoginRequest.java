package cn.superhuang.data.scalpel.admin.security.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "使用后台系统用户名和密码换取当前登录用户的短期 JWT。")
public record LoginRequest(
        @Schema(description = "后台系统登录用户名；用户名创建后不可修改。")
        @NotBlank String username,
        @Schema(description = "该用户当前登录密码；仅用于本次认证，不会在响应或日志中返回。", format = "password")
        @NotBlank String password
) {
}
