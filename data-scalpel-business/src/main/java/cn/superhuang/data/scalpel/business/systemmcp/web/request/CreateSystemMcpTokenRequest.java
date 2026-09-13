package cn.superhuang.data.scalpel.business.systemmcp.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "创建绑定系统用户的系统 MCP 专用访问令牌")
public record CreateSystemMcpTokenRequest(
        @Schema(description = "令牌名称，用于区分客户端或使用场景")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "绑定用户 UUID；每次调用实时使用该用户当前状态、角色和权限")
        @NotNull UUID userId,
        @Schema(description = "令牌过期时间，ISO-8601 UTC 时间；必须晚于当前时间，为空表示不过期")
        Instant expiresAt
) {
}
