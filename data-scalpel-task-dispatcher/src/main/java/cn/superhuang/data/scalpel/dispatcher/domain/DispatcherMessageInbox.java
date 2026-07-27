package cn.superhuang.data.scalpel.dispatcher.domain;

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
@Table(name = "dispatcher_message_inbox", uniqueConstraints = {
        @UniqueConstraint(name = "uk_dispatcher_inbox_message", columnNames = "message_id")
}, indexes = {
        @Index(name = "idx_dispatcher_inbox_execution", columnList = "execution_id,received_at")
})
public class DispatcherMessageInbox extends DispatcherBaseEntity {
    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;
    @Column(name = "message_type", nullable = false, length = 64, updatable = false)
    private String messageType;
    @Column(nullable = false, length = 249, updatable = false)
    private String topic;
    @Column(name = "topic_partition", nullable = false, updatable = false)
    private int partition;
    @Column(name = "topic_offset", nullable = false, updatable = false)
    private long offset;
    @Column(name = "execution_id", nullable = false, updatable = false)
    private UUID executionId;
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;
    @Column(name = "processed_at")
    private Instant processedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DispatcherInboxState state;
    @Column(name = "safe_error", length = 1000)
    private String safeError;

    protected DispatcherMessageInbox() {
    }

    public static DispatcherMessageInbox received(
            UUID messageId,
            String messageType,
            String topic,
            int partition,
            long offset,
            UUID executionId
    ) {
        DispatcherMessageInbox inbox = new DispatcherMessageInbox();
        inbox.messageId = messageId;
        inbox.messageType = messageType;
        inbox.topic = topic;
        inbox.partition = partition;
        inbox.offset = offset;
        inbox.executionId = executionId;
        inbox.receivedAt = Instant.now();
        inbox.state = DispatcherInboxState.RECEIVED;
        return inbox;
    }

    public void processed() {
        state = DispatcherInboxState.PROCESSED;
        processedAt = Instant.now();
        safeError = null;
    }

    public void rejected(String error) {
        state = DispatcherInboxState.REJECTED;
        processedAt = Instant.now();
        safeError = safe(error);
    }

    public UUID getMessageId() { return messageId; }
    public DispatcherInboxState getState() { return state; }
    public String getSafeError() { return safeError; }

    private static String safe(String value) {
        String safe = value == null || value.isBlank() ? "消息被拒绝" : value.trim();
        return safe.substring(0, Math.min(1000, safe.length()));
    }
}
