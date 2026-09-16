package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** Persisted stable JSON definition for one SPARK_CANVAS task. */
@Entity
@Table(
        name = "task_canvas_definition",
        uniqueConstraints = @UniqueConstraint(name = "uk_task_canvas_definition_task", columnNames = "task_id")
)
public class CanvasTaskDefinition extends BaseEntity {

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "schema_minor_version", nullable = false)
    private int schemaMinorVersion;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "definition_json", nullable = false)
    private String definitionJson;

    @Column(nullable = false)
    private int version;

    @Column(name = "file_reference_version")
    private Integer fileReferenceVersion;

    @Column(name = "file_reference_error", length = 200)
    private String fileReferenceError;

    public boolean fileReferencesCurrent() {
        return fileReferenceVersion != null && fileReferenceVersion == version;
    }

    public void fileReferencesIndexed() {
        fileReferenceVersion = version;
        fileReferenceError = null;
    }

    public void fileReferenceIndexFailed() {
        fileReferenceVersion = null;
        fileReferenceError = "Canvas 定义无法解析，请修复并重新保存定义后重建文件引用索引";
    }

    protected CanvasTaskDefinition() {
    }

    private CanvasTaskDefinition(UUID taskId, int schemaVersion, int schemaMinorVersion, String definitionJson) {
        this.taskId = requireTaskId(taskId);
        this.version = 1;
        apply(schemaVersion, schemaMinorVersion, definitionJson);
    }

    public static CanvasTaskDefinition create(
            UUID taskId,
            int schemaVersion,
            int schemaMinorVersion,
            String definitionJson
    ) {
        return new CanvasTaskDefinition(taskId, schemaVersion, schemaMinorVersion, definitionJson);
    }

    public void update(int schemaVersion, int schemaMinorVersion, String definitionJson) {
        if (version == Integer.MAX_VALUE) {
            throw new IllegalStateException("Canvas 任务定义版本已达到最大值");
        }
        apply(schemaVersion, schemaMinorVersion, definitionJson);
        version++;
    }

    public boolean hasSameContent(int schemaVersion, int schemaMinorVersion, String definitionJson) {
        return this.schemaVersion == schemaVersion
                && this.schemaMinorVersion == schemaMinorVersion
                && this.definitionJson.equals(definitionJson);
    }

    public UUID getTaskId() {
        return taskId;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public int getSchemaMinorVersion() {
        return schemaMinorVersion;
    }

    public String getDefinitionJson() {
        return definitionJson;
    }

    public int getVersion() {
        return version;
    }

    private void apply(int schemaVersion, int schemaMinorVersion, String definitionJson) {
        if (schemaVersion < 1 || schemaMinorVersion < 0 || definitionJson == null || definitionJson.isBlank()) {
            throw new IllegalArgumentException("Canvas 任务定义不完整");
        }
        this.schemaVersion = schemaVersion;
        this.schemaMinorVersion = schemaMinorVersion;
        this.definitionJson = definitionJson;
    }

    private static UUID requireTaskId(UUID taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("任务不能为空");
        }
        return taskId;
    }
}
