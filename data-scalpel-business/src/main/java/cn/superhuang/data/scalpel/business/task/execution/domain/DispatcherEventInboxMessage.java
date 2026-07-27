package cn.superhuang.data.scalpel.business.task.execution.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dispatcher_event_inbox", uniqueConstraints = {
        @UniqueConstraint(name = "uk_dispatcher_event_inbox_message", columnNames = "message_id")
}, indexes = {
        @Index(name = "idx_dispatcher_event_inbox_execution", columnList = "execution_id,sequence")
})
public class DispatcherEventInboxMessage extends BaseEntity {

    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(name = "execution_id", nullable = false, updatable = false)
    private UUID executionId;

    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @Column(nullable = false, updatable = false)
    private int attempt;

    @Column(nullable = false, updatable = false)
    private long sequence;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_state", nullable = false, length = 32)
    private DispatcherEventInboxState processingState;

    @Column(name = "safe_error", length = 1000)
    private String safeError;

    protected DispatcherEventInboxMessage() {
    }

    public static DispatcherEventInboxMessage received(DispatcherExecutionEvent event) {
        DispatcherEventInboxMessage message = new DispatcherEventInboxMessage();
        message.messageId = event.messageId();
        message.executionId = event.executionId();
        message.runId = event.runId();
        message.attempt = event.attempt();
        message.sequence = event.sequence();
        message.receivedAt = Instant.now();
        message.processingState = DispatcherEventInboxState.RECEIVED;
        return message;
    }

    public void processed() {
        processingState = DispatcherEventInboxState.PROCESSED;
        processedAt = Instant.now();
        safeError = null;
    }

    public void rejected(String error) {
        processingState = DispatcherEventInboxState.REJECTED;
        processedAt = Instant.now();
        String normalized = error == null || error.isBlank() ? "Dispatcher 事件被拒绝" : error.trim();
        safeError = normalized.substring(0, Math.min(1000, normalized.length()));
    }

    public UUID getMessageId() { return messageId; }
    public UUID getExecutionId() { return executionId; }
    public UUID getRunId() { return runId; }
    public int getAttempt() { return attempt; }
    public long getSequence() { return sequence; }
    public DispatcherEventInboxState getProcessingState() { return processingState; }
    public String getSafeError() { return safeError; }
}
