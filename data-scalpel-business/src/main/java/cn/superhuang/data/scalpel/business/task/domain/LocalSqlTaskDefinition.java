package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** The complete mutable SQL definition of a LOCAL_SQL task. */
@Entity
@Table(
        name = "task_local_sql_definition",
        uniqueConstraints = @UniqueConstraint(name = "uk_task_local_sql_definition_task", columnNames = "task_id"),
        indexes = @Index(
                name = "idx_task_local_sql_definition_output_model_task",
                columnList = "output_model_id, task_id"
        )
)
public class LocalSqlTaskDefinition extends BaseEntity {

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "sql_text", nullable = false)
    private String sqlText;

    @Column(name = "output_model_id", nullable = false)
    private UUID outputModelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "write_mode", nullable = false, length = 32)
    private LocalSqlWriteMode writeMode;

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds;

    @Column(nullable = false)
    private int version;

    protected LocalSqlTaskDefinition() {
    }

    private LocalSqlTaskDefinition(
            UUID taskId,
            String sqlText,
            UUID outputModelId,
            LocalSqlWriteMode writeMode,
            int timeoutSeconds
    ) {
        this.taskId = taskId;
        this.version = 1;
        apply(sqlText, outputModelId, writeMode, timeoutSeconds);
    }

    public static LocalSqlTaskDefinition create(
            UUID taskId,
            String sqlText,
            UUID outputModelId,
            LocalSqlWriteMode writeMode,
            int timeoutSeconds
    ) {
        return new LocalSqlTaskDefinition(taskId, sqlText, outputModelId, writeMode, timeoutSeconds);
    }

    public void update(String sqlText, UUID outputModelId, LocalSqlWriteMode writeMode, int timeoutSeconds) {
        apply(sqlText, outputModelId, writeMode, timeoutSeconds);
        if (version == Integer.MAX_VALUE) {
            throw new IllegalStateException("任务定义版本已达到最大值");
        }
        version++;
    }

    public boolean hasSameContent(String sqlText, UUID outputModelId, LocalSqlWriteMode writeMode, int timeoutSeconds) {
        return this.sqlText.equals(sqlText)
                && this.outputModelId.equals(outputModelId)
                && this.writeMode == writeMode
                && this.timeoutSeconds == timeoutSeconds;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getSqlText() {
        return sqlText;
    }

    public UUID getOutputModelId() {
        return outputModelId;
    }

    public LocalSqlWriteMode getWriteMode() {
        return writeMode;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public int getVersion() {
        return version;
    }

    private void apply(String sqlText, UUID outputModelId, LocalSqlWriteMode writeMode, int timeoutSeconds) {
        if (sqlText == null || sqlText.isBlank()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        if (sqlText.length() > 100_000) {
            throw new IllegalArgumentException("SQL 长度不能超过 100000 字符");
        }
        if (outputModelId == null || writeMode == null || timeoutSeconds < 1 || timeoutSeconds > 3600) {
            throw new IllegalArgumentException("本地 SQL 任务定义不完整或超时范围无效");
        }
        this.sqlText = sqlText;
        this.outputModelId = outputModelId;
        this.writeMode = writeMode;
        this.timeoutSeconds = timeoutSeconds;
    }
}
