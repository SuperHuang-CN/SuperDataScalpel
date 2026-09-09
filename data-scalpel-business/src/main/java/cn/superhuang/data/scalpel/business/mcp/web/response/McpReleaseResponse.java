package cn.superhuang.data.scalpel.business.mcp.web.response;

import cn.superhuang.data.scalpel.business.mcp.domain.McpServerRelease;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record McpReleaseResponse(UUID id, UUID serverId, int version, String serverCode, String serverName,
        String instructions, String digest, int toolCount, long draftRevision, Instant publishedAt, List<McpReleaseToolResponse> tools) {
    public static McpReleaseResponse from(McpServerRelease release, List<McpReleaseToolResponse> tools) {
        return new McpReleaseResponse(release.getId(), release.getServerId(), release.getVersion(), release.getServerCode(),
                release.getServerName(), release.getInstructions(), release.getDigest(), release.getToolCount(), release.getDraftRevision(),
                release.getPublishedAt(), tools);
    }
}
