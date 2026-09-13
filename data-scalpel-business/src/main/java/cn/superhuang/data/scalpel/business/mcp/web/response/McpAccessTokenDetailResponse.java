package cn.superhuang.data.scalpel.business.mcp.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "原 MCP 平台访问令牌的非敏感元数据及已授权 Server 列表。")

public record McpAccessTokenDetailResponse(
        @Schema(description = "访问令牌的元数据、状态、有效期和最近使用情况，不包含可用秘密。")
        McpAccessTokenResponse token,
        @Schema(description = "该令牌允许访问的 MCP 服务器；列表为空时不能调用任何服务器。")
        List<McpAuthorizedServerResponse> authorizedServers
) {}
