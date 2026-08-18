package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "task_model_quality_definition",
        indexes = @Index(name = "idx_task_model_quality_definition_model", columnList = "model_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_task_model_quality_definition_task", columnNames = "task_id")
)
public class ModelQualityTaskDefinition extends BaseEntity {

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "model_id", nullable = false)
    private UUID modelId;

    @Column(nullable = false)
    private int version;

    @Column(name = "failure_sample_limit")
    private Integer failureSampleLimit;

    protected ModelQualityTaskDefinition() {
    }

    private ModelQualityTaskDefinition(UUID taskId, UUID modelId) {
        this.taskId = Objects.requireNonNull(taskId, "任务 ID 不能为空");
        this.modelId = Objects.requireNonNull(modelId, "目标模型 ID 不能为空");
        this.version = 1;
        this.failureSampleLimit = 100;
    }

    public static ModelQualityTaskDefinition create(UUID taskId, UUID modelId) {
        return new ModelQualityTaskDefinition(taskId, modelId);
    }

    public boolean updateModel(UUID modelId) {
        return update(modelId, null);
    }

    public boolean update(UUID modelId, Integer failureSampleLimit) {
        UUID normalized = Objects.requireNonNull(modelId, "目标模型 ID 不能为空");
        int normalizedLimit = failureSampleLimit == null ? getFailureSampleLimit() : failureSampleLimit;
        if (normalizedLimit < 0 || normalizedLimit > 1000) {
            throw new IllegalArgumentException("每条失败规则样本数必须在 0 到 1000 之间");
        }
        if (Objects.equals(this.modelId, normalized) && getFailureSampleLimit() == normalizedLimit) return false;
        if (version == Integer.MAX_VALUE) throw new IllegalStateException("质检任务定义版本已达到最大值");
        this.modelId = normalized;
        this.failureSampleLimit = normalizedLimit;
        version++;
        return true;
    }

    public UUID getTaskId() { return taskId; }
    public UUID getModelId() { return modelId; }
    public int getVersion() { return version; }
    public int getFailureSampleLimit() { return failureSampleLimit == null ? 0 : failureSampleLimit; }
}
