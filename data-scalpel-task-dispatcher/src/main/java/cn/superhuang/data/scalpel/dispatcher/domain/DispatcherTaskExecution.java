package cn.superhuang.data.scalpel.dispatcher.domain;

import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;
import cn.superhuang.data.scalpel.contract.execution.ExecutionErrorCategory;
import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;
import cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType;
import cn.superhuang.data.scalpel.contract.execution.SafeExecutionError;
import cn.superhuang.data.scalpel.contract.execution.SubmitExecutionCommand;
import cn.superhuang.data.scalpel.contract.execution.StartStreamingExecutionCommand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;
import java.util.List;
import cn.superhuang.data.scalpel.contract.quality.QualityConclusion;
import cn.superhuang.data.scalpel.contract.quality.QualitySummary;
import cn.superhuang.data.scalpel.contract.execution.ExecutionUserJarArtifact;
import cn.superhuang.data.scalpel.contract.execution.SparkConfigurationEntry;

@Entity
@Table(name = "dispatcher_task_execution", uniqueConstraints = {
        @UniqueConstraint(name = "uk_dispatcher_execution_attempt", columnNames = {"execution_id", "attempt"})
}, indexes = {
        @Index(name = "idx_dispatcher_execution_queue", columnList = "state,queued_at"),
        @Index(name = "idx_dispatcher_execution_run", columnList = "run_id")
})
public class DispatcherTaskExecution extends DispatcherBaseEntity {

