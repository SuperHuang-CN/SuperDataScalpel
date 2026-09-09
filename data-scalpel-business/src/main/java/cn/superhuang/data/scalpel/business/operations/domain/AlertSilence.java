package cn.superhuang.data.scalpel.business.operations.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.search.SearchExcluded;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ops_alert_silence")
public class AlertSilence extends BaseEntity {
    @Column(unique = true, nullable = false, length = 100)
    private String scopeKey;

    @Column(nullable = false)
    private Instant untilAt;

    private Instant reminderSentAt;

    @Column(nullable = false)
    private UUID actorId;

    @Column(nullable = false, length = 1000)
    private String reason;

    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String value) { scopeKey = value; }
    public Instant getUntilAt() { return untilAt; }
    public void setUntilAt(Instant value) { untilAt = value; }
    public Instant getReminderSentAt() { return reminderSentAt; }
    public void setReminderSentAt(Instant value) { reminderSentAt = value; }
    public UUID getActorId() { return actorId; }
    public void setActorId(UUID value) { actorId = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
}
