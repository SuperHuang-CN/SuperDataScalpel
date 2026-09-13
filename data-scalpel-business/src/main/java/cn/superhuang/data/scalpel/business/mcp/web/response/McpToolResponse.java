package cn.superhuang.data.scalpel.business.mcp.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.mcp.domain.McpTool;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "原 MCP 在线开发平台中的完整 Tool 草稿、Schema、脚本和修订号。")

public record McpToolResponse(
        @Schema(description = "MCP Tool 草稿 UUID。")
        UUID id,
        @Schema(description = "该草稿工具所属的 MCP 服务器 UUID。")
        UUID serverId,
        @Schema(description = "Tool 在所属 Server 内稳定唯一的协议名称。")
        String code,
        @Schema(description = "供管理页面显示的 Tool 名称。")
        String name,
        @Schema(description = "提供给 MCP 客户端和智能体的 Tool 用途说明。")
        String description,
        @Schema(description = "工具输入 JSON Schema 文本；必须是本地、受支持的 JSON Schema。")
        String inputSchemaJson,
        @Schema(description = "工具输出 JSON Schema 文本；为空表示不声明结构化输出。")
        String outputSchemaJson,
        @Schema(description = "发布后调用时执行的 Groovy Tool 脚本；按可信管理员代码运行，不提供安全沙箱。")
        String script,
        @Schema(description = "工具调用示例 JSON 文本。")
        String examplesJson,
        @Schema(description = "是否启用；false 时不参与后续执行。")
        boolean enabled,
        @Schema(description = "同级展示顺序，数值越小越靠前。")
        int sortOrder,
        @Schema(description = "工具草稿的并发控制修订号；修改时必须提交最新值。")
        long revision,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
    public static McpToolResponse from(McpTool tool) {
        return new McpToolResponse(tool.getId(), tool.getServerId(), tool.getCode(), tool.getName(), tool.getDescription(),
                tool.getInputSchemaJson(), tool.getOutputSchemaJson(), tool.getScript(), tool.getExamplesJson(),
                tool.isEnabled(), tool.getSortOrder(), tool.getRevision(), tool.getCreatedAt(), tool.getUpdatedAt());
    }
}
