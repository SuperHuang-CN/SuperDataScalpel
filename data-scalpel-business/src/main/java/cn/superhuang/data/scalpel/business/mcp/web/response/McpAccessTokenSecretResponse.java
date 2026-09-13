package cn.superhuang.data.scalpel.business.mcp.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "按需解密读取的原 MCP 平台当前完整访问令牌。")

public record McpAccessTokenSecretResponse(
        @Schema(description = "当前完整访问令牌；响应禁止缓存，应按秘密信息处理。")
        String accessToken
) {}
