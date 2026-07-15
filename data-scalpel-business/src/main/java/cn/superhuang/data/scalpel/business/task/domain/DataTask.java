package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Locale;
import java.util.UUID;

/** Root metadata for one locally executable data-processing task. */
@Entity
@Table(name = "ds_data_task", uniqueConstraints = @UniqueConstraint(name = "uk_ds_data_task_code", columnNames = "code"))
public class DataTask extends BaseEntity {

    @Column(nullable = false, updatable = false, length = 64)
    private String code;

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

    protected DataTask() {
    }

    private DataTask(String code, String name, UUID directoryId, String description) {
        this.code = normalizeCode(code);
        this.type = TaskType.LOCAL_SQL;
        this.status = TaskStatus.DRAFT;
        update(name, directoryId, description);
    }

    public static DataTask create(String code, String name, UUID directoryId, String description) {
        return new DataTask(code, name, directoryId, description);
    }

    public void update(String name, UUID directoryId, String description) {
        this.name = normalizeRequired(name);
        this.directoryId = directoryId;
        this.description = normalizeOptional(description);
    }

    public void publish() {
        status = TaskStatus.PUBLISHED;
    }

    public void disable() {
        status = TaskStatus.DISABLED;
    }

    public String getCode() {
        return code;
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

    private static String normalizeCode(String value) {
        return normalizeRequired(value).toLowerCase(Locale.ROOT);
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
