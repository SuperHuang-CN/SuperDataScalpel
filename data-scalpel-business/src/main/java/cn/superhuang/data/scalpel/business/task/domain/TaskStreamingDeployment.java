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
import cn.superhuang.data.scalpel.contract.execution.StreamingSourceProgress;
import cn.superhuang.data.scalpel.contract.execution.StreamingSourceKind;
import cn.superhuang.data.scalpel.contract.execution.StreamingCheckpointMode;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "task_streaming_deployment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_task_streaming_deployment_version",
                columnNames = {"task_id", "definition_version", "checkpoint_generation"}
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

    @Column(name = "checkpoint_generation", nullable = false, updatable = false)
    private int checkpointGeneration;

    @Enumerated(EnumType.STRING)
    @Column(name = "checkpoint_start_mode", nullable = false, updatable = false, length = 16)
    private StreamingCheckpointMode checkpointStartMode;

    @Column(name = "checkpoint_source_deployment_id", updatable = false)
    private UUID checkpointSourceDeploymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false, updatable = false, length = 16)
    private StreamingDeploymentExecutionMode executionMode;

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

    @Column(name = "source_node_id")
    private UUID sourceNodeId;

    @Column(name = "source_signature", length = 64)
    private String sourceSignature;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "initial_source_offset")
    private String initialSourceOffset;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "last_committed_offset")
    private String lastCommittedOffset;

    @Column(name = "last_window_start")
    private Instant lastWindowStart;

    @Column(name = "last_window_end")
    private Instant lastWindowEnd;

    @Column(name = "last_window_row_count")
    private Long lastWindowRowCount;

    @Column(name = "last_poll_duration_millis")
    private Long lastPollDurationMillis;

    @Column(name = "last_poll_at")
    private Instant lastPollAt;

    @Column(name = "cursor_lag_millis")
    private Long cursorLagMillis;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_kind", length = 32)
    private StreamingSourceKind sourceKind;

    @Column(name = "last_vgroup_count")
    private Integer lastVGroupCount;

    @Column(name = "last_batch_offset_span")
    private Long lastBatchOffsetSpan;

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
        deployment.checkpointGeneration = 1;
        deployment.checkpointStartMode = StreamingCheckpointMode.FRESH;
        deployment.executionMode = StreamingDeploymentExecutionMode.REAL;
        deployment.desiredState = StreamingDeploymentDesiredState.STOPPED;
        deployment.actualState = StreamingDeploymentActualState.STOPPED;
        return deployment;
    }

    public static TaskStreamingDeployment create(
            UUID taskId,
            int definitionVersion,
            UUID computeEngineId,
            String checkpointKeyPrefix,
            int checkpointGeneration,
            StreamingCheckpointMode checkpointStartMode,
            UUID checkpointSourceDeploymentId
    ) {
        TaskStreamingDeployment deployment = create(taskId, definitionVersion, computeEngineId, checkpointKeyPrefix);
        if (checkpointGeneration < 1 || checkpointStartMode == null
                || checkpointStartMode == StreamingCheckpointMode.CONTINUE && checkpointSourceDeploymentId == null
                || checkpointStartMode == StreamingCheckpointMode.FRESH && checkpointSourceDeploymentId != null) {
            throw new IllegalArgumentException("Checkpoint 世代参数无效");
        }
        deployment.checkpointGeneration = checkpointGeneration;
        deployment.checkpointStartMode = checkpointStartMode;
        deployment.checkpointSourceDeploymentId = checkpointSourceDeploymentId;
        return deployment;
    }

    public static TaskStreamingDeployment createTrial(
            UUID taskId,
            int definitionVersion,
            UUID computeEngineId,
            String checkpointKeyPrefix,
            int checkpointGeneration
    ) {
        TaskStreamingDeployment deployment = create(
                taskId, definitionVersion, computeEngineId, checkpointKeyPrefix,
                checkpointGeneration, StreamingCheckpointMode.FRESH, null);
        deployment.executionMode = StreamingDeploymentExecutionMode.TRIAL;
        return deployment;
    }

    public static TaskStreamingDeployment create(
            UUID taskId,
            int definitionVersion,
            UUID computeEngineId,
            String checkpointKeyPrefix,
            UUID sourceNodeId,
            String sourceSignature,
            String initialSourceOffset
    ) {
        TaskStreamingDeployment deployment = create(
                taskId, definitionVersion, computeEngineId, checkpointKeyPrefix);
        if ((sourceNodeId == null) != (sourceSignature == null)
                || sourceSignature != null && !sourceSignature.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("实时来源身份无效");
        }
        deployment.sourceNodeId = sourceNodeId;
        deployment.sourceSignature = sourceSignature;
        deployment.initialSourceOffset = truncateOffset(initialSourceOffset);
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

    public void initializeSource(
            UUID sourceNodeId,
            String sourceSignature,
            String initialSourceOffset
    ) {
        if (sourceNodeId == null || sourceSignature == null
                || !sourceSignature.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("实时来源身份无效");
        }
        if (this.sourceNodeId != null || this.sourceSignature != null) {
            if (!sourceNodeId.equals(this.sourceNodeId)
                    || !sourceSignature.equals(this.sourceSignature)) {
                throw new IllegalStateException("实时部署的来源身份不能修改");
            }
            return;
        }
        this.sourceNodeId = sourceNodeId;
        this.sourceSignature = sourceSignature;
        this.initialSourceOffset = truncateOffset(initialSourceOffset);
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

    public void recordSourceProgress(StreamingSourceProgress progress) {
        if (progress == null) return;
        if (sourceNodeId == null || sourceSignature == null
                || !sourceNodeId.toString().equals(progress.sourceNodeId())
                || !sourceSignature.equals(progress.sourceSignature())) {
            throw new IllegalArgumentException("实时来源进度与部署身份不匹配");
        }
        lastCommittedOffset = truncateOffset(progress.committedOffset());
        lastWindowStart = progress.windowStart();
        lastWindowEnd = progress.windowEnd();
        lastWindowRowCount = progress.rowCount();
        lastPollDurationMillis = progress.pollDurationMillis();
        lastPollAt = progress.pollTime();
        cursorLagMillis = progress.cursorLagMillis();
        sourceKind = progress.sourceKind();
        lastVGroupCount = progress.vGroupCount();
        lastBatchOffsetSpan = progress.batchOffsetSpan();
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
    public int getCheckpointGeneration() { return checkpointGeneration; }
    public StreamingCheckpointMode getCheckpointStartMode() { return checkpointStartMode; }
    public UUID getCheckpointSourceDeploymentId() { return checkpointSourceDeploymentId; }
    public StreamingDeploymentExecutionMode getExecutionMode() { return executionMode; }
    public StreamingDeploymentDesiredState getDesiredState() { return desiredState; }
    public StreamingDeploymentActualState getActualState() { return actualState; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getStopRequestedAt() { return stopRequestedAt; }
    public Instant getStoppedAt() { return stoppedAt; }
    public Instant getLastProgressAt() { return lastProgressAt; }
    public Instant getLastErrorAt() { return lastErrorAt; }
    public String getLastError() { return lastError; }
    public UUID getSourceNodeId() { return sourceNodeId; }
    public String getSourceSignature() { return sourceSignature; }
    public String getInitialSourceOffset() { return initialSourceOffset; }
    public String getLastCommittedOffset() { return lastCommittedOffset; }
    public Instant getLastWindowStart() { return lastWindowStart; }
    public Instant getLastWindowEnd() { return lastWindowEnd; }
    public Long getLastWindowRowCount() { return lastWindowRowCount; }
    public Long getLastPollDurationMillis() { return lastPollDurationMillis; }
    public Instant getLastPollAt() { return lastPollAt; }
    public Long getCursorLagMillis() { return cursorLagMillis; }
    public StreamingSourceKind getSourceKind() { return sourceKind; }
    public Integer getLastVGroupCount() { return lastVGroupCount; }
    public Long getLastBatchOffsetSpan() { return lastBatchOffsetSpan; }

    private static String truncate(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.substring(0, Math.min(2000, normalized.length()));
    }

    private static String truncateOffset(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 4000) throw new IllegalArgumentException("实时 Offset 过长");
        return normalized;
    }
}
