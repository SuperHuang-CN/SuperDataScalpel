package cn.superhuang.data.scalpel.business.mcp.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

@Schema(description = "修改原 MCP 平台访问令牌的显示信息和有效期，不改变密钥与 Server 授权。")

public record UpdateMcpAccessTokenRequest(
        @Schema(description = "便于管理员识别令牌用途的显示名称。")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "用途说明；未填写时为空。")
        @Size(max = 1000) String description,
        @Schema(description = "新的过期时间；为空表示不过期，设为当前或过去时间会使令牌在后续认证中立即不可用。")
        Instant expiresAt
) {}
