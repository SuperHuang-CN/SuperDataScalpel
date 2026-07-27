package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(
        name = "task_canvas_model_reference",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_task_canvas_model_reference_task_node",
                columnNames = {"task_id", "node_id"}
        ),
        indexes = {
                @Index(name = "idx_task_canvas_model_reference_task", columnList = "task_id"),
                @Index(name = "idx_task_canvas_model_reference_model", columnList = "model_id")
        }
)
public class TaskCanvasModelReference extends BaseEntity {

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "node_id", nullable = false, updatable = false)
    private UUID nodeId;

    @Column(name = "model_id", nullable = false, updatable = false)
    private UUID modelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_role", nullable = false, updatable = false, length = 16)
    private TaskCanvasModelReferenceRole referenceRole;

    protected TaskCanvasModelReference() {
    }

    private TaskCanvasModelReference(
            UUID taskId,
            UUID nodeId,
            UUID modelId,
            TaskCanvasModelReferenceRole referenceRole
    ) {
        this.taskId = java.util.Objects.requireNonNull(taskId, "taskId");
        this.nodeId = java.util.Objects.requireNonNull(nodeId, "nodeId");
        this.modelId = java.util.Objects.requireNonNull(modelId, "modelId");
        this.referenceRole = java.util.Objects.requireNonNull(referenceRole, "referenceRole");
    }

    public static TaskCanvasModelReference create(
            UUID taskId,
            UUID nodeId,
            UUID modelId,
            TaskCanvasModelReferenceRole referenceRole
    ) {
        return new TaskCanvasModelReference(taskId, nodeId, modelId, referenceRole);
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getNodeId() {
        return nodeId;
    }

    public UUID getModelId() {
        return modelId;
    }

    public TaskCanvasModelReferenceRole getReferenceRole() {
        return referenceRole;
    }
}
