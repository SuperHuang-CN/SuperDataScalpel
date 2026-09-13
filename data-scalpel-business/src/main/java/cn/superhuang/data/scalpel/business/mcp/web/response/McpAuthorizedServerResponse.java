package cn.superhuang.data.scalpel.business.mcp.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.mcp.domain.McpServer;
import cn.superhuang.data.scalpel.business.mcp.domain.McpServerStatus;

import java.util.UUID;

@Schema(description = "原 MCP 平台 Server 的授权选择或已授权摘要；是否已经授权由所在响应语境决定。")

public record McpAuthorizedServerResponse(
        @Schema(description = "MCP 服务 UUID。")
        UUID id,
        @Schema(description = "MCP Server 稳定技术编码。")
        String code,
        @Schema(description = "MCP Server 显示名称。")
        String name,
        @Schema(description = "服务器状态：DRAFT 尚未发布，ENABLED 已发布可调用，DISABLED 已停用。")
        McpServerStatus status
) {
    public static McpAuthorizedServerResponse from(McpServer server) {
        return new McpAuthorizedServerResponse(server.getId(), server.getCode(), server.getName(), server.getStatus());
    }
}
