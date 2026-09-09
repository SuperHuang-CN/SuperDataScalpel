package cn.superhuang.data.scalpel.business.mcp.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import cn.superhuang.data.scalpel.search.SearchExcluded;

import java.time.Instant;

@Entity
@Table(name = "ds_mcp_access_token",
        uniqueConstraints = @UniqueConstraint(name = "uk_ds_mcp_access_token_digest", columnNames = "token_digest"),
        indexes = {
                @Index(name = "idx_ds_mcp_access_token_status", columnList = "status"),
                @Index(name = "idx_ds_mcp_access_token_last_used", columnList = "last_used_at")
        })
public class McpAccessToken extends BaseEntity {
    @Column(nullable = false, length = 100)
    private String name;
    @Column(length = 1000)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private McpAccessTokenStatus status;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "token_digest", nullable = false, length = 64)
    @SearchExcluded
    private String tokenDigest;
    @Column(name = "token_ciphertext", nullable = false, length = 512)
    @SearchExcluded
    private String tokenCiphertext;
    @Column(name = "token_hint", nullable = false, length = 24)
    private String tokenHint;
    @Column(nullable = false)
    private int revision;
    @Column(name = "rotated_at", nullable = false)
    private Instant rotatedAt;
    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected McpAccessToken() {}

    public static McpAccessToken create(String name, String description, Instant expiresAt,
                                        String digest, String ciphertext, String hint, Instant now) {
        McpAccessToken token = new McpAccessToken();
        token.name = name.trim();
        token.description = normalize(description);
        token.status = McpAccessTokenStatus.ENABLED;
        token.expiresAt = expiresAt;
        token.tokenDigest = digest;
        token.tokenCiphertext = ciphertext;
        token.tokenHint = hint;
        token.revision = 1;
        token.rotatedAt = now;
        return token;
    }

    public void update(String name, String description, Instant expiresAt) {
        this.name = name.trim();
        this.description = normalize(description);
        this.expiresAt = expiresAt;
    }

    public void enable() { status = McpAccessTokenStatus.ENABLED; }
    public void disable() { status = McpAccessTokenStatus.DISABLED; }

    public void rotate(String digest, String ciphertext, String hint, Instant now) {
        tokenDigest = digest;
        tokenCiphertext = ciphertext;
        tokenHint = hint;
        revision++;
        rotatedAt = now;
    }

    public boolean isUsableAt(Instant now) {
        return status == McpAccessTokenStatus.ENABLED && (expiresAt == null || expiresAt.isAfter(now));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public McpAccessTokenStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getTokenDigest() { return tokenDigest; }
    public String getTokenCiphertext() { return tokenCiphertext; }
    public String getTokenHint() { return tokenHint; }
    public int getRevision() { return revision; }
    public Instant getRotatedAt() { return rotatedAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
}
