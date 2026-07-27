package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/** One explicit input-model reference for a local SQL task. */
@Entity
@Table(
        name = "task_local_sql_input",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_task_local_sql_input_model", columnNames = {"task_id", "model_id"}),
                @UniqueConstraint(name = "uk_task_local_sql_input_order", columnNames = {"task_id", "sort_order"})
        },
        indexes = @Index(
                name = "idx_task_local_sql_input_model_task",
                columnList = "model_id, task_id"
        )
)
public class LocalSqlTaskInput extends BaseEntity {

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "model_id", nullable = false, updatable = false)
    private UUID modelId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected LocalSqlTaskInput() {
    }

    private LocalSqlTaskInput(UUID taskId, UUID modelId, int sortOrder) {
        if (taskId == null || modelId == null || sortOrder < 0) {
            throw new IllegalArgumentException("任务输入引用无效");
        }
        this.taskId = taskId;
        this.modelId = modelId;
        this.sortOrder = sortOrder;
    }

    public static LocalSqlTaskInput create(UUID taskId, UUID modelId, int sortOrder) {
        return new LocalSqlTaskInput(taskId, modelId, sortOrder);
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getModelId() {
        return modelId;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
