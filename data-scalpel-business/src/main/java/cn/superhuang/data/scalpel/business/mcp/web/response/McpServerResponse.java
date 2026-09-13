package cn.superhuang.data.scalpel.business.mcp.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.mcp.domain.McpServer;
import cn.superhuang.data.scalpel.business.mcp.domain.McpServerStatus;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "原 MCP 在线开发平台 Server 的草稿、发布状态、调用端点和 Tool 数量。")

public record McpServerResponse(
        @Schema(description = "当前 MCP 服务的唯一 UUID。")
        UUID id,
        @Schema(description = "MCP Server 稳定技术编码，创建后不可修改。")
        String code,
        @Schema(description = "MCP Server 显示名称。")
        String name,
        @Schema(description = "所属目录 UUID；位于根目录时为空。")
        UUID directoryId,
        @Schema(description = "用途说明；未填写时为空。")
        String description,
        @Schema(description = "当前草稿中提供给智能体的 Server 整体使用说明。")
        String instructions,
        @Schema(description = "服务器状态：DRAFT 尚未发布，ENABLED 已发布可调用，DISABLED 已停用。")
        McpServerStatus status,
        @Schema(description = "Server 名称、目录、说明、instructions 或任一 Tool 草稿内容实际变化后递增的整体草稿修订号，从 1 开始。")
        long draftRevision,
        @Schema(description = "当前已发布版本号；尚未发布时为空。")
        Integer publishedVersion,
        @Schema(description = "当前协议端点实际提供的发布快照 UUID；尚未发布时为空。")
        UUID activeReleaseId,
        @Schema(description = "最近已发布时间；尚未发生时为空。")
        Instant lastPublishedAt,
        @Schema(description = "当前服务器草稿中的工具总数，包含已停用工具。")
        long toolCount,
        @Schema(description = "当前草稿是否尚未形成活动发布快照；从未发布或草稿修订与最近发布时不同均为 true。端点始终使用 activeReleaseId 指向的不可变快照，不读取这些未发布修改。")
        boolean unpublishedChanges,
        @Schema(description = "该服务器的 MCP Streamable HTTP 调用地址。")
        String endpoint,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
    public static McpServerResponse from(McpServer server, long toolCount, boolean unpublishedChanges, String endpoint) {
        return new McpServerResponse(server.getId(), server.getCode(), server.getName(), server.getDirectoryId(),
                server.getDescription(), server.getInstructions(), server.getStatus(), server.getDraftRevision(),
                server.getPublishedVersion(), server.getActiveReleaseId(), server.getLastPublishedAt(), toolCount,
                unpublishedChanges, endpoint, server.getCreatedAt(), server.getUpdatedAt());
    }
}
