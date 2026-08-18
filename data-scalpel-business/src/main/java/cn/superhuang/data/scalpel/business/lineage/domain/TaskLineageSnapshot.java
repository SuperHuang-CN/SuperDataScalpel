package cn.superhuang.data.scalpel.business.lineage.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable lineage generated for one published task-definition version. */
@Entity
@Table(
        name = "task_lineage_snapshot",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_task_lineage_snapshot_generation",
                columnNames = {"task_id", "definition_version", "generation"}
        ),
        indexes = {
                @Index(name = "idx_task_lineage_snapshot_current", columnList = "task_id, retired_at"),
                @Index(name = "idx_task_lineage_snapshot_fingerprint", columnList = "task_id, content_sha256")
        }
)
public class TaskLineageSnapshot extends BaseEntity {

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "task_name_snapshot", nullable = false, updatable = false, length = 100)
    private String taskNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, updatable = false, length = 32)
    private TaskType taskType;

    @Column(name = "definition_version", nullable = false, updatable = false)
    private int definitionVersion;

    @Column(nullable = false, updatable = false)
    private int generation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private LineageCoverage coverage;

    @Column(name = "generator_version", nullable = false, updatable = false)
    private int generatorVersion;

    @Column(name = "content_sha256", nullable = false, updatable = false, length = 64)
    private String contentSha256;

    @Column(name = "retired_at")
    private Instant retiredAt;

    protected TaskLineageSnapshot() {
    }

    private TaskLineageSnapshot(
            UUID taskId,
            String taskNameSnapshot,
            TaskType taskType,
            int definitionVersion,
            int generation,
            LineageCoverage coverage,
            int generatorVersion,
            String contentSha256
    ) {
        if (definitionVersion < 1 || generation < 1 || generatorVersion < 1) {
            throw new IllegalArgumentException("血缘版本必须大于 0");
        }
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.taskNameSnapshot = required(taskNameSnapshot, "taskNameSnapshot");
        this.taskType = Objects.requireNonNull(taskType, "taskType");
        this.definitionVersion = definitionVersion;
        this.generation = generation;
        this.coverage = Objects.requireNonNull(coverage, "coverage");
        this.generatorVersion = generatorVersion;
        this.contentSha256 = required(contentSha256, "contentSha256");
    }

    public static TaskLineageSnapshot create(
            UUID taskId,
            String taskNameSnapshot,
            TaskType taskType,
            int definitionVersion,
            int generation,
            LineageCoverage coverage,
            int generatorVersion,
            String contentSha256
    ) {
        return new TaskLineageSnapshot(
                taskId, taskNameSnapshot, taskType, definitionVersion, generation,
                coverage, generatorVersion, contentSha256
        );
    }

    public void retire(Instant retiredAt) {
        if (this.retiredAt == null) {
            this.retiredAt = Objects.requireNonNull(retiredAt, "retiredAt");
        }
    }

    public UUID getTaskId() { return taskId; }
    public String getTaskNameSnapshot() { return taskNameSnapshot; }
    public TaskType getTaskType() { return taskType; }
    public int getDefinitionVersion() { return definitionVersion; }
    public int getGeneration() { return generation; }
    public LineageCoverage getCoverage() { return coverage; }
    public int getGeneratorVersion() { return generatorVersion; }
    public String getContentSha256() { return contentSha256; }
    public Instant getRetiredAt() { return retiredAt; }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        return value.trim();
    }
}
