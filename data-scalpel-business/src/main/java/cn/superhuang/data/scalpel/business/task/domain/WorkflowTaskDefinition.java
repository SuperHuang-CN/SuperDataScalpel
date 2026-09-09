package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;

@Entity
@Table(name = "task_workflow_definition", uniqueConstraints =
        @UniqueConstraint(name = "uk_task_workflow_definition_task", columnNames = "task_id"))
public class WorkflowTaskDefinition extends BaseEntity {
    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;
    @Column(nullable = false)
    private int version;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "definition_json", nullable = false)
    private String definitionJson;
    protected WorkflowTaskDefinition() { }
    public static WorkflowTaskDefinition create(UUID taskId, String json) {
        var definition = new WorkflowTaskDefinition();
        definition.taskId = java.util.Objects.requireNonNull(taskId);
        definition.version = 1;
        definition.definitionJson = java.util.Objects.requireNonNull(json);
        return definition;
    }
    public void update(String json) {
        if (!definitionJson.equals(json)) {
            version = Math.incrementExact(version);
            definitionJson = json;
        }
    }
    public UUID getTaskId() { return taskId; }
    public int getVersion() { return version; }
    public String getDefinitionJson() { return definitionJson; }
}
