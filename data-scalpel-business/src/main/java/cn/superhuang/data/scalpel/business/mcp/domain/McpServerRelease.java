package cn.superhuang.data.scalpel.business.mcp.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ds_mcp_server_release", indexes = @Index(name = "idx_ds_mcp_release_server", columnList = "server_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_ds_mcp_release_version", columnNames = {"server_id", "version"}))
public class McpServerRelease extends BaseEntity {
    @Column(name = "server_id", nullable = false, updatable = false)
    private UUID serverId;
    @Column(nullable = false, updatable = false)
    private int version;
    @Column(name = "server_code", nullable = false, length = 64, updatable = false)
    private String serverCode;
    @Column(name = "server_name", nullable = false, length = 100, updatable = false)
    private String serverName;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(updatable = false)
    private String instructions;
    @Column(nullable = false, length = 64, updatable = false)
    private String digest;
    @Column(name = "tool_count", nullable = false, updatable = false)
    private int toolCount;
    @Column(name = "draft_revision", nullable = false, updatable = false)
    private long draftRevision;
    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    protected McpServerRelease() {}
    public static McpServerRelease create(UUID serverId, int version, String serverCode, String serverName,
                                          String instructions, String digest, int toolCount, long draftRevision, Instant publishedAt) {
        McpServerRelease release = new McpServerRelease();
        release.serverId = serverId; release.version = version; release.serverCode = serverCode;
        release.serverName = serverName; release.instructions = instructions; release.digest = digest;
        release.toolCount = toolCount; release.draftRevision = draftRevision; release.publishedAt = publishedAt;
        return release;
    }
    public UUID getServerId() { return serverId; }
    public int getVersion() { return version; }
    public String getServerCode() { return serverCode; }
    public String getServerName() { return serverName; }
    public String getInstructions() { return instructions; }
    public String getDigest() { return digest; }
    public int getToolCount() { return toolCount; }
    public long getDraftRevision() { return draftRevision; }
    public Instant getPublishedAt() { return publishedAt; }
}
