package cn.superhuang.data.scalpel.business.systemmcp.domain;
import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;
@org.hibernate.annotations.DynamicUpdate
@Entity
@Table(name = "ds_system_mcp_access_token")
public class SystemMcpAccessToken extends BaseEntity {
    @Column(nullable = false, length = 100)
    private String name;
    @Column private Boolean managed;
    public boolean isManaged() { return Boolean.TRUE.equals(managed); }
    public void setManaged(boolean value) { managed=value; }
    @Column(nullable = false)
    private UUID userId;
    @Column(nullable = false, unique = true, length = 64)
    private String tokenDigest;
    @Column(nullable = false)
    private boolean enabled;
    @Column(nullable = false)
    private long revision;
    @Column
    private Instant expiresAt;
    @Column
    private Instant lastUsedAt;
    public SystemMcpAccessToken() {
    }
    public String getName() {
        return name;
    }
    public void setName(String value) {
        name = value;
    }
    public UUID getUserId() {
        return userId;
    }
    public void setUserId(UUID value) {
        userId = value;
    }
    public String getTokenDigest() {
        return tokenDigest;
    }
    public void setTokenDigest(String value) {
        tokenDigest = value;
    }
    public boolean getEnabled() {
        return enabled;
    }
    public void setEnabled(boolean value) {
        enabled = value;
    }
    public long getRevision() {
        return revision;
    }
    public void setRevision(long value) {
        revision = value;
    }
    public Instant getExpiresAt() {
        return expiresAt;
    }
    public void setExpiresAt(Instant value) {
        expiresAt = value;
    }
    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
    public void setLastUsedAt(Instant value) {
        lastUsedAt = value;
    }
}
