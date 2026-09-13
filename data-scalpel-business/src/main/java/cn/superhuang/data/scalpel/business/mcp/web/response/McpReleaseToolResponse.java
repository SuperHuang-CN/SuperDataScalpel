package cn.superhuang.data.scalpel.business.mcp.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.mcp.domain.McpToolRelease;

import java.util.UUID;

@Schema(description = "MCP Server 发布快照中冻结的一份可执行 Tool 定义。")

public record McpReleaseToolResponse(
        @Schema(description = "发布 Tool 快照 UUID。")
        UUID id,
        @Schema(description = "发布时来源草稿工具的 UUID；来源工具删除后快照仍保留，此字段可能为空。")
        UUID sourceToolId,
        @Schema(description = "发布给 MCP 客户端的稳定 Tool 名称。")
        String code,
        @Schema(description = "发布时冻结的 Tool 显示名称。")
        String name,
        @Schema(description = "发布给 MCP 客户端和智能体的 Tool 用途说明。")
        String description,
        @Schema(description = "工具输入 JSON Schema 文本；必须是本地、受支持的 JSON Schema。")
        String inputSchemaJson,
        @Schema(description = "工具输出 JSON Schema 文本；为空表示不声明结构化输出。")
        String outputSchemaJson,
        @Schema(description = "发布时冻结、调用时按可信管理员代码执行的 Groovy 脚本；运行环境不提供安全沙箱。")
        String script,
        @Schema(description = "同级展示顺序，数值越小越靠前。")
        int sortOrder
) {
    public static McpReleaseToolResponse from(McpToolRelease tool) {
        return new McpReleaseToolResponse(tool.getId(), tool.getSourceToolId(), tool.getCode(), tool.getName(),
                tool.getDescription(), tool.getInputSchemaJson(), tool.getOutputSchemaJson(), tool.getScript(), tool.getSortOrder());
    }
}
