package cn.superhuang.data.scalpel.business.mcp.web.response;

import java.time.Instant;
import java.util.UUID;

public record McpToolSummaryResponse(UUID id, UUID serverId, String code, String name, String description,
                                     boolean enabled, int sortOrder, long revision, Instant createdAt, Instant updatedAt) {}
