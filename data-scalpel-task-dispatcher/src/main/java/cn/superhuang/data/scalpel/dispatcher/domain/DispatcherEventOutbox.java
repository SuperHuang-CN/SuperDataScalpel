package cn.superhuang.data.scalpel.dispatcher.domain;

import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionEvent;
import cn.superhuang.data.scalpel.contract.execution.StopStreamingExecutionCommand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dispatcher_event_outbox", uniqueConstraints = {
        @UniqueConstraint(name = "uk_dispatcher_outbox_message", columnNames = "message_id")
}, indexes = {
        @Index(name = "idx_dispatcher_outbox_due", columnList = "state,next_attempt_at")
})
public class DispatcherEventOutbox extends DispatcherBaseEntity {
    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;
    @Column(name = "execution_id", nullable = false, updatable = false)
    private UUID executionId;
    @Column(name = "engine_id", nullable = false, updatable = false)
    private UUID engineId;
    @Column(nullable = false, updatable = false)
    private long sequence;
    @Column(nullable = false, length = 249, updatable = false)
    private String topic;
    @Column(name = "message_type", nullable = false, length = 64, updatable = false)
    private String messageType;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false, updatable = false)
    private String payload;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DispatcherOutboxState state;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "claimed_at")
    private Instant claimedAt;
    @Column(name = "last_error", length = 1000)
    private String lastError;

    protected DispatcherEventOutbox() {
    }

    public static DispatcherEventOutbox pending(String topic, DispatcherExecutionEvent event, String payload) {
        DispatcherEventOutbox outbox = new DispatcherEventOutbox();
        outbox.messageId = event.messageId();
        outbox.executionId = event.executionId();
        outbox.engineId = event.engineId();
        outbox.sequence = event.sequence();
        outbox.topic = topic;
        outbox.messageType = event.messageType().name();
        outbox.payload = payload;
        outbox.state = DispatcherOutboxState.PENDING;
        outbox.nextAttemptAt = Instant.now();
        return outbox;
    }

    public static DispatcherEventOutbox pendingControl(
            String topic,
            StopStreamingExecutionCommand command,
            String payload
    ) {
        DispatcherEventOutbox outbox = new DispatcherEventOutbox();
        outbox.messageId = command.messageId();
        outbox.executionId = command.executionId();
        outbox.engineId = command.engineId();
        outbox.sequence = 0;
        outbox.topic = topic;
        outbox.messageType = command.messageType().name();
        outbox.payload = payload;
        outbox.state = DispatcherOutboxState.PENDING;
        outbox.nextAttemptAt = Instant.now();
        return outbox;
    }

    public void publishing(Instant now) {
        if (state != DispatcherOutboxState.PENDING && state != DispatcherOutboxState.FAILED) return;
        state = DispatcherOutboxState.PUBLISHING;
        claimedAt = now;
        attempts++;
    }

    public void published() {
        state = DispatcherOutboxState.PUBLISHED;
        publishedAt = Instant.now();
        claimedAt = null;
        lastError = null;
    }

    public void failed(String error) {
        state = DispatcherOutboxState.FAILED;
        claimedAt = null;
        nextAttemptAt = Instant.now().plusSeconds(Math.min(300, 1L << Math.min(8, Math.max(0, attempts - 1))));
        String safe = error == null || error.isBlank() ? "Kafka 发布失败" : error.trim();
        lastError = safe.substring(0, Math.min(1000, safe.length()));
    }

    public void recoverStaleClaim(Instant now, java.time.Duration timeout) {
        if (state == DispatcherOutboxState.PUBLISHING && claimedAt != null
                && claimedAt.plus(timeout).isBefore(now)) {
            state = DispatcherOutboxState.FAILED;
            claimedAt = null;
            nextAttemptAt = now;
            lastError = "发布 Claim 超时，等待重试";
        }
    }

    public UUID getMessageId() { return messageId; }
    public UUID getExecutionId() { return executionId; }
    public UUID getEngineId() { return engineId; }
    public long getSequence() { return sequence; }
    public String getTopic() { return topic; }
    public String getMessageType() { return messageType; }
    public String getPayload() { return payload; }
    public DispatcherOutboxState getState() { return state; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getClaimedAt() { return claimedAt; }
}
