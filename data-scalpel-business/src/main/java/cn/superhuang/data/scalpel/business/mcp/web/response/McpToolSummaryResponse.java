package cn.superhuang.data.scalpel.business.mcp.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "MCP Server 工具列表使用的 Tool 草稿摘要，不包含 Schema 和脚本正文。")

public record McpToolSummaryResponse(
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
) {}
