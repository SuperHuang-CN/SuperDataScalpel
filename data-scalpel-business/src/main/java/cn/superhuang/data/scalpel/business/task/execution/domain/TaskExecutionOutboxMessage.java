package cn.superhuang.data.scalpel.business.task.execution.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.contract.execution.ExecutionCommand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_execution_outbox", uniqueConstraints = {
        @UniqueConstraint(name = "uk_task_execution_outbox_message", columnNames = "message_id")
}, indexes = {
        @Index(name = "idx_task_execution_outbox_due", columnList = "state,next_attempt_at"),
        @Index(name = "idx_task_execution_outbox_submission", columnList = "aggregate_id,execution_id,message_type,state")
})
public class TaskExecutionOutboxMessage extends BaseEntity {

    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "execution_id", nullable = false, updatable = false)
    private UUID executionId;

    @Column(name = "engine_id", nullable = false, updatable = false)
    private UUID engineId;

    @Column(nullable = false, length = 249, updatable = false)
    private String topic;

    @Column(name = "message_type", nullable = false, length = 64, updatable = false)
    private String messageType;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false, updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskExecutionOutboxState state;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    protected TaskExecutionOutboxMessage() {
    }

    public static TaskExecutionOutboxMessage pending(String topic, ExecutionCommand command, String payload) {
        if (command == null || blank(topic) || blank(payload)) {
            throw new IllegalArgumentException("执行 Outbox 消息无效");
        }
        TaskExecutionOutboxMessage message = new TaskExecutionOutboxMessage();
        message.messageId = command.messageId();
        message.aggregateId = command.runId();
        message.executionId = command.executionId();
        message.engineId = command.engineId();
        message.topic = topic.trim();
        message.messageType = command.messageType().name();
        message.payload = payload;
        message.state = TaskExecutionOutboxState.PENDING;
        message.nextAttemptAt = Instant.now();
        return message;
    }

    public void claim(Instant now) {
        if (state != TaskExecutionOutboxState.PENDING && state != TaskExecutionOutboxState.FAILED) {
            throw new IllegalStateException("Outbox 消息当前不能 Claim");
        }
        state = TaskExecutionOutboxState.PUBLISHING;
        claimedAt = now;
        attempts++;
        lastError = null;
    }

    public void published(Instant now) {
        if (state != TaskExecutionOutboxState.PUBLISHING) throw new IllegalStateException("Outbox 消息未处于发布中");
        state = TaskExecutionOutboxState.PUBLISHED;
        publishedAt = now;
        claimedAt = null;
        lastError = null;
    }

    public void failed(Instant now, String error) {
        if (state != TaskExecutionOutboxState.PUBLISHING) throw new IllegalStateException("Outbox 消息未处于发布中");
        state = TaskExecutionOutboxState.FAILED;
        claimedAt = null;
        long seconds = Math.min(300, 1L << Math.min(8, Math.max(0, attempts - 1)));
        nextAttemptAt = now.plusSeconds(seconds);
        lastError = safe(error);
    }

    public void recoverStaleClaim(Instant now, Duration claimTimeout) {
        if (state == TaskExecutionOutboxState.PUBLISHING && claimedAt != null
                && claimedAt.plus(claimTimeout).isBefore(now)) {
            state = TaskExecutionOutboxState.FAILED;
            nextAttemptAt = now;
            claimedAt = null;
            lastError = "发布 Claim 超时，等待重试";
        }
    }

    public UUID getMessageId() { return messageId; }
    public UUID getAggregateId() { return aggregateId; }
    public UUID getExecutionId() { return executionId; }
    public UUID getEngineId() { return engineId; }
    public String getTopic() { return topic; }
    public String getMessageType() { return messageType; }
    public String getPayload() { return payload; }
    public TaskExecutionOutboxState getState() { return state; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getLastError() { return lastError; }

    private static String safe(String value) {
        String safe = blank(value) ? "Kafka 发布失败" : value.trim().replaceAll("(?i)(password|secret|token)=[^\\s,;]+", "$1=***");
        return safe.substring(0, Math.min(1000, safe.length()));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
