package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "task_streaming_deployment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_task_streaming_deployment_version",
                columnNames = {"task_id", "definition_version"}
        ),
        indexes = {
                @Index(name = "idx_task_streaming_deployment_task_state", columnList = "task_id,actual_state"),
                @Index(name = "idx_task_streaming_deployment_run", columnList = "current_run_id")
        }
)
public class TaskStreamingDeployment extends BaseEntity {

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "definition_version", nullable = false, updatable = false)
    private int definitionVersion;

    @Column(name = "compute_engine_id", nullable = false, updatable = false)
    private UUID computeEngineId;

    @Column(name = "current_run_id")
    private UUID currentRunId;

    @Column(name = "checkpoint_key_prefix", nullable = false, updatable = false, length = 500)
    private String checkpointKeyPrefix;

    @Enumerated(EnumType.STRING)
    @Column(name = "desired_state", nullable = false, length = 16)
    private StreamingDeploymentDesiredState desiredState;

    @Enumerated(EnumType.STRING)
    @Column(name = "actual_state", nullable = false, length = 16)
    private StreamingDeploymentActualState actualState;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "stop_requested_at")
    private Instant stopRequestedAt;

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    @Column(name = "last_progress_at")
    private Instant lastProgressAt;

    @Column(name = "last_error_at")
    private Instant lastErrorAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    protected TaskStreamingDeployment() {
    }

    public static TaskStreamingDeployment create(
            UUID taskId,
            int definitionVersion,
            UUID computeEngineId,
            String checkpointKeyPrefix
    ) {
        if (taskId == null || definitionVersion < 1 || computeEngineId == null
                || checkpointKeyPrefix == null || checkpointKeyPrefix.isBlank()) {
            throw new IllegalArgumentException("实时部署参数无效");
        }
        TaskStreamingDeployment deployment = new TaskStreamingDeployment();
        deployment.taskId = taskId;
        deployment.definitionVersion = definitionVersion;
        deployment.computeEngineId = computeEngineId;
        deployment.checkpointKeyPrefix = checkpointKeyPrefix.trim();
        deployment.desiredState = StreamingDeploymentDesiredState.STOPPED;
        deployment.actualState = StreamingDeploymentActualState.STOPPED;
        return deployment;
    }

    public void beginStart(UUID runId) {
        if (runId == null || actualState.active()) throw new IllegalStateException("实时部署当前不能启动");
        currentRunId = runId;
        desiredState = StreamingDeploymentDesiredState.RUNNING;
        actualState = StreamingDeploymentActualState.STARTING;
        stopRequestedAt = null;
        stoppedAt = null;
        lastError = null;
        lastErrorAt = null;
    }

    public void markRunning(Instant at) {
        if (actualState != StreamingDeploymentActualState.STARTING
                && actualState != StreamingDeploymentActualState.RUNNING) return;
        actualState = StreamingDeploymentActualState.RUNNING;
        startedAt = startedAt == null ? (at == null ? Instant.now() : at) : startedAt;
        lastProgressAt = at == null ? Instant.now() : at;
    }

    public void recordProgress(Instant at) {
        if (actualState == StreamingDeploymentActualState.STARTING) markRunning(at);
        if (actualState == StreamingDeploymentActualState.RUNNING) {
            lastProgressAt = at == null ? Instant.now() : at;
        }
    }

    public void requestStop() {
        desiredState = StreamingDeploymentDesiredState.STOPPED;
        if (actualState == StreamingDeploymentActualState.STOPPED
                || actualState == StreamingDeploymentActualState.STOPPING) return;
        if (!actualState.active()) throw new IllegalStateException("实时部署当前不能停止");
        actualState = StreamingDeploymentActualState.STOPPING;
        stopRequestedAt = Instant.now();
    }

    public void markStopped(Instant at) {
        desiredState = StreamingDeploymentDesiredState.STOPPED;
        actualState = StreamingDeploymentActualState.STOPPED;
        stoppedAt = at == null ? Instant.now() : at;
    }

    public void fail(String error, Instant at) {
        desiredState = StreamingDeploymentDesiredState.STOPPED;
        actualState = StreamingDeploymentActualState.FAILED;
        lastError = truncate(error);
        lastErrorAt = at == null ? Instant.now() : at;
    }

    public UUID getTaskId() { return taskId; }
    public int getDefinitionVersion() { return definitionVersion; }
    public UUID getComputeEngineId() { return computeEngineId; }
    public UUID getCurrentRunId() { return currentRunId; }
    public String getCheckpointKeyPrefix() { return checkpointKeyPrefix; }
    public StreamingDeploymentDesiredState getDesiredState() { return desiredState; }
    public StreamingDeploymentActualState getActualState() { return actualState; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getStopRequestedAt() { return stopRequestedAt; }
    public Instant getStoppedAt() { return stoppedAt; }
    public Instant getLastProgressAt() { return lastProgressAt; }
    public Instant getLastErrorAt() { return lastErrorAt; }
    public String getLastError() { return lastError; }

    private static String truncate(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.substring(0, Math.min(2000, normalized.length()));
    }
}
