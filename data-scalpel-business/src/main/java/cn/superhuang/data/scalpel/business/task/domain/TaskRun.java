package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Immutable-definition execution history for one manually requested local SQL task run. */
@Entity
@Table(name = "ds_task_run")
public class TaskRun extends BaseEntity {

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "definition_version", nullable = false, updatable = false)
    private int definitionVersion;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "definition_snapshot", nullable = false, updatable = false)
    private String definitionSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, updatable = false, length = 32)
    private TaskRunTriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskRunStatus status;

    @Column(name = "queued_at", nullable = false, updatable = false)
    private Instant queuedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "affected_rows")
    private Long affectedRows;

    @Column(length = 1000)
    private String message;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "error_detail")
    private String errorDetail;

    protected TaskRun() {
    }

    private TaskRun(UUID taskId, int definitionVersion, String definitionSnapshot) {
        if (taskId == null || definitionVersion < 1 || definitionSnapshot == null || definitionSnapshot.isBlank()) {
            throw new IllegalArgumentException("任务运行快照无效");
        }
        this.taskId = taskId;
        this.definitionVersion = definitionVersion;
        this.definitionSnapshot = definitionSnapshot;
        this.triggerType = TaskRunTriggerType.MANUAL;
        this.status = TaskRunStatus.QUEUED;
        this.queuedAt = Instant.now();
    }

    public static TaskRun queue(UUID taskId, int definitionVersion, String definitionSnapshot) {
        return new TaskRun(taskId, definitionVersion, definitionSnapshot);
    }

    public void start() {
        requireStatus(TaskRunStatus.QUEUED);
        status = TaskRunStatus.RUNNING;
        startedAt = Instant.now();
    }

    public void succeed(long affectedRows) {
        requireStatus(TaskRunStatus.RUNNING);
        status = TaskRunStatus.SUCCESS;
        this.affectedRows = affectedRows;
        message = "执行成功";
        endedAt = Instant.now();
    }

    public void fail(String message, String errorDetail) {
        if (status != TaskRunStatus.QUEUED && status != TaskRunStatus.RUNNING) {
            return;
        }
        status = TaskRunStatus.FAILED;
        this.message = normalizeMessage(message);
        this.errorDetail = normalizeErrorDetail(errorDetail);
        endedAt = Instant.now();
    }

    public void timeout(String message, String errorDetail) {
        if (status != TaskRunStatus.QUEUED && status != TaskRunStatus.RUNNING) {
            return;
        }
        status = TaskRunStatus.TIMED_OUT;
        this.message = normalizeMessage(message);
        this.errorDetail = normalizeErrorDetail(errorDetail);
        endedAt = Instant.now();
    }

    public UUID getTaskId() {
        return taskId;
    }

    public int getDefinitionVersion() {
        return definitionVersion;
    }

    public String getDefinitionSnapshot() {
        return definitionSnapshot;
    }

    public TaskRunTriggerType getTriggerType() {
        return triggerType;
    }

    public TaskRunStatus getStatus() {
        return status;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public Long getAffectedRows() {
        return affectedRows;
    }

    public String getMessage() {
        return message;
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    private void requireStatus(TaskRunStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("任务运行状态不允许当前操作：" + status);
        }
    }

    private static String normalizeMessage(String value) {
        if (value == null || value.isBlank()) {
            return "任务执行失败";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private static String normalizeErrorDetail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= 4000 ? value : value.substring(0, 4000);
    }
}
