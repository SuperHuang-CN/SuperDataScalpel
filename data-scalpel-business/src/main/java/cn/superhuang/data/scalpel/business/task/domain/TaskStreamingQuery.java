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
        name = "task_streaming_query",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_task_streaming_query_output",
                columnNames = {"deployment_id", "output_node_id"}
        ),
        indexes = @Index(name = "idx_task_streaming_query_deployment_state", columnList = "deployment_id,state")
)
public class TaskStreamingQuery extends BaseEntity {

    @Column(name = "deployment_id", nullable = false, updatable = false)
    private UUID deploymentId;

    @Column(name = "output_node_id", nullable = false, updatable = false)
    private UUID outputNodeId;

    @Column(name = "output_node_name", nullable = false, length = 100)
    private String outputNodeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "sink_type", nullable = false, updatable = false, length = 16)
    private StreamingSinkType sinkType;

    @Column(name = "checkpoint_key", nullable = false, updatable = false, length = 500)
    private String checkpointKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StreamingQueryState state;

    @Column(name = "latest_batch_id")
    private Long latestBatchId;

    @Column(name = "latest_input_rows")
    private Long latestInputRows;

    @Column(name = "input_rows_per_second")
    private Double inputRowsPerSecond;

    @Column(name = "processed_rows_per_second")
    private Double processedRowsPerSecond;

    @Column(name = "batch_duration_millis")
    private Long batchDurationMillis;

    @Column(name = "last_progress_at")
    private Instant lastProgressAt;

    @Column(name = "last_error_at")
    private Instant lastErrorAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    protected TaskStreamingQuery() {
    }

    public static TaskStreamingQuery create(
            UUID deploymentId,
            UUID outputNodeId,
            String outputNodeName,
            StreamingSinkType sinkType,
            String checkpointKey
    ) {
        if (deploymentId == null || outputNodeId == null || outputNodeName == null || outputNodeName.isBlank()
                || sinkType == null || checkpointKey == null || checkpointKey.isBlank()) {
            throw new IllegalArgumentException("实时输出查询参数无效");
        }
        TaskStreamingQuery query = new TaskStreamingQuery();
        query.deploymentId = deploymentId;
        query.outputNodeId = outputNodeId;
        query.outputNodeName = outputNodeName.trim();
        query.sinkType = sinkType;
        query.checkpointKey = checkpointKey.trim();
        query.state = StreamingQueryState.STARTING;
        return query;
    }

    public void beginStart() {
        state = StreamingQueryState.STARTING;
        lastError = null;
        lastErrorAt = null;
    }

    public void recordProgress(
            long batchId,
            long inputRows,
            double inputRate,
            double processedRate,
            long durationMillis,
            Instant at
    ) {
        if (batchId < -1 || inputRows < 0 || inputRate < 0 || processedRate < 0 || durationMillis < 0) {
            throw new IllegalArgumentException("实时输出进度无效");
        }
        state = StreamingQueryState.RUNNING;
        latestBatchId = batchId;
        latestInputRows = inputRows;
        inputRowsPerSecond = inputRate;
        processedRowsPerSecond = processedRate;
        batchDurationMillis = durationMillis;
        lastProgressAt = at == null ? Instant.now() : at;
    }

    public void requestStop() {
        if (state == StreamingQueryState.STARTING || state == StreamingQueryState.RUNNING) {
            state = StreamingQueryState.STOPPING;
        }
    }

    public void markStopped() {
        state = StreamingQueryState.STOPPED;
    }

    public void fail(String error, Instant at) {
        state = StreamingQueryState.FAILED;
        if (error != null && !error.isBlank()) {
            String normalized = error.trim();
            lastError = normalized.substring(0, Math.min(2000, normalized.length()));
        }
        lastErrorAt = at == null ? Instant.now() : at;
    }

    public UUID getDeploymentId() { return deploymentId; }
    public UUID getOutputNodeId() { return outputNodeId; }
    public String getOutputNodeName() { return outputNodeName; }
    public StreamingSinkType getSinkType() { return sinkType; }
    public String getCheckpointKey() { return checkpointKey; }
    public StreamingQueryState getState() { return state; }
    public Long getLatestBatchId() { return latestBatchId; }
    public Long getLatestInputRows() { return latestInputRows; }
    public Double getInputRowsPerSecond() { return inputRowsPerSecond; }
    public Double getProcessedRowsPerSecond() { return processedRowsPerSecond; }
    public Long getBatchDurationMillis() { return batchDurationMillis; }
    public Instant getLastProgressAt() { return lastProgressAt; }
    public Instant getLastErrorAt() { return lastErrorAt; }
    public String getLastError() { return lastError; }
}
