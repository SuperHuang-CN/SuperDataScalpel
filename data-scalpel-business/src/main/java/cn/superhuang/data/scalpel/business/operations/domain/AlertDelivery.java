package cn.superhuang.data.scalpel.business.operations.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.search.SearchExcluded;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ops_alert_delivery", indexes = {@Index(name = "idx_ops_delivery_due", columnList = "status,next_attempt_at"), @Index(name = "idx_ops_delivery_incident", columnList = "incident_id,channel_id,sequence")})
public class AlertDelivery extends BaseEntity {
    @Column()
    private UUID incidentId;

    @Enumerated(EnumType.STRING)
    @Column()
    private AlertRuleType ruleType;

    @Column()
    private UUID subjectId;

    @Column(nullable = false)
    private UUID channelId;

    @Column(nullable = false)
    private long channelVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertEventType eventType;

    @Column(nullable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertDeliveryStatus status = AlertDeliveryStatus.PENDING;

    @SearchExcluded
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String payloadJson;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    @Column()
    private Instant leaseUntil;

    @Column()
    private UUID claimToken;

    @Column(nullable = false)
    private int attempts;

    @Column()
    private Integer httpStatus;

    @Column()
    private Long durationMillis;

    @Column(length = 300)
    private String lastError;

    @Column()
    private Instant sentAt;

    @Column()
    private UUID requestedBy;

    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(UUID value) { incidentId = value; }
    public AlertRuleType getRuleType() { return ruleType; }
    public void setRuleType(AlertRuleType value) { ruleType = value; }
    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID value) { subjectId = value; }
    public UUID getChannelId() { return channelId; }
    public void setChannelId(UUID value) { channelId = value; }
    public long getChannelVersion() { return channelVersion; }
    public void setChannelVersion(long value) { channelVersion = value; }
    public AlertEventType getEventType() { return eventType; }
    public void setEventType(AlertEventType value) { eventType = value; }
    public int getSequence() { return sequence; }
    public void setSequence(int value) { sequence = value; }
    public AlertDeliveryStatus getStatus() { return status; }
    public void setStatus(AlertDeliveryStatus value) { status = value; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String value) { payloadJson = value; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant value) { nextAttemptAt = value; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(Instant value) { leaseUntil = value; }
    public UUID getClaimToken() { return claimToken; }
    public void setClaimToken(UUID value) { claimToken = value; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int value) { attempts = value; }
    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer value) { httpStatus = value; }
    public Long getDurationMillis() { return durationMillis; }
    public void setDurationMillis(Long value) { durationMillis = value; }
    public String getLastError() { return lastError; }
    public void setLastError(String value) { lastError = value; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant value) { sentAt = value; }
    public UUID getRequestedBy() { return requestedBy; }
    public void setRequestedBy(UUID value) { requestedBy = value; }
}
