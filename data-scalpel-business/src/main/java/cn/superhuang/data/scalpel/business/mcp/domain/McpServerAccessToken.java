package cn.superhuang.data.scalpel.business.mcp.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ds_mcp_server_access_token", uniqueConstraints = @UniqueConstraint(name = "uk_ds_mcp_token_server", columnNames = "server_id"))
public class McpServerAccessToken extends BaseEntity {
    @Column(name = "server_id", nullable = false, updatable = false)
    private UUID serverId;
    @Column(name = "token_digest", nullable = false, length = 64)
    private String tokenDigest;
    @Column(name = "token_hint", nullable = false, length = 24)
    private String tokenHint;
    @Column(nullable = false)
    private int revision;
    @Column(name = "rotated_at", nullable = false)
    private Instant rotatedAt;

    protected McpServerAccessToken() {}
    public static McpServerAccessToken create(UUID serverId, String digest, String hint, Instant now) {
        McpServerAccessToken token = new McpServerAccessToken();
        token.serverId = serverId; token.tokenDigest = digest; token.tokenHint = hint; token.revision = 1; token.rotatedAt = now;
        return token;
    }
    public void rotate(String digest, String hint, Instant now) { tokenDigest = digest; tokenHint = hint; revision++; rotatedAt = now; }
    public UUID getServerId() { return serverId; }
    public String getTokenDigest() { return tokenDigest; }
    public String getTokenHint() { return tokenHint; }
    public int getRevision() { return revision; }
    public Instant getRotatedAt() { return rotatedAt; }
}
