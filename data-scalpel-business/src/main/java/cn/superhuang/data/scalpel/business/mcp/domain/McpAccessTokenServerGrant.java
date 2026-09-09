package cn.superhuang.data.scalpel.business.mcp.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "ds_mcp_access_token_server_grant",
        uniqueConstraints = @UniqueConstraint(name = "uk_ds_mcp_token_server_grant", columnNames = {"access_token_id", "server_id"}),
        indexes = {
                @Index(name = "idx_ds_mcp_grant_token", columnList = "access_token_id"),
                @Index(name = "idx_ds_mcp_grant_server", columnList = "server_id")
        })
public class McpAccessTokenServerGrant extends BaseEntity {
    @Column(name = "access_token_id", nullable = false, updatable = false)
    private UUID accessTokenId;
    @Column(name = "server_id", nullable = false, updatable = false)
    private UUID serverId;

    protected McpAccessTokenServerGrant() {}

    public static McpAccessTokenServerGrant create(UUID accessTokenId, UUID serverId) {
        McpAccessTokenServerGrant grant = new McpAccessTokenServerGrant();
        grant.accessTokenId = accessTokenId;
        grant.serverId = serverId;
        return grant;
    }

    public UUID getAccessTokenId() { return accessTokenId; }
    public UUID getServerId() { return serverId; }
}
