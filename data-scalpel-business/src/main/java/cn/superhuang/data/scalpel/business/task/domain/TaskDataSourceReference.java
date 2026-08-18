package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;
import java.util.UUID;

/** Rebuildable, task-type-neutral index of direct data-source references in saved task definitions. */
@Entity
@Table(
        name = "task_data_source_reference",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_task_data_source_reference_location",
                columnNames = {"task_id", "data_source_id", "location_kind", "location_key", "reference_role", "resource_kind"}
        ),
        indexes = {
                @Index(name = "idx_task_data_source_reference_task", columnList = "task_id"),
                @Index(name = "idx_task_data_source_reference_source", columnList = "data_source_id, task_id")
        }
)
public class TaskDataSourceReference extends BaseEntity {

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "data_source_id", nullable = false, updatable = false)
    private UUID dataSourceId;

    @Column(name = "definition_version", nullable = false, updatable = false)
    private int definitionVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_role", nullable = false, updatable = false, length = 16)
    private TaskDataSourceReferenceRole referenceRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_kind", nullable = false, updatable = false, length = 32)
    private TaskDataSourceReferenceLocationKind locationKind;

    @Column(name = "location_key", nullable = false, updatable = false, length = 128)
    private String locationKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_kind", nullable = false, updatable = false, length = 32)
    private TaskDataSourceResourceKind resourceKind;

    protected TaskDataSourceReference() {
    }

    private TaskDataSourceReference(
            UUID taskId,
            UUID dataSourceId,
            int definitionVersion,
            TaskDataSourceReferenceRole referenceRole,
            TaskDataSourceReferenceLocationKind locationKind,
            String locationKey,
            TaskDataSourceResourceKind resourceKind
    ) {
        if (definitionVersion < 1) {
            throw new IllegalArgumentException("任务定义版本必须大于 0");
        }
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.dataSourceId = Objects.requireNonNull(dataSourceId, "dataSourceId");
        this.definitionVersion = definitionVersion;
        this.referenceRole = Objects.requireNonNull(referenceRole, "referenceRole");
        this.locationKind = Objects.requireNonNull(locationKind, "locationKind");
        this.locationKey = required(locationKey, "locationKey");
        this.resourceKind = Objects.requireNonNull(resourceKind, "resourceKind");
    }

    public static TaskDataSourceReference create(
            UUID taskId,
            UUID dataSourceId,
            int definitionVersion,
            TaskDataSourceReferenceRole referenceRole,
            TaskDataSourceReferenceLocationKind locationKind,
            String locationKey,
            TaskDataSourceResourceKind resourceKind
    ) {
        return new TaskDataSourceReference(
                taskId, dataSourceId, definitionVersion, referenceRole, locationKind, locationKey, resourceKind
        );
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getDataSourceId() {
        return dataSourceId;
    }

    public int getDefinitionVersion() {
        return definitionVersion;
    }

    public TaskDataSourceReferenceRole getReferenceRole() {
        return referenceRole;
    }

    public TaskDataSourceReferenceLocationKind getLocationKind() {
        return locationKind;
    }

    public String getLocationKey() {
        return locationKey;
    }

    public TaskDataSourceResourceKind getResourceKind() {
        return resourceKind;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return value.trim();
    }
}
