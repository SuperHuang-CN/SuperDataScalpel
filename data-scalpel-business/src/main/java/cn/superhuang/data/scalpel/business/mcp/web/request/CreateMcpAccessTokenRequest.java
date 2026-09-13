package cn.superhuang.data.scalpel.business.mcp.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Schema(description = "创建原 MCP 在线开发平台的访问令牌，并限定可连接的 MCP Server。")

public record CreateMcpAccessTokenRequest(
        @Schema(description = "便于管理员识别令牌用途的显示名称。")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "用途说明；未填写时为空。")
        @Size(max = 1000) String description,
        @Schema(description = "过期时间，必须晚于创建时刻；为空表示不过期。")
        Instant expiresAt,
        @Schema(description = "该令牌获准访问的现有 MCP Server UUID 集合；可预先授权尚未发布或已停用的 Server，但只有 ENABLED Server 可实际调用；为空或空集合表示不允许访问任何 Server。")
        Set<@jakarta.validation.constraints.NotNull UUID> serverIds
) {}
