package cn.superhuang.data.scalpel.business.mcp.web.response;

import cn.superhuang.data.scalpel.business.mcp.domain.McpServer;
import cn.superhuang.data.scalpel.business.mcp.domain.McpServerStatus;

import java.time.Instant;
import java.util.UUID;

public record McpServerResponse(UUID id, String code, String name, UUID directoryId, String description,
        String instructions, McpServerStatus status, long draftRevision, Integer publishedVersion,
        UUID activeReleaseId, Instant lastPublishedAt, long toolCount, boolean unpublishedChanges,
        String endpoint, Instant createdAt, Instant updatedAt) {
    public static McpServerResponse from(McpServer server, long toolCount, boolean unpublishedChanges, String endpoint) {
        return new McpServerResponse(server.getId(), server.getCode(), server.getName(), server.getDirectoryId(),
                server.getDescription(), server.getInstructions(), server.getStatus(), server.getDraftRevision(),
                server.getPublishedVersion(), server.getActiveReleaseId(), server.getLastPublishedAt(), toolCount,
                unpublishedChanges, endpoint, server.getCreatedAt(), server.getUpdatedAt());
    }
}
