package cn.superhuang.data.scalpel.business.operations.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.search.SearchExcluded;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ops_notification", indexes = @Index(name = "idx_ops_notification_user", columnList = "user_id,created_at"))
public class InAppNotification extends BaseEntity {
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID incidentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertRuleType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertEventType eventType;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Column()
    private Instant readAt;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID value) { userId = value; }
    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(UUID value) { incidentId = value; }
    public AlertRuleType getRuleType() { return ruleType; }
    public void setRuleType(AlertRuleType value) { ruleType = value; }
    public AlertEventType getEventType() { return eventType; }
    public void setEventType(AlertEventType value) { eventType = value; }
    public String getSummary() { return summary; }
    public void setSummary(String value) { summary = value; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant value) { readAt = value; }
}
