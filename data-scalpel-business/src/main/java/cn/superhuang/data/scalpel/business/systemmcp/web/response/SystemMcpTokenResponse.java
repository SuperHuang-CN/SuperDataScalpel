package cn.superhuang.data.scalpel.business.systemmcp.web.response;
public record SystemMcpTokenResponse(java.util.UUID id, String name, java.util.UUID userId, String username, boolean enabled, long revision, java.time.Instant expiresAt, java.time.Instant lastUsedAt, java.time.Instant createdAt) {
}
