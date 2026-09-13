package cn.superhuang.data.scalpel.business.mcp.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "把一个现有原 MCP 平台访问令牌授权给当前 MCP Server。")

public record McpAccessTokenGrantRequest(
        @Schema(description = "要授权访问当前 MCP Server 的平台访问令牌 UUID；不能为空。")
        @NotNull UUID accessTokenId
) {}
