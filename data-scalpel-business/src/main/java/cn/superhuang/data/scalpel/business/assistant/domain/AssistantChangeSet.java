package cn.superhuang.data.scalpel.business.assistant.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "ai_assistant_change_set",
        indexes = {
                @Index(name = "idx_ai_change_session", columnList = "session_id,status,created_at"),
                @Index(name = "idx_ai_change_run", columnList = "run_id")
        }
)
public class AssistantChangeSet extends BaseEntity {

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @Column(name = "owner_username", nullable = false, updatable = false, length = 100)
    private String ownerUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, updatable = false, length = 24)
    private AssistantChangeSetType changeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AssistantChangeSetStatus status;

    @Column(nullable = false, length = 500)
    private String summary;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "payload_json", nullable = false, updatable = false)
    private String payloadJson;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "result_json")
    private String resultJson;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "failure_summary", length = 1000)
    private String failureSummary;

    protected AssistantChangeSet() {
    }

    private AssistantChangeSet(
            UUID sessionId,
            UUID runId,
            String ownerUsername,
            AssistantChangeSetType changeType,
            String summary,
            String payloadJson
    ) {
        this.sessionId = java.util.Objects.requireNonNull(sessionId);
        this.runId = java.util.Objects.requireNonNull(runId);
        this.ownerUsername = ownerUsername;
        this.changeType = changeType;
        this.summary = summary;
        this.payloadJson = payloadJson;
        status = AssistantChangeSetStatus.PENDING;
    }

    public static AssistantChangeSet create(
            UUID sessionId,
            UUID runId,
            String ownerUsername,
            AssistantChangeSetType changeType,
            String summary,
            String payloadJson
    ) {
        return new AssistantChangeSet(sessionId, runId, ownerUsername, changeType, summary, payloadJson);
    }

    public void supersede() {
        if (status == AssistantChangeSetStatus.PENDING) status = AssistantChangeSetStatus.SUPERSEDED;
    }

    public void reject() {
        if (status == AssistantChangeSetStatus.PENDING) status = AssistantChangeSetStatus.REJECTED;
    }

    public void markStale(String reason) {
        status = AssistantChangeSetStatus.STALE;
        failureSummary = truncate(reason, 1000);
    }

    public void apply(String username, String resultJson, Instant now) {
        status = AssistantChangeSetStatus.APPLIED;
        approvedBy = username;
        approvedAt = now;
        executedAt = now;
        this.resultJson = resultJson;
        failureSummary = null;
    }

    public void fail(String username, String reason, Instant now) {
        status = AssistantChangeSetStatus.FAILED;
        approvedBy = username;
        approvedAt = now;
        executedAt = now;
        failureSummary = truncate(reason, 1000);
    }

    private static String truncate(String value, int length) {
        if (value == null) return null;
        return value.length() <= length ? value : value.substring(0, length);
    }

    public UUID getSessionId() { return sessionId; }
    public UUID getRunId() { return runId; }
    public String getOwnerUsername() { return ownerUsername; }
    public AssistantChangeSetType getChangeType() { return changeType; }
    public AssistantChangeSetStatus getStatus() { return status; }
    public String getSummary() { return summary; }
    public String getPayloadJson() { return payloadJson; }
    public String getResultJson() { return resultJson; }
    public String getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getExecutedAt() { return executedAt; }
    public String getFailureSummary() { return failureSummary; }
}
