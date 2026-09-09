package cn.superhuang.data.scalpel.business.operations.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.search.SearchExcluded;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ops_alert_incident", indexes = {@Index(name = "idx_ops_incident_state_time", columnList = "status,occurred_at"), @Index(name = "idx_ops_incident_evaluate", columnList = "status,last_evaluated_at")})
public class AlertIncident extends BaseEntity {
    @Column(unique = true, length = 100)
    private String eventKey;

    @Column(unique = true, length = 100)
    private String activeKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertRuleType ruleType;

    @Column(nullable = false)
    private UUID ruleId;

    @Column(nullable = false)
    private long ruleVersion;

    @Column(nullable = false)
    private UUID subjectId;

    @Column(nullable = false, length = 300)
    private String subjectName;

    @Column()
    private UUID runId;

    @Column()
    private UUID engineId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertHandlingStatus status = AlertHandlingStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertConditionState conditionState = AlertConditionState.TRIGGERED;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Column(length = 100)
    private String errorCode;

    @Column()
    private UUID diagnosticId;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private Instant detectedAt;

    @Column()
    private Instant lastObservedAt;

    @Column()
    private Instant closedAt;

    @Column(length = 1000)
    private String closeReason;

    @SearchExcluded
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String ruleSnapshotJson;

    @Column()
    private Instant lastSilenceReminderAt;

    @Column(nullable = false)
    private Instant lastEvaluatedAt;

    public String getEventKey() { return eventKey; }
    public void setEventKey(String value) { eventKey = value; }
    public String getActiveKey() { return activeKey; }
    public void setActiveKey(String value) { activeKey = value; }
    public AlertRuleType getRuleType() { return ruleType; }
    public void setRuleType(AlertRuleType value) { ruleType = value; }
    public UUID getRuleId() { return ruleId; }
    public void setRuleId(UUID value) { ruleId = value; }
    public long getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(long value) { ruleVersion = value; }
    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID value) { subjectId = value; }
    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String value) { subjectName = value; }
    public UUID getRunId() { return runId; }
    public void setRunId(UUID value) { runId = value; }
    public UUID getEngineId() { return engineId; }
    public void setEngineId(UUID value) { engineId = value; }
    public AlertSeverity getSeverity() { return severity; }
    public void setSeverity(AlertSeverity value) { severity = value; }
    public AlertHandlingStatus getStatus() { return status; }
    public void setStatus(AlertHandlingStatus value) { status = value; }
    public AlertConditionState getConditionState() { return conditionState; }
    public void setConditionState(AlertConditionState value) { conditionState = value; }
    public String getSummary() { return summary; }
    public void setSummary(String value) { summary = value; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String value) { errorCode = value; }
    public UUID getDiagnosticId() { return diagnosticId; }
    public void setDiagnosticId(UUID value) { diagnosticId = value; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant value) { occurredAt = value; }
    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant value) { detectedAt = value; }
    public Instant getLastObservedAt() { return lastObservedAt; }
    public void setLastObservedAt(Instant value) { lastObservedAt = value; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant value) { closedAt = value; }
    public String getCloseReason() { return closeReason; }
    public void setCloseReason(String value) { closeReason = value; }
    public String getRuleSnapshotJson() { return ruleSnapshotJson; }
    public void setRuleSnapshotJson(String value) { ruleSnapshotJson = value; }
    public Instant getLastSilenceReminderAt() { return lastSilenceReminderAt; }
    public void setLastSilenceReminderAt(Instant value) { lastSilenceReminderAt = value; }
    public Instant getLastEvaluatedAt() { return lastEvaluatedAt; }
    public void setLastEvaluatedAt(Instant value) { lastEvaluatedAt = value; }
}
