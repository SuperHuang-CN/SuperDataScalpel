package cn.superhuang.data.scalpel.business.operations.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.search.SearchExcluded;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ops_alert_action", indexes = @Index(name = "idx_ops_action_incident", columnList = "incident_id,created_at"))
public class AlertAction extends BaseEntity {
    @Column(nullable = false)
    private UUID incidentId;

    @Column(nullable = false, length = 32)
    private String action;

    @Column()
    private UUID actorId;

    @Column(length = 150)
    private String actorName;

    @Column(length = 1000)
    private String reason;

    @Column()
    private Instant untilAt;

    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(UUID value) { incidentId = value; }
    public String getAction() { return action; }
    public void setAction(String value) { action = value; }
    public UUID getActorId() { return actorId; }
    public void setActorId(UUID value) { actorId = value; }
    public String getActorName() { return actorName; }
    public void setActorName(String value) { actorName = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public Instant getUntilAt() { return untilAt; }
    public void setUntilAt(Instant value) { untilAt = value; }
}
