package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/** Rebuildable projection of the distinct file tables referenced by a saved Canvas. */
@Entity
@Table(name = "task_canvas_file_reference", uniqueConstraints = @UniqueConstraint(
        name = "uk_task_canvas_file_reference", columnNames = {"task_id", "file_table_id"}), indexes =
        @Index(name = "idx_task_canvas_file_reference_table", columnList = "file_table_id"))
public class TaskCanvasFileReference extends BaseEntity {
    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;
    @Column(name = "file_table_id", nullable = false, updatable = false)
    private UUID fileTableId;

    protected TaskCanvasFileReference() { }

    public static TaskCanvasFileReference create(UUID taskId, UUID tableId) {
        var reference = new TaskCanvasFileReference();
        reference.taskId = java.util.Objects.requireNonNull(taskId);
        reference.fileTableId = java.util.Objects.requireNonNull(tableId);
        return reference;
    }
}
