package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.ZoneId;
import java.util.UUID;

@Entity
@Table(
        name = "task_schedule",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_task_schedule_task_name",
                columnNames = {"task_id", "name"}
        ),
        indexes = @Index(name = "idx_task_schedule_task_status", columnList = "task_id,status")
)
public class TaskSchedule extends BaseEntity {

    public static final String DEFAULT_ZONE_ID = "Asia/Shanghai";

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "cron_expression", nullable = false, length = 120)
    private String cronExpression;

    @Column(name = "zone_id", nullable = false, length = 64)
    private String zoneId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskScheduleStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "misfire_policy", nullable = false, length = 32)
    private TaskMisfirePolicy misfirePolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "overlap_policy", nullable = false, length = 32)
    private TaskOverlapPolicy overlapPolicy;

    protected TaskSchedule() {
    }

    private TaskSchedule(
            UUID taskId,
            String name,
            String cronExpression,
            String zoneId,
            TaskMisfirePolicy misfirePolicy,
            TaskOverlapPolicy overlapPolicy
    ) {
        if (taskId == null) {
            throw new IllegalArgumentException("任务不能为空");
        }
        this.taskId = taskId;
        this.status = TaskScheduleStatus.DISABLED;
        apply(name, cronExpression, zoneId, misfirePolicy, overlapPolicy);
    }

    public static TaskSchedule create(
            UUID taskId,
            String name,
            String cronExpression,
            String zoneId,
            TaskMisfirePolicy misfirePolicy,
            TaskOverlapPolicy overlapPolicy
    ) {
        return new TaskSchedule(taskId, name, cronExpression, zoneId, misfirePolicy, overlapPolicy);
    }

    public void update(
            String name,
            String cronExpression,
            String zoneId,
            TaskMisfirePolicy misfirePolicy,
            TaskOverlapPolicy overlapPolicy
    ) {
        apply(name, cronExpression, zoneId, misfirePolicy, overlapPolicy);
    }

    public void enable() {
        status = TaskScheduleStatus.ENABLED;
    }

    public void disable() {
        status = TaskScheduleStatus.DISABLED;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getName() {
        return name;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public String getZoneId() {
        return zoneId;
    }

    public TaskScheduleStatus getStatus() {
        return status;
    }

    public TaskMisfirePolicy getMisfirePolicy() {
        return misfirePolicy;
    }

    public TaskOverlapPolicy getOverlapPolicy() {
        return overlapPolicy;
    }

    private void apply(
            String name,
            String cronExpression,
            String zoneId,
            TaskMisfirePolicy misfirePolicy,
            TaskOverlapPolicy overlapPolicy
    ) {
        this.name = requireText(name, "计划名称", 100);
        this.cronExpression = requireText(cronExpression, "Cron 表达式", 120);
        String normalizedZoneId = zoneId == null || zoneId.isBlank() ? DEFAULT_ZONE_ID : zoneId.trim();
        this.zoneId = ZoneId.of(normalizedZoneId).getId();
        this.misfirePolicy = misfirePolicy == null ? TaskMisfirePolicy.FIRE_ONCE_NOW : misfirePolicy;
        this.overlapPolicy = overlapPolicy == null ? TaskOverlapPolicy.FORBID : overlapPolicy;
    }

    private static String requireText(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + "长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }
}
