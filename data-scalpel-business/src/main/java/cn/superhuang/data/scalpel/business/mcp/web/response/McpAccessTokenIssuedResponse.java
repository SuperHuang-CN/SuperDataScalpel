package cn.superhuang.data.scalpel.business.mcp.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "原 MCP 平台访问令牌创建或轮换后的单次密钥展示结果。")

public record McpAccessTokenIssuedResponse(
        @Schema(description = "新签发令牌的元数据和授权数量。")
        McpAccessTokenResponse token,
        @Schema(description = "完整访问令牌；只在本次创建或轮换响应中返回，应立即安全保存。")
        String accessToken
) {}
