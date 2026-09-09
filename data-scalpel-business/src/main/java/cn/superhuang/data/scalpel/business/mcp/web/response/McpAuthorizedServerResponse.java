package cn.superhuang.data.scalpel.business.mcp.web.response;

import cn.superhuang.data.scalpel.business.mcp.domain.McpServer;
import cn.superhuang.data.scalpel.business.mcp.domain.McpServerStatus;

import java.util.UUID;

public record McpAuthorizedServerResponse(UUID id, String code, String name, McpServerStatus status) {
    public static McpAuthorizedServerResponse from(McpServer server) {
        return new McpAuthorizedServerResponse(server.getId(), server.getCode(), server.getName(), server.getStatus());
    }
}
