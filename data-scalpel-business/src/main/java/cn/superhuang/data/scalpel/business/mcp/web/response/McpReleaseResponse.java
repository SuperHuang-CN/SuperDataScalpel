package cn.superhuang.data.scalpel.business.mcp.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.mcp.domain.McpServerRelease;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "原 MCP 平台 Server 的一个不可变发布快照及其冻结 Tool 定义。")

public record McpReleaseResponse(
        @Schema(description = "MCP Server 发布快照 UUID。")
        UUID id,
        @Schema(description = "该不可变发布快照所属的 MCP 服务器 UUID。")
        UUID serverId,
        @Schema(description = "所属 Server 内从 1 开始递增的发布版本号。")
        int version,
        @Schema(description = "MCP 服务器稳定编码。")
        String serverCode,
        @Schema(description = "MCP 服务器名称。")
        String serverName,
        @Schema(description = "发布时冻结并提供给智能体的 Server 整体使用说明。")
        String instructions,
        @Schema(description = "发布快照内容摘要，用于比较和验证服务器说明、工具 Schema 与脚本是否变化。")
        String digest,
        @Schema(description = "冻结在该发布快照中的已启用 Tool 数量。")
        int toolCount,
        @Schema(description = "生成该发布快照时服务器草稿的修订号。")
        long draftRevision,
        @Schema(description = "该不可变 Release 创建并成为活动版本的时间，ISO-8601 UTC 时间戳，始终有值。")
        Instant publishedAt,
        @Schema(description = "冻结在该发布版本中的工具快照，后续草稿修改不会改变这里的内容。")
        List<McpReleaseToolResponse> tools
) {
    public static McpReleaseResponse from(McpServerRelease release, List<McpReleaseToolResponse> tools) {
        return new McpReleaseResponse(release.getId(), release.getServerId(), release.getVersion(), release.getServerCode(),
                release.getServerName(), release.getInstructions(), release.getDigest(), release.getToolCount(), release.getDraftRevision(),
                release.getPublishedAt(), tools);
    }
}
