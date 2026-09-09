package cn.superhuang.data.scalpel.business.operations.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.search.SearchExcluded;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ops_alert_cooldown")
public class AlertCooldown extends BaseEntity {
    @Column(unique = true, nullable = false, length = 160)
    private String scopeKey;

    @Column(nullable = false)
    private Instant nextAllowedAt;

    @Column(nullable = false)
    private long suppressedCount;

    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String value) { scopeKey = value; }
    public Instant getNextAllowedAt() { return nextAllowedAt; }
    public void setNextAllowedAt(Instant value) { nextAllowedAt = value; }
    public long getSuppressedCount() { return suppressedCount; }
    public void setSuppressedCount(long value) { suppressedCount = value; }
}
