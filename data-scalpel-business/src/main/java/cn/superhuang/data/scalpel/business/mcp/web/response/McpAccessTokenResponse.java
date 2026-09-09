package cn.superhuang.data.scalpel.business.mcp.web.response;

import cn.superhuang.data.scalpel.business.mcp.domain.McpAccessToken;
import cn.superhuang.data.scalpel.business.mcp.domain.McpAccessTokenStatus;

import java.time.Instant;
import java.util.UUID;

public record McpAccessTokenResponse(UUID id, String name, String description, McpAccessTokenStatus status,
        Instant expiresAt, String hint, int revision, Instant rotatedAt, Instant lastUsedAt,
        long authorizedServerCount, Instant createdAt, Instant updatedAt) {
    public static McpAccessTokenResponse from(McpAccessToken token, long authorizedServerCount) {
        return new McpAccessTokenResponse(token.getId(), token.getName(), token.getDescription(), token.getStatus(),
                token.getExpiresAt(), token.getTokenHint(), token.getRevision(), token.getRotatedAt(), token.getLastUsedAt(),
                authorizedServerCount, token.getCreatedAt(), token.getUpdatedAt());
    }
}