    @Column(name = "engine_id", nullable = false, updatable = false)
    private UUID engineId;
    @Column(name = "execution_id", nullable = false, updatable = false)
    private UUID executionId;
    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;
    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;
    @Column(nullable = false, updatable = false)
    private int attempt;
    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 32, updatable = false)
    private ExecutionTaskType taskType;
    @Column(name = "definition_version", nullable = false, updatable = false)
    private int definitionVersion;
    @Column(name = "streaming_deployment_id", updatable = false)
    private UUID streamingDeploymentId;
    @Column(name = "checkpoint_key_prefix", length = 500, updatable = false)
    private String checkpointKeyPrefix;
    @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false)
    private String requestFingerprint;
    @Enumerated(EnumType.STRING)
    @Column(name = "backend_type", nullable = false, length = 32, updatable = false)
    private ExecutionBackendType backendType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DispatcherExecutionState state;
    @Column(name = "external_execution_id", length = 300)
    private String externalExecutionId;
    @Column(name = "tracking_url", length = 1000)
    private String trackingUrl;
    @Column(name = "manifest_key", nullable = false, length = 500, updatable = false)
    private String manifestKey;
    @Column(name = "manifest_sha256", nullable = false, length = 64, updatable = false)
    private String manifestSha256;
    @Column(name = "result_key", nullable = false, length = 500, updatable = false)
    private String resultKey;
    @Column(name = "log_key", nullable = false, length = 500, updatable = false)
    private String logKey;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "quality_sample_rule_ids", updatable = false)
    private String qualitySampleRuleIds;
    @Column(name = "quality_sample_limit", updatable = false)
    private Integer qualitySampleLimit;
    @Column(name = "user_jar_object_key", length = 500, updatable = false)
    private String userJarObjectKey;
    @Column(name = "user_jar_sha256", length = 64, updatable = false)
    private String userJarSha256;
    @Column(name = "user_jar_size_bytes", updatable = false)
    private Long userJarSizeBytes;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "spark_conf", updatable = false)
    private String sparkConf;
    @Column(name = "deadline_at", updatable = false)
    private Instant deadlineAt;
    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;
    @Column(name = "force_terminate_requested_at")
    private Instant forceTerminateRequestedAt;
    @Column(name = "streaming_stop_requested_at")
    private Instant streamingStopRequestedAt;
    @Column(name = "streaming_force_stop_at")
    private Instant streamingForceStopAt;
    @Column(name = "event_sequence", nullable = false)
    private long eventSequence;
    @Column(name = "safe_error_code", length = 100)
    private String safeErrorCode;
    @Column(name = "safe_error_message", length = 1000)
    private String safeErrorMessage;
    @Enumerated(EnumType.STRING)
    @Column(name = "safe_error_category", length = 32)
    private ExecutionErrorCategory safeErrorCategory;
    @Column(name = "safe_error_retryable")
    private Boolean safeErrorRetryable;
    @Enumerated(EnumType.STRING)
    @Column(name = "safe_error_phase", length = 32)
    private ExecutionFailurePhase safeErrorPhase;
    @Column(name = "safe_error_sql_state", length = 5)
    private String safeErrorSqlState;
    @Column(name = "safe_error_node_id", length = 100)
    private String safeErrorNodeId;
    @Column(name = "safe_error_node_type", length = 64)
    private String safeErrorNodeType;
    @Column(name = "safe_error_node_name", length = 200)
    private String safeErrorNodeName;
    @Column(name = "safe_error_diagnostic_id")
    private UUID safeErrorDiagnosticId;
    @Column(name = "affected_rows")
    private Long affectedRows;
    @Enumerated(EnumType.STRING)
    @Column(name = "quality_conclusion", length = 16)
    private QualityConclusion qualityConclusion;
    @Column(name = "quality_total_rules")
    private Long qualityTotalRules;
    @Column(name = "quality_passed_rules")
    private Long qualityPassedRules;
    @Column(name = "quality_failed_rules")
    private Long qualityFailedRules;
    @Column(name = "quality_skipped_rules")
    private Long qualitySkippedRules;
    @Column(name = "quality_checked_rows")
    private Long qualityCheckedRows;
    @Column(name = "queued_at", nullable = false, updatable = false)
    private Instant queuedAt;
    @Column(name = "submission_started_at")
    private Instant submissionStartedAt;
    @Column(name = "submitted_at")
    private Instant submittedAt;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "ended_at")
    private Instant endedAt;
    @Column(name = "last_observed_at")
    private Instant lastObservedAt;
    @Column(name = "observation_failure_since")
    private Instant observationFailureSince;
    @Column(name = "result_awaiting_since")
    private Instant resultAwaitingSince;
    @Column(name = "log_artifact_stored", nullable = false)
    private boolean logArtifactStored;
    @Column(name = "external_cleanup_completed", nullable = false)
    private boolean externalCleanupCompleted;

    protected DispatcherTaskExecution() {
    }

    public static DispatcherTaskExecution queue(
            SubmitExecutionCommand command,
            String fingerprint,
            ExecutionBackendType backendType
    ) {
        DispatcherTaskExecution execution = new DispatcherTaskExecution();
        execution.engineId = command.engineId();
        execution.executionId = command.executionId();
        execution.runId = command.runId();
        execution.taskId = command.taskId();
        execution.attempt = command.attempt();
        execution.taskType = command.taskType();
        execution.definitionVersion = command.definitionVersion();
        execution.requestFingerprint = fingerprint;
        execution.backendType = backendType;
        execution.state = DispatcherExecutionState.QUEUED;
        execution.manifestKey = command.artifacts().manifestKey();
        execution.manifestSha256 = command.artifacts().manifestSha256();
        execution.resultKey = command.artifacts().resultKey();
        execution.logKey = command.artifacts().logKey();
        execution.qualitySampleRuleIds = command.qualitySampleRuleIds().stream()
                .map(UUID::toString).collect(java.util.stream.Collectors.joining(","));
        execution.qualitySampleLimit = command.qualitySampleLimit();
        if (command.userJar() != null) {
            execution.userJarObjectKey = command.userJar().objectKey();
            execution.userJarSha256 = command.userJar().sha256();
            execution.userJarSizeBytes = command.userJar().sizeBytes();
        }
        execution.sparkConf = encodeSparkConf(command.sparkConf());
        execution.deadlineAt = command.deadlineAt();
        execution.queuedAt = Instant.now();
        return execution;
    }

    public static DispatcherTaskExecution queue(
            StartStreamingExecutionCommand command,
            String fingerprint,
            ExecutionBackendType backendType
    ) {
        DispatcherTaskExecution execution = new DispatcherTaskExecution();
        execution.engineId = command.engineId();
        execution.executionId = command.executionId();
        execution.runId = command.runId();
        execution.taskId = command.taskId();
        execution.attempt = command.attempt();
        execution.taskType = command.taskType();
        execution.definitionVersion = command.definitionVersion();
        execution.streamingDeploymentId = command.deploymentId();
        execution.checkpointKeyPrefix = command.checkpointKeyPrefix();
        execution.requestFingerprint = fingerprint;
        execution.backendType = backendType;
        execution.state = DispatcherExecutionState.QUEUED;
        execution.manifestKey = command.artifacts().manifestKey();
        execution.manifestSha256 = command.artifacts().manifestSha256();
        execution.resultKey = command.artifacts().resultKey();
        execution.logKey = command.artifacts().logKey();
        if (command.userJar() != null) {
            execution.userJarObjectKey = command.userJar().objectKey();
            execution.userJarSha256 = command.userJar().sha256();
            execution.userJarSizeBytes = command.userJar().sizeBytes();
        }
        execution.sparkConf = encodeSparkConf(command.sparkConf());
        execution.deadlineAt = null;
        execution.queuedAt = Instant.now();
        return execution;
    }

    public void beginSubmission() {
        require(DispatcherExecutionState.QUEUED);
        state = DispatcherExecutionState.SUBMITTING;
        submissionStartedAt = Instant.now();
    }

    public void submitted(String externalId, String trackingUrl) {
        require(DispatcherExecutionState.SUBMITTING);
        this.externalExecutionId = required(externalId);
        this.trackingUrl = optional(trackingUrl);
        submittedAt = Instant.now();
        state = cancelRequested || streamingStopRequestedAt != null
                ? DispatcherExecutionState.CANCEL_REQUESTED
                : DispatcherExecutionState.SUBMITTED;
    }

    public void running(Instant at) {
        if (state != DispatcherExecutionState.SUBMITTED && state != DispatcherExecutionState.RUNNING) return;
        state = DispatcherExecutionState.RUNNING;
        if (startedAt == null) startedAt = at == null ? Instant.now() : at;
        lastObservedAt = Instant.now();
        observationFailureSince = null;
    }

    public void requestCancel() {
        if (state.terminal() || state == DispatcherExecutionState.CANCEL_REQUESTED) return;
        cancelRequested = true;
        if (state == DispatcherExecutionState.QUEUED) {
            cancelled("取消发生在提交前");
        } else if (state == DispatcherExecutionState.SUBMITTED || state == DispatcherExecutionState.RUNNING) {
            state = DispatcherExecutionState.CANCEL_REQUESTED;
        }
    }

    public void requestForceTerminate() {
        if (state.terminal()) return;
        if (forceTerminateRequestedAt == null) forceTerminateRequestedAt = Instant.now();
        cancelRequested = true;
        if (state == DispatcherExecutionState.SUBMITTED || state == DispatcherExecutionState.RUNNING) {
            state = DispatcherExecutionState.CANCEL_REQUESTED;
        }
    }

    public void requestStreamingStop(int gracePeriodSeconds) {
        if ((taskType != ExecutionTaskType.SPARK_STREAMING_CANVAS
                && taskType != ExecutionTaskType.SPARK_STREAMING_JAR)
                || gracePeriodSeconds < 1 || gracePeriodSeconds > 600) {
            throw new IllegalArgumentException("实时停止参数无效");
        }
        if (state.terminal() || streamingStopRequestedAt != null) return;
        streamingStopRequestedAt = Instant.now();
        streamingForceStopAt = streamingStopRequestedAt.plusSeconds(gracePeriodSeconds);
        if (state == DispatcherExecutionState.QUEUED) {
            stopped(streamingStopRequestedAt);
        } else if (state == DispatcherExecutionState.SUBMITTED
                || state == DispatcherExecutionState.RUNNING) {
            state = DispatcherExecutionState.CANCEL_REQUESTED;
        }
    }

    private static String encodeSparkConf(List<SparkConfigurationEntry> values) {
        if (values == null || values.isEmpty()) return null;
        return values.stream().map(entry -> entry.name() + "="
                        + java.util.Base64.getEncoder().encodeToString(entry.value().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static List<SparkConfigurationEntry> decodeSparkConf(String value) {
        if (value == null || value.isBlank()) return List.of();
        return value.lines().map(line -> {
            int split = line.indexOf('=');
            return new SparkConfigurationEntry(line.substring(0, split), new String(
                    java.util.Base64.getDecoder().decode(line.substring(split + 1)),
                    java.nio.charset.StandardCharsets.UTF_8));
        }).toList();
    }

    public void succeed() { terminal(DispatcherExecutionState.SUCCESS, (String) null, null); }
    public void fail(String code, String message) { terminal(DispatcherExecutionState.FAILED, code, message); }
    public void fail(SafeExecutionError error) { terminal(DispatcherExecutionState.FAILED, error, Instant.now()); }
    public void timedOut(String message) { terminal(DispatcherExecutionState.TIMED_OUT, "EXECUTION_TIMEOUT", message); }
    public void timedOut(SafeExecutionError error) { terminal(DispatcherExecutionState.TIMED_OUT, error, Instant.now()); }
    public void cancelled(String message) { terminal(DispatcherExecutionState.CANCELLED, null, message); }
    public void cancelled(SafeExecutionError error) { terminal(DispatcherExecutionState.CANCELLED, error, Instant.now()); }
    public void stopped(Instant at) { terminal(DispatcherExecutionState.STOPPED, (SafeExecutionError) null, at); }
    public void lost(String message) { terminal(DispatcherExecutionState.LOST, "EXECUTION_LOST", message); }
    public void lost(SafeExecutionError error) { terminal(DispatcherExecutionState.LOST, error, Instant.now()); }

    public void completeFromRunner(
            DispatcherExecutionState target,
            Instant resultStartedAt,
            Instant resultEndedAt,
            Long resultAffectedRows,
            SafeExecutionError error
    ) {
        if (target == null || !target.terminal()) throw new IllegalArgumentException("Runner 终态无效");
        if (state.terminal()) return;
        // A verified Runner result is the authoritative source for task-level
        // start/end timestamps. Backend observations describe infrastructure
        // lifecycle and may be recorded later or come from a skewed clock.
        if (resultStartedAt != null) startedAt = resultStartedAt;
        affectedRows = resultAffectedRows;
        terminal(target, error, resultEndedAt);
    }

    public void applyQualitySummary(QualitySummary summary) {
        if (summary == null) return;
        qualityConclusion = summary.conclusion();
        qualityTotalRules = summary.totalRules();
        qualityPassedRules = summary.passedRules();
        qualityFailedRules = summary.failedRules();
        qualitySkippedRules = summary.skippedRules();
        qualityCheckedRows = summary.checkedRows();
        affectedRows = null;
    }

    public QualitySummary getQualitySummary() {
        if (qualityConclusion == null) return null;
        return new QualitySummary(
                qualityConclusion, qualityTotalRules, qualityPassedRules, qualityFailedRules,
                qualitySkippedRules, qualityCheckedRows);
    }

    public void awaitingResult(Instant at) {
        if (state.terminal()) return;
        if (resultAwaitingSince == null) resultAwaitingSince = at == null ? Instant.now() : at;
        lastObservedAt = Instant.now();
    }

    public void logArtifactStored() { logArtifactStored = true; }
    public void externalCleanupCompleted() { externalCleanupCompleted = true; }

    private void terminal(DispatcherExecutionState target, String code, String message) {
        terminal(target, code, message, Instant.now());
    }

    private void terminal(DispatcherExecutionState target, String code, String message, Instant terminalAt) {
        if (state.terminal()) return;
        state = target;
        safeErrorCode = optional(code);
        safeErrorMessage = truncate(optional(message), 1000);
        clearStructuredError();
        endedAt = terminalAt == null ? Instant.now() : terminalAt;
        lastObservedAt = Instant.now();
    }

    private void terminal(DispatcherExecutionState target, SafeExecutionError error, Instant terminalAt) {
        if (state.terminal()) return;
        state = target;
        safeErrorCode = error == null ? null : error.code();
        safeErrorMessage = error == null ? null : error.message();
        clearStructuredError();
        if (error != null) {
            safeErrorCategory = error.category();
            safeErrorRetryable = error.retryable();
            safeErrorPhase = error.phase();
            safeErrorSqlState = error.sqlState();
            safeErrorNodeId = error.nodeId();
            safeErrorNodeType = error.nodeType();
            safeErrorNodeName = error.nodeName();
            safeErrorDiagnosticId = error.diagnosticId();
        }
        endedAt = terminalAt == null ? Instant.now() : terminalAt;
        lastObservedAt = Instant.now();
    }

    private void clearStructuredError() {
        safeErrorCategory = null;
        safeErrorRetryable = null;
        safeErrorPhase = null;
        safeErrorSqlState = null;
        safeErrorNodeId = null;
        safeErrorNodeType = null;
        safeErrorNodeName = null;
        safeErrorDiagnosticId = null;
    }

    public long nextEventSequence() { return ++eventSequence; }
    public void observed() {
        lastObservedAt = Instant.now();
        observationFailureSince = null;
    }
    public void observationFailed() {
        if (observationFailureSince == null) observationFailureSince = Instant.now();
    }

    public void observeTrackingUrl(String value) {
        String normalized = optional(value);
        if (normalized != null) trackingUrl = truncate(normalized, 1000);
    }

    public UUID getEngineId() { return engineId; }
    public UUID getExecutionId() { return executionId; }
    public UUID getRunId() { return runId; }
    public UUID getTaskId() { return taskId; }
    public int getAttempt() { return attempt; }
    public ExecutionTaskType getTaskType() { return taskType; }
    public int getDefinitionVersion() { return definitionVersion; }
    public UUID getStreamingDeploymentId() { return streamingDeploymentId; }
    public String getCheckpointKeyPrefix() { return checkpointKeyPrefix; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public ExecutionBackendType getBackendType() { return backendType; }
    public DispatcherExecutionState getState() { return state; }
    public String getExternalExecutionId() { return externalExecutionId; }
    public String getTrackingUrl() { return trackingUrl; }
    public String getManifestKey() { return manifestKey; }
    public String getManifestSha256() { return manifestSha256; }
    public String getResultKey() { return resultKey; }
    public String getLogKey() { return logKey; }
    public List<UUID> getQualitySampleRuleIds() {
        return qualitySampleRuleIds == null || qualitySampleRuleIds.isBlank() ? List.of()
                : java.util.Arrays.stream(qualitySampleRuleIds.split(",")).map(UUID::fromString).toList();
    }
    public int getQualitySampleLimit() { return qualitySampleLimit == null ? 0 : qualitySampleLimit; }
    public ExecutionUserJarArtifact getUserJar() {
        return userJarObjectKey == null ? null
                : new ExecutionUserJarArtifact(userJarObjectKey, userJarSha256, userJarSizeBytes);
    }
    public List<SparkConfigurationEntry> getSparkConf() { return decodeSparkConf(sparkConf); }
    public Instant getDeadlineAt() { return deadlineAt; }
    public boolean isCancelRequested() { return cancelRequested; }
    public boolean isForceTerminateRequested() { return forceTerminateRequestedAt != null; }
    public Instant getForceTerminateRequestedAt() { return forceTerminateRequestedAt; }
    public boolean isStreamingStopRequested() { return streamingStopRequestedAt != null; }
    public Instant getStreamingForceStopAt() { return streamingForceStopAt; }
    public long getEventSequence() { return eventSequence; }
    public String getSafeErrorCode() { return safeErrorCode; }
    public String getSafeErrorMessage() { return safeErrorMessage; }
    public SafeExecutionError getSafeExecutionError() {
        if (safeErrorCode == null || safeErrorMessage == null || safeErrorCategory == null
                || safeErrorRetryable == null || safeErrorPhase == null || safeErrorDiagnosticId == null) {
            return null;
        }
        return new SafeExecutionError(
                safeErrorCode, safeErrorMessage, safeErrorCategory, safeErrorRetryable,
                safeErrorNodeId, safeErrorNodeType, safeErrorNodeName, safeErrorPhase,
                safeErrorSqlState, safeErrorDiagnosticId);
    }
    public Long getAffectedRows() { return affectedRows; }
    public Instant getQueuedAt() { return queuedAt; }
    public Instant getSubmissionStartedAt() { return submissionStartedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public Instant getLastObservedAt() { return lastObservedAt; }
    public Instant getObservationFailureSince() { return observationFailureSince; }
    public Instant getResultAwaitingSince() { return resultAwaitingSince; }
    public boolean isLogArtifactStored() { return logArtifactStored; }
    public boolean isExternalCleanupCompleted() { return externalCleanupCompleted; }

    private void require(DispatcherExecutionState expected) {
        if (state != expected) throw new IllegalStateException("执行状态要求 " + expected + "，实际为 " + state);
    }
    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("外部执行标识不能为空");
        return truncate(value.trim(), 300);
    }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String truncate(String value, int max) { return value == null ? null : value.substring(0, Math.min(max, value.length())); }
}
