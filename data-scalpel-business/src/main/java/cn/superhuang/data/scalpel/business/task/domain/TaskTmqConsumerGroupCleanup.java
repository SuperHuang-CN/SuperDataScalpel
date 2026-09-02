package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
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
@Table(
        name = "task_tmq_consumer_group_cleanup",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_task_tmq_cleanup_group",
                columnNames = {"data_source_id", "topic_name", "consumer_group_id"}
        ),
        indexes = {
                @Index(name = "idx_task_tmq_cleanup_due", columnList = "state,next_attempt_at"),
                @Index(name = "idx_task_tmq_cleanup_task", columnList = "task_id,state")
        }
)
public class TaskTmqConsumerGroupCleanup extends BaseEntity {
    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "data_source_id", nullable = false, updatable = false)
    private UUID dataSourceId;

    @Column(name = "topic_name", nullable = false, updatable = false, length = 192)
    private String topicName;

    @Column(name = "consumer_group_id", nullable = false, updatable = false, length = 96)
    private String consumerGroupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TmqConsumerGroupCleanupState state;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    protected TaskTmqConsumerGroupCleanup() {
    }

    public static TaskTmqConsumerGroupCleanup create(
            UUID taskId,
            UUID dataSourceId,
            String topicName,
            String consumerGroupId
    ) {
        if (taskId == null || dataSourceId == null || topicName == null || topicName.isBlank()
                || consumerGroupId == null || consumerGroupId.isBlank()) {
            throw new IllegalArgumentException("TMQ Consumer Group 清理参数无效");
        }
        TaskTmqConsumerGroupCleanup cleanup = new TaskTmqConsumerGroupCleanup();
        cleanup.taskId = taskId;
        cleanup.dataSourceId = dataSourceId;
        cleanup.topicName = topicName.trim();
        cleanup.consumerGroupId = consumerGroupId.trim();
        cleanup.requeue();
        return cleanup;
    }

    public void requeue() {
        state = TmqConsumerGroupCleanupState.PENDING;
        attempts = 0;
        nextAttemptAt = Instant.now();
        claimedAt = null;
        completedAt = null;
        lastError = null;
    }

    public void claim(Instant now) {
        if (state != TmqConsumerGroupCleanupState.PENDING
                && state != TmqConsumerGroupCleanupState.FAILED) {
            throw new IllegalStateException("TMQ Consumer Group 清理任务当前不能领取");
        }
        state = TmqConsumerGroupCleanupState.RUNNING;
        attempts++;
        claimedAt = now;
        lastError = null;
    }

    public void succeed(Instant now) {
        state = TmqConsumerGroupCleanupState.SUCCESS;
        completedAt = now;
        nextAttemptAt = now;
        lastError = null;
    }

    public void fail(String error, Instant nextAttemptAt) {
        state = TmqConsumerGroupCleanupState.FAILED;
        this.nextAttemptAt = nextAttemptAt;
        String normalized = error == null || error.isBlank() ? "清理执行失败" : error.trim();
        lastError = normalized.substring(0, Math.min(1000, normalized.length()));
    }

    public UUID getTaskId() { return taskId; }
    public UUID getDataSourceId() { return dataSourceId; }
    public String getTopicName() { return topicName; }
    public String getConsumerGroupId() { return consumerGroupId; }
    public TmqConsumerGroupCleanupState getState() { return state; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
}
