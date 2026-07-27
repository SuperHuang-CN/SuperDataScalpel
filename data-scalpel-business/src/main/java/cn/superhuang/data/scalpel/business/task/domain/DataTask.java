package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** Common metadata root shared by all task definition families. */
@Entity
@Table(name = "task")
public class DataTask extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "directory_id")
    private UUID directoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private TaskType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStatus status;

    @Column(length = 1000)
    private String description;

    @Column(name = "compute_engine_id")
    private UUID computeEngineId;

    protected DataTask() {
    }

    private DataTask(String name, UUID directoryId, TaskType type, String description) {
        this(name, directoryId, type, description, null);
    }

    private DataTask(String name, UUID directoryId, TaskType type, String description, UUID computeEngineId) {
        if (type == null) {
            throw new IllegalArgumentException("任务类型不能为空");
        }
        this.type = type;
        this.status = TaskStatus.DRAFT;
        update(name, directoryId, description, computeEngineId);
    }

    public static DataTask create(String name, UUID directoryId, TaskType type, String description) {
        return new DataTask(name, directoryId, type, description);
    }

    public static DataTask create(
            String name,
            UUID directoryId,
            TaskType type,
            String description,
            UUID computeEngineId
    ) {
        return new DataTask(name, directoryId, type, description, computeEngineId);
    }

    public void update(String name, UUID directoryId, String description) {
        update(name, directoryId, description, computeEngineId);
    }

    public void update(String name, UUID directoryId, String description, UUID computeEngineId) {
        this.name = normalizeRequired(name);
        this.directoryId = directoryId;
        this.description = normalizeOptional(description);
        if (type == TaskType.LOCAL_SQL && computeEngineId != null) {
            throw new IllegalArgumentException("本地 SQL 任务不能绑定计算引擎");
        }
        this.computeEngineId = computeEngineId;
    }

    public void publish() {
        status = TaskStatus.PUBLISHED;
    }

    public void disable() {
        status = TaskStatus.DISABLED;
    }

    public String getName() {
        return name;
    }

    public UUID getDirectoryId() {
        return directoryId;
    }

    public TaskType getType() {
        return type;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public UUID getComputeEngineId() {
        return computeEngineId;
    }

    private static String normalizeRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("必填内容不能为空");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
