package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(
        name = "task_streaming_configuration",
        uniqueConstraints = @UniqueConstraint(name = "uk_task_streaming_configuration_task", columnNames = "task_id")
)
public class TaskStreamingConfiguration extends BaseEntity {
    public static final int DEFAULT_TRIGGER_INTERVAL_SECONDS = 10;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "trigger_interval_seconds", nullable = false)
    private int triggerIntervalSeconds;

    protected TaskStreamingConfiguration() {
    }

    public static TaskStreamingConfiguration create(UUID taskId) {
        if (taskId == null) throw new IllegalArgumentException("实时任务不能为空");
        TaskStreamingConfiguration configuration = new TaskStreamingConfiguration();
        configuration.taskId = taskId;
        configuration.triggerIntervalSeconds = DEFAULT_TRIGGER_INTERVAL_SECONDS;
        return configuration;
    }

    public void update(int triggerIntervalSeconds) {
        if (triggerIntervalSeconds < 1 || triggerIntervalSeconds > 300) {
            throw new IllegalArgumentException("微批间隔必须在 1 到 300 秒之间");
        }
        this.triggerIntervalSeconds = triggerIntervalSeconds;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public int getTriggerIntervalSeconds() {
        return triggerIntervalSeconds;
    }
}
