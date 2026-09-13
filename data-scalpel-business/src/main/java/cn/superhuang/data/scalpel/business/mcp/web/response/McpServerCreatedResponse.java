package cn.superhuang.data.scalpel.business.mcp.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "原 MCP 平台 Server 创建结果及自动签发的初始访问令牌。")

public record McpServerCreatedResponse(
        @Schema(description = "新建 MCP 服务器的草稿状态和调用端点。")
        McpServerResponse server,
        @Schema(description = "允许访问新 Server 的完整初始令牌；仅本次响应返回，应立即安全保存。")
        String accessToken
) {}
