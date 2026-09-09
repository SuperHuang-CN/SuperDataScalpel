package cn.superhuang.data.scalpel.business.mcp.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ds_mcp_server", uniqueConstraints = @UniqueConstraint(name = "uk_ds_mcp_server_code", columnNames = "code"))
public class McpServer extends BaseEntity {

    @Column(nullable = false, length = 64, updatable = false)
    private String code;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "directory_id")
    private UUID directoryId;
    @Column(length = 1000)
    private String description;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String instructions;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private McpServerStatus status;
    @Column(name = "draft_revision", nullable = false)
    private long draftRevision;
    @Column(name = "published_version")
    private Integer publishedVersion;
    @Column(name = "published_draft_revision")
    private Long publishedDraftRevision;
    @Column(name = "active_release_id")
    private UUID activeReleaseId;
    @Column(name = "last_published_at")
    private Instant lastPublishedAt;

    protected McpServer() {}

    public static McpServer create(String code, String name, UUID directoryId, String description, String instructions) {
        McpServer server = new McpServer();
        server.code = normalizeCode(code);
        server.status = McpServerStatus.DRAFT;
        server.draftRevision = 1;
        server.apply(name, directoryId, description, instructions);
        return server;
    }

    public void update(String name, UUID directoryId, String description, String instructions) {
        String nextName = required(name, "名称");
        String nextDescription = optional(description);
        String nextInstructions = optional(instructions);
        if (!Objects.equals(this.name, nextName) || !Objects.equals(this.directoryId, directoryId)
                || !Objects.equals(this.description, nextDescription) || !Objects.equals(this.instructions, nextInstructions)) {
            apply(nextName, directoryId, nextDescription, nextInstructions);
            draftRevision++;
        }
    }

    private void apply(String name, UUID directoryId, String description, String instructions) {
        this.name = required(name, "名称");
        this.directoryId = directoryId;
        this.description = optional(description);
        this.instructions = optional(instructions);
    }

    public void touchDraft() { draftRevision++; }
    public void publish(UUID releaseId, int version, Instant publishedAt) {
        activeReleaseId = releaseId;
        publishedVersion = version;
        publishedDraftRevision = draftRevision;
        lastPublishedAt = publishedAt;
        status = McpServerStatus.ENABLED;
    }
    public void enable() {
        if (activeReleaseId == null) throw new IllegalStateException("MCP Server 尚未发布");
        status = McpServerStatus.ENABLED;
    }
    public void disable() { status = McpServerStatus.DISABLED; }

    private static String normalizeCode(String value) {
        String normalized = required(value, "编码").toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9_-]{1,63}")) throw new IllegalArgumentException("编码必须以字母开头，仅包含小写字母、数字、下划线或连字符");
        return normalized;
    }
    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        return value.trim();
    }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public String getCode() { return code; }
    public String getName() { return name; }
    public UUID getDirectoryId() { return directoryId; }
    public String getDescription() { return description; }
    public String getInstructions() { return instructions; }
    public McpServerStatus getStatus() { return status; }
    public long getDraftRevision() { return draftRevision; }
    public Integer getPublishedVersion() { return publishedVersion; }
    public Long getPublishedDraftRevision() { return publishedDraftRevision; }
    public UUID getActiveReleaseId() { return activeReleaseId; }
    public Instant getLastPublishedAt() { return lastPublishedAt; }
}
