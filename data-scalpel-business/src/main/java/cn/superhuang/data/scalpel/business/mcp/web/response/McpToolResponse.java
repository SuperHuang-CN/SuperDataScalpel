package cn.superhuang.data.scalpel.business.mcp.web.response;

import cn.superhuang.data.scalpel.business.mcp.domain.McpTool;

import java.time.Instant;
import java.util.UUID;

public record McpToolResponse(UUID id, UUID serverId, String code, String name, String description,
        String inputSchemaJson, String outputSchemaJson, String script, String examplesJson,
        boolean enabled, int sortOrder, long revision, Instant createdAt, Instant updatedAt) {
    public static McpToolResponse from(McpTool tool) {
        return new McpToolResponse(tool.getId(), tool.getServerId(), tool.getCode(), tool.getName(), tool.getDescription(),
                tool.getInputSchemaJson(), tool.getOutputSchemaJson(), tool.getScript(), tool.getExamplesJson(),
                tool.isEnabled(), tool.getSortOrder(), tool.getRevision(), tool.getCreatedAt(), tool.getUpdatedAt());
    }
}
