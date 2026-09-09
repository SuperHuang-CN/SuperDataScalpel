package cn.superhuang.data.scalpel.business.operations.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.search.SearchExcluded;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ops_alert_signal", indexes = @Index(name = "idx_ops_signal_due", columnList = "status,next_attempt_at"))
public class AlertSignal extends BaseEntity {
    @Column(unique = true, nullable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertRuleType ruleType;

    @SearchExcluded
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String factsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertSignalStatus status = AlertSignalStatus.PENDING;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    @Column(nullable = false)
    private int attempts;

    @Column(length = 300)
    private String lastError;

    public UUID getRunId() { return runId; }
    public void setRunId(UUID value) { runId = value; }
    public AlertRuleType getRuleType() { return ruleType; }
    public void setRuleType(AlertRuleType value) { ruleType = value; }
    public String getFactsJson() { return factsJson; }
    public void setFactsJson(String value) { factsJson = value; }
    public AlertSignalStatus getStatus() { return status; }
    public void setStatus(AlertSignalStatus value) { status = value; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant value) { nextAttemptAt = value; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int value) { attempts = value; }
    public String getLastError() { return lastError; }
    public void setLastError(String value) { lastError = value; }
}
