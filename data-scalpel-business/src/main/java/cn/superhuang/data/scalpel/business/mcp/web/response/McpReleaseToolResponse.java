package cn.superhuang.data.scalpel.business.mcp.web.response;

import cn.superhuang.data.scalpel.business.mcp.domain.McpToolRelease;

import java.util.UUID;

public record McpReleaseToolResponse(UUID id, UUID sourceToolId, String code, String name, String description,
        String inputSchemaJson, String outputSchemaJson, String script, int sortOrder) {
    public static McpReleaseToolResponse from(McpToolRelease tool) {
        return new McpReleaseToolResponse(tool.getId(), tool.getSourceToolId(), tool.getCode(), tool.getName(),
                tool.getDescription(), tool.getInputSchemaJson(), tool.getOutputSchemaJson(), tool.getScript(), tool.getSortOrder());
    }
}
