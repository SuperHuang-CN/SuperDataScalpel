package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.contract.execution.ExecutionErrorCategory;
import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;
import cn.superhuang.data.scalpel.contract.execution.CanvasTrialSpec;
import cn.superhuang.data.scalpel.contract.execution.SafeExecutionError;
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
import cn.superhuang.data.scalpel.contract.quality.QualityConclusion;
import cn.superhuang.data.scalpel.contract.quality.QualitySummary;

/** Immutable-definition execution history for one manual or scheduled task run. */
@Entity
@Table(
        name = "task_run",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_task_run_schedule_fire",
                columnNames = {"schedule_id", "scheduled_fire_at"}
        ),
        indexes = {
                @Index(name = "idx_task_run_task_queued", columnList = "task_id,queued_at"),
                @Index(name = "idx_task_run_task_status", columnList = "task_id,status"),
                @Index(
                        name = "idx_task_run_canvas_trial_target_queued",
                        columnList = "task_id,canvas_trial_target_node_id,queued_at"
                ),
                @Index(name = "idx_task_run_quality_model_queued", columnList = "quality_target_model_id,queued_at"),
                @Index(name = "idx_task_run_quality_model_ended", columnList = "quality_target_model_id,status,ended_at")
        }
)
public class TaskRun extends BaseEntity {

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "schedule_id", updatable = false)
    private UUID scheduleId;

    @Column(name = "streaming_deployment_id", updatable = false)
    private UUID streamingDeploymentId;

    @Column(name = "definition_version", nullable = false, updatable = false)
    private int definitionVersion;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "definition_snapshot", nullable = false, updatable = false)
    private String definitionSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", length = 32, updatable = false)
    private TaskType taskType;

    @Column(name = "external_execution_id", updatable = false)
    private UUID externalExecutionId;

    /** Stable execution identity shared by the manifest, Kafka and Dispatcher. */
    @Column(name = "execution_run_id", unique = true, updatable = false)
    private UUID executionRunId;

    @Column(name = "compute_engine_id", updatable = false)
    private UUID computeEngineId;

    @Column(name = "command_topic_snapshot", length = 249, updatable = false)
    private String commandTopicSnapshot;

    @Column(name = "last_dispatcher_event_sequence", nullable = false)
    private long lastDispatcherEventSequence;

    @Column(name = "backend_application_id", length = 300)
    private String backendApplicationId;

    @Column(name = "tracking_url", length = 1000)
    private String trackingUrl;

    @Column(name = "execution_attempt", updatable = false)
    private Integer attempt;

    @Column(name = "execution_deadline_at", updatable = false)
    private Instant deadlineAt;

    @Column(name = "manifest_object_key", length = 500)
    private String manifestObjectKey;

    @Column(name = "result_object_key", length = 500)
    private String resultObjectKey;

    @Column(name = "log_object_key", length = 500)
    private String logObjectKey;

    @Column(name = "user_jar_file_name", length = 255, updatable = false)
    private String userJarFileName;

    @Column(name = "user_jar_sha256", length = 64, updatable = false)
    private String userJarSha256;

    @Column(name = "user_jar_size_bytes", updatable = false)
    private Long userJarSizeBytes;

    @Column(name = "run_user_jar_object_key", length = 500)
    private String runUserJarObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_jar_cleanup_status", length = 16)
    private TaskRunJarCleanupStatus userJarCleanupStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, updatable = false, length = 32)
    private TaskRunTriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false, updatable = false, length = 32)
    private TaskRunExecutionMode executionMode;

    /** Immutable Canvas-node identity captured for a persisted trial-run session. */
    @Column(name = "canvas_trial_target_node_id", length = 100, updatable = false)
    private String canvasTrialTargetNodeId;

    @Column(name = "canvas_trial_table_name", length = 255, updatable = false)
    private String canvasTrialTableName;

    @Column(name = "canvas_trial_selected_column_count", updatable = false)
    private Integer canvasTrialSelectedColumnCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskRunStatus status;

    @Column(name = "queued_at", nullable = false, updatable = false)
    private Instant queuedAt;

    @Column(name = "scheduled_fire_at", updatable = false)
    private Instant scheduledFireAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "affected_rows")
    private Long affectedRows;

    @Column(name = "user_job_phase", length = 100)
    private String userJobPhase;

    @Column(name = "user_job_status_message", length = 1000)
    private String userJobStatusMessage;

    @Column(name = "user_job_status_at")
    private Instant userJobStatusAt;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "user_job_metrics")
    private String userJobMetrics;

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

    /** Immutable target-model identity captured when a model-quality run is created. */
    @Column(name = "quality_target_model_id", updatable = false)
    private UUID qualityTargetModelId;

    /** Time at which the executable and skipped rule snapshots for this run were fixed. */
    @Column(name = "quality_rule_snapshot_at", updatable = false)
    private Instant qualityRuleSnapshotAt;

    @Column(length = 1000)
    private String message;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "error_detail")
    private String errorDetail;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_category", length = 32)
    private ExecutionErrorCategory errorCategory;

    @Column(name = "error_retryable")
    private Boolean errorRetryable;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_phase", length = 32)
    private ExecutionFailurePhase errorPhase;

    @Column(name = "error_sql_state", length = 5)
    private String errorSqlState;

    @Column(name = "error_node_id", length = 100)
    private String errorNodeId;

    @Column(name = "error_node_type", length = 64)
    private String errorNodeType;

    @Column(name = "error_node_name", length = 200)
    private String errorNodeName;

    @Column(name = "error_diagnostic_id")
    private UUID errorDiagnosticId;

    protected TaskRun() {
    }

    private TaskRun(UUID taskId, int definitionVersion, String definitionSnapshot) {
        if (taskId == null || definitionVersion < 1 || definitionSnapshot == null || definitionSnapshot.isBlank()) {
            throw new IllegalArgumentException("任务运行快照无效");
        }
        this.taskId = taskId;
        this.definitionVersion = definitionVersion;
        this.definitionSnapshot = definitionSnapshot;
        this.taskType = TaskType.LOCAL_SQL;
        this.triggerType = TaskRunTriggerType.MANUAL;
        this.executionMode = TaskRunExecutionMode.REAL;
        this.status = TaskRunStatus.QUEUED;
        this.queuedAt = Instant.now();
    }

    public static TaskRun queue(UUID taskId, int definitionVersion, String definitionSnapshot) {
        return new TaskRun(taskId, definitionVersion, definitionSnapshot);
    }

    private static TaskRun queueCanvas(
            UUID taskId,
            int definitionVersion,
            String definitionSnapshot,
            UUID executionId,
            int attempt,
            Instant deadlineAt
    ) {
        TaskRun run = new TaskRun(taskId, definitionVersion, definitionSnapshot);
        if (executionId == null || attempt != 1 || deadlineAt == null) {
            throw new IllegalArgumentException("Canvas 任务运行标识无效");
        }
        run.taskType = TaskType.SPARK_CANVAS;
        run.externalExecutionId = executionId;
        run.attempt = attempt;
        run.deadlineAt = deadlineAt;
        return run;
    }

    private static TaskRun queueDispatchedCanvas(
            UUID taskId,
            int definitionVersion,
            String definitionSnapshot,
            UUID executionId,
            int attempt,
            Instant deadlineAt,
            UUID computeEngineId,
            String commandTopicSnapshot
    ) {
        TaskRun run = queueCanvas(taskId, definitionVersion, definitionSnapshot, executionId, attempt, deadlineAt);
        if (computeEngineId == null || blank(commandTopicSnapshot)) {
            throw new IllegalArgumentException("Canvas 任务分发路由无效");
        }
        run.computeEngineId = computeEngineId;
        run.commandTopicSnapshot = commandTopicSnapshot.trim();
        return run;
    }

    public static TaskRun queueDispatchedCanvas(
            UUID runId,
            UUID taskId,
            int definitionVersion,
            String definitionSnapshot,
            UUID executionId,
            int attempt,
            Instant deadlineAt,
            UUID computeEngineId,
            String commandTopicSnapshot
    ) {
        TaskRun run = queueDispatchedCanvas(
                taskId, definitionVersion, definitionSnapshot, executionId, attempt, deadlineAt,
                computeEngineId, commandTopicSnapshot);
        if (runId == null) {
            throw new IllegalArgumentException("Canvas 任务 runId 不能为空");
        }
        run.executionRunId = runId;
        return run;
    }

    public static TaskRun queueDispatchedCanvasTrial(
            UUID runId, UUID taskId, int definitionVersion, String definitionSnapshot,
            UUID executionId, int attempt, Instant deadlineAt, UUID computeEngineId,
            String commandTopicSnapshot
    ) {
        TaskRun run = queueDispatchedCanvas(
                runId, taskId, definitionVersion, definitionSnapshot, executionId, attempt,
                deadlineAt, computeEngineId, commandTopicSnapshot);
        run.executionMode = TaskRunExecutionMode.TRIAL;
        return run;
    }

    public void captureCanvasTrialContext(CanvasTrialSpec trialSpec) {
        if (getTaskType() != TaskType.SPARK_CANVAS
                || executionMode != TaskRunExecutionMode.TRIAL
                || trialSpec == null
                || canvasTrialTargetNodeId != null
                || canvasTrialTableName != null
                || canvasTrialSelectedColumnCount != null) {
            throw new IllegalStateException("Canvas 试运行上下文无效");
        }
        canvasTrialTargetNodeId = trialSpec.targetNodeId();
        canvasTrialTableName = trialSpec.tableName();
        canvasTrialSelectedColumnCount = trialSpec.columnNames().size();
    }

    public static TaskRun queueScheduledDispatchedCanvas(
            UUID runId,
            UUID taskId,
            UUID scheduleId,
            Instant scheduledFireAt,
            int definitionVersion,
            String definitionSnapshot,
            UUID executionId,
            int attempt,
            Instant deadlineAt,
            UUID computeEngineId,
            String commandTopicSnapshot
    ) {
        if (scheduleId == null || scheduledFireAt == null) {
            throw new IllegalArgumentException("Canvas 定时运行触发信息不能为空");
        }
        TaskRun run = queueDispatchedCanvas(
                runId, taskId, definitionVersion, definitionSnapshot, executionId, attempt,
                deadlineAt, computeEngineId, commandTopicSnapshot);
        run.triggerType = TaskRunTriggerType.SCHEDULED;
        run.scheduleId = scheduleId;
        run.scheduledFireAt = scheduledFireAt;
        return run;
    }

    public static TaskRun queueDispatchedModelQuality(
            UUID runId,
            UUID taskId,
            int definitionVersion,
            String definitionSnapshot,
            UUID executionId,
            int attempt,
            Instant deadlineAt,
            UUID computeEngineId,
            String commandTopicSnapshot
    ) {
        TaskRun run = queueDispatchedCanvas(
                runId, taskId, definitionVersion, definitionSnapshot, executionId, attempt,
                deadlineAt, computeEngineId, commandTopicSnapshot);
        run.taskType = TaskType.SPARK_MODEL_QUALITY;
        return run;
    }

    public void captureModelQualityContext(UUID modelId, Instant ruleSnapshotAt) {
        if (getTaskType() != TaskType.SPARK_MODEL_QUALITY || modelId == null
                || qualityTargetModelId != null || qualityRuleSnapshotAt != null) {
            throw new IllegalStateException("模型质检运行上下文无效");
        }
        qualityTargetModelId = modelId;
        qualityRuleSnapshotAt = ruleSnapshotAt;
    }

    public static TaskRun queueScheduledDispatchedModelQuality(
            UUID runId,
            UUID taskId,
            UUID scheduleId,
            Instant scheduledFireAt,
            int definitionVersion,
            String definitionSnapshot,
            UUID executionId,
            int attempt,
            Instant deadlineAt,
            UUID computeEngineId,
            String commandTopicSnapshot
    ) {
        TaskRun run = queueDispatchedModelQuality(
                runId, taskId, definitionVersion, definitionSnapshot, executionId, attempt,
                deadlineAt, computeEngineId, commandTopicSnapshot);
        if (scheduleId == null || scheduledFireAt == null) {
            throw new IllegalArgumentException("质检定时运行触发信息不能为空");
        }
        run.triggerType = TaskRunTriggerType.SCHEDULED;
        run.scheduleId = scheduleId;
        run.scheduledFireAt = scheduledFireAt;
        return run;
    }

    public static TaskRun queueDispatchedSparkJar(
            UUID runId, UUID taskId, int definitionVersion, String definitionSnapshot,
            UUID executionId, int attempt, Instant deadlineAt, UUID computeEngineId,
            String commandTopicSnapshot
    ) {
        TaskRun run = queueDispatchedCanvas(runId, taskId, definitionVersion, definitionSnapshot,
                executionId, attempt, deadlineAt, computeEngineId, commandTopicSnapshot);
        run.taskType = TaskType.SPARK_JAR;
        return run;
    }

    public static TaskRun queueDispatchedSparkJarTrial(
            UUID runId, UUID taskId, int definitionVersion, String definitionSnapshot,
            UUID executionId, int attempt, Instant deadlineAt, UUID computeEngineId,
            String commandTopicSnapshot
    ) {
        TaskRun run = queueDispatchedSparkJar(runId, taskId, definitionVersion, definitionSnapshot,
                executionId, attempt, deadlineAt, computeEngineId, commandTopicSnapshot);
        run.executionMode = TaskRunExecutionMode.TRIAL;
        return run;
    }

    public static TaskRun queueScheduledDispatchedSparkJar(
            UUID runId, UUID taskId, UUID scheduleId, Instant scheduledFireAt,
            int definitionVersion, String definitionSnapshot, UUID executionId, int attempt,
            Instant deadlineAt, UUID computeEngineId, String commandTopicSnapshot
    ) {
        TaskRun run = queueDispatchedSparkJar(runId, taskId, definitionVersion, definitionSnapshot,
                executionId, attempt, deadlineAt, computeEngineId, commandTopicSnapshot);
        if (scheduleId == null || scheduledFireAt == null) {
            throw new IllegalArgumentException("Spark JAR 定时运行触发信息不能为空");
        }
        run.triggerType = TaskRunTriggerType.SCHEDULED;
        run.scheduleId = scheduleId;
        run.scheduledFireAt = scheduledFireAt;
        return run;
    }

    public void attachUserJar(String fileName, String sha256, long sizeBytes, String runObjectKey) {
        requireStatus(TaskRunStatus.QUEUED);
        if (taskType == null || !taskType.isJar() || blank(fileName) || blank(sha256)
                || sizeBytes < 1 || blank(runObjectKey) || runUserJarObjectKey != null) {
            throw new IllegalStateException("用户 JAR 制品不能写入当前运行实例");
        }
        userJarFileName = fileName.trim();
        userJarSha256 = sha256.trim();
        userJarSizeBytes = sizeBytes;
        runUserJarObjectKey = runObjectKey.trim();
        userJarCleanupStatus = TaskRunJarCleanupStatus.PENDING;
    }

    public void userJarCleanupCompleted() {
        if (taskType == null || !taskType.isJar() || runUserJarObjectKey == null) return;
        userJarCleanupStatus = TaskRunJarCleanupStatus.COMPLETED;
        runUserJarObjectKey = null;
    }

    public static TaskRun queueDispatchedStreaming(
            UUID runId,
            UUID taskId,
            UUID streamingDeploymentId,
            int definitionVersion,
            String definitionSnapshot,
            UUID executionId,
            int attempt,
            UUID computeEngineId,
            String commandTopicSnapshot
    ) {
        return queueDispatchedStreaming(runId, taskId, streamingDeploymentId, definitionVersion,
                definitionSnapshot, executionId, attempt, computeEngineId, commandTopicSnapshot,
                TaskType.SPARK_STREAMING_CANVAS);
    }

    public static TaskRun queueDispatchedStreaming(
            UUID runId,
            UUID taskId,
            UUID streamingDeploymentId,
            int definitionVersion,
            String definitionSnapshot,
            UUID executionId,
            int attempt,
            UUID computeEngineId,
            String commandTopicSnapshot,
            TaskType streamingTaskType
    ) {
        if (runId == null || streamingDeploymentId == null || executionId == null || attempt < 1
                || computeEngineId == null || blank(commandTopicSnapshot)
                || streamingTaskType == null || !streamingTaskType.isStreaming()) {
            throw new IllegalArgumentException("实时 Canvas 任务运行标识无效");
        }
        TaskRun run = new TaskRun(taskId, definitionVersion, definitionSnapshot);
        run.taskType = streamingTaskType;
        run.streamingDeploymentId = streamingDeploymentId;
        run.externalExecutionId = executionId;
        run.executionRunId = runId;
        run.attempt = attempt;
        run.computeEngineId = computeEngineId;
        run.commandTopicSnapshot = commandTopicSnapshot.trim();
        run.deadlineAt = null;
        return run;
    }

    public static TaskRun queueDispatchedStreamingTrial(
            UUID runId,
            UUID taskId,
            UUID streamingDeploymentId,
            int definitionVersion,
            String definitionSnapshot,
            UUID executionId,
            int attempt,
            Instant deadlineAt,
            UUID computeEngineId,
            String commandTopicSnapshot
    ) {
        if (deadlineAt == null) {
            throw new IllegalArgumentException("实时试运行截止时间不能为空");
        }
        TaskRun run = queueDispatchedStreaming(
                runId, taskId, streamingDeploymentId, definitionVersion, definitionSnapshot,
                executionId, attempt, computeEngineId, commandTopicSnapshot,
                TaskType.SPARK_STREAMING_JAR);
        run.executionMode = TaskRunExecutionMode.TRIAL;
        run.deadlineAt = deadlineAt;
        return run;
    }

    public void attachArtifacts(String manifestObjectKey, String resultObjectKey, String logObjectKey) {
        requireStatus(TaskRunStatus.QUEUED);
        if ((taskType != TaskType.SPARK_CANVAS && taskType != TaskType.SPARK_STREAMING_CANVAS
                && taskType != TaskType.SPARK_STREAMING_JAR
                && taskType != TaskType.SPARK_MODEL_QUALITY && taskType != TaskType.SPARK_JAR)
                || this.manifestObjectKey != null
                || blank(manifestObjectKey) || blank(resultObjectKey) || blank(logObjectKey)) {
            throw new IllegalStateException("Canvas 任务制品不能写入当前运行实例");
        }
        this.manifestObjectKey = manifestObjectKey;
        this.resultObjectKey = resultObjectKey;
        this.logObjectKey = logObjectKey;
    }

    public void useTaskType(TaskType taskType) {
        if (status == null || status == TaskRunStatus.QUEUED
                || status == TaskRunStatus.STOPPED || status == TaskRunStatus.SUCCESS
                || status == TaskRunStatus.FAILED || status == TaskRunStatus.TIMED_OUT
                || status == TaskRunStatus.CANCELLED || status == TaskRunStatus.SKIPPED) {
            this.taskType = java.util.Objects.requireNonNull(taskType, "任务类型不能为空");
            return;
        }
        throw new IllegalStateException("当前运行状态不能修改任务类型");
    }

    public static TaskRun scheduledSuccess(
            UUID taskId,
            UUID scheduleId,
            int definitionVersion,
            String definitionSnapshot,
            Instant scheduledFireAt
    ) {
        TaskRun run = scheduled(taskId, scheduleId, definitionVersion, definitionSnapshot, scheduledFireAt);
        run.status = TaskRunStatus.SUCCESS;
        run.message = "定时触发成功（模拟执行，未访问数据源）";
        return run;
    }

    public static TaskRun scheduledSkipped(
            UUID taskId,
            UUID scheduleId,
            int definitionVersion,
            String definitionSnapshot,
            Instant scheduledFireAt
    ) {
        TaskRun run = scheduled(taskId, scheduleId, definitionVersion, definitionSnapshot, scheduledFireAt);
        run.status = TaskRunStatus.SKIPPED;
        run.message = "定时触发已跳过：当前任务存在正在执行的实例";
        return run;
    }

    public static TaskRun scheduledCanvasSkipped(
            UUID taskId,
            UUID scheduleId,
            int definitionVersion,
            String definitionSnapshot,
            Instant scheduledFireAt,
            String message
    ) {
        TaskRun run = scheduledCanvasTerminal(
                taskId, scheduleId, definitionVersion, definitionSnapshot, scheduledFireAt);
        Instant now = run.queuedAt;
        run.status = TaskRunStatus.SKIPPED;
        run.startedAt = now;
        run.endedAt = now;
        run.message = normalizeMessage(message == null
                ? "定时触发已跳过：当前任务存在正在执行的实例"
                : message);
        return run;
    }

    public static TaskRun scheduledModelQualitySkipped(
            UUID taskId,
            UUID scheduleId,
            int definitionVersion,
            String definitionSnapshot,
            Instant scheduledFireAt,
            String message
    ) {
        TaskRun run = scheduledCanvasSkipped(
                taskId, scheduleId, definitionVersion, definitionSnapshot, scheduledFireAt, message);
        run.taskType = TaskType.SPARK_MODEL_QUALITY;
        return run;
    }

    public static TaskRun scheduledSparkJarSkipped(
            UUID taskId, UUID scheduleId, int definitionVersion, String definitionSnapshot,
            Instant scheduledFireAt, String message
    ) {
        TaskRun run = scheduledCanvasSkipped(taskId, scheduleId, definitionVersion,
                definitionSnapshot, scheduledFireAt, message);
        run.taskType = TaskType.SPARK_JAR;
        return run;
    }

    public static TaskRun failedScheduledCanvasSubmission(
            UUID taskId,
            UUID scheduleId,
            int definitionVersion,
            String definitionSnapshot,
            Instant scheduledFireAt,
            SafeExecutionError error
    ) {
        if (error == null) {
            throw new IllegalArgumentException("Canvas 定时提交错误不能为空");
        }
        TaskRun run = scheduledCanvasTerminal(
                taskId, scheduleId, definitionVersion, definitionSnapshot, scheduledFireAt);
        run.fail(error);
        return run;
    }

    public void start() {
        if (status == TaskRunStatus.RUNNING) return;
        requireStatus(TaskRunStatus.QUEUED);
        status = TaskRunStatus.RUNNING;
        startAt(Instant.now());
    }

    public void startAt(Instant value) {
        if (status == TaskRunStatus.RUNNING && startedAt != null) return;
        if (status != TaskRunStatus.QUEUED && status != TaskRunStatus.RUNNING) return;
        status = TaskRunStatus.RUNNING;
        startedAt = value == null ? Instant.now() : value;
    }

    public void succeed(long affectedRows) {
        requireStatus(TaskRunStatus.RUNNING);
        status = TaskRunStatus.SUCCESS;
        this.affectedRows = affectedRows;
        message = "执行成功";
        endedAt = Instant.now();
    }

    public void externalSucceed(Long affectedRows, Instant startedAt, Instant endedAt) {
        externalSucceed(affectedRows, startedAt, endedAt, null);
    }

    public void externalSucceed(
            Long affectedRows,
            Instant startedAt,
            Instant endedAt,
            QualitySummary qualitySummary
    ) {
        if (status != TaskRunStatus.QUEUED && status != TaskRunStatus.RUNNING
                && status != TaskRunStatus.CANCEL_REQUESTED && status != TaskRunStatus.STOP_REQUESTED) return;
        status = TaskRunStatus.SUCCESS;
        this.affectedRows = affectedRows;
        this.startedAt = this.startedAt == null ? startedAt : this.startedAt;
        this.endedAt = endedAt == null ? Instant.now() : endedAt;
        this.message = "执行成功";
        if (qualitySummary != null) {
            qualityConclusion = qualitySummary.conclusion();
            qualityTotalRules = qualitySummary.totalRules();
            qualityPassedRules = qualitySummary.passedRules();
            qualityFailedRules = qualitySummary.failedRules();
            qualitySkippedRules = qualitySummary.skippedRules();
            qualityCheckedRows = qualitySummary.checkedRows();
            this.affectedRows = null;
            this.message = qualityConclusion == QualityConclusion.PASSED ? "质检通过" : "质检未通过";
        }
    }

    public void fail(String message, String errorDetail) {
        if (status != TaskRunStatus.QUEUED && status != TaskRunStatus.RUNNING
                && status != TaskRunStatus.CANCEL_REQUESTED && status != TaskRunStatus.STOP_REQUESTED) {
            return;
        }
        status = TaskRunStatus.FAILED;
        this.message = normalizeMessage(message);
        this.errorDetail = normalizeErrorDetail(errorDetail);
        endedAt = Instant.now();
    }

    public void fail(SafeExecutionError error) {
        if (error == null) {
            fail("执行失败", null);
            return;
        }
        fail(error.message(), error.code());
        applyExecutionError(error);
    }

    public void fail(
            SafeExecutionError error,
            Long affectedRows,
            Instant startedAt,
            Instant endedAt
    ) {
        fail(error);
        if (status != TaskRunStatus.FAILED) return;
        this.affectedRows = affectedRows;
        this.startedAt = this.startedAt == null ? startedAt : this.startedAt;
        this.endedAt = endedAt == null ? this.endedAt : endedAt;
    }

    public void timeout(String message, String errorDetail) {
        if (status != TaskRunStatus.QUEUED && status != TaskRunStatus.RUNNING
                && status != TaskRunStatus.CANCEL_REQUESTED && status != TaskRunStatus.STOP_REQUESTED) {
            return;
        }
        status = TaskRunStatus.TIMED_OUT;
        this.message = normalizeMessage(message);
        this.errorDetail = normalizeErrorDetail(errorDetail);
        endedAt = Instant.now();
    }

    public void timeout(SafeExecutionError error) {
        if (error == null) {
            timeout("执行超时", null);
            return;
        }
        timeout(error.message(), error.code());
        applyExecutionError(error);
    }

    public void timeout(
            SafeExecutionError error,
            Long affectedRows,
            Instant startedAt,
            Instant endedAt
    ) {
        timeout(error);
        if (status != TaskRunStatus.TIMED_OUT) return;
        this.affectedRows = affectedRows;
        this.startedAt = this.startedAt == null ? startedAt : this.startedAt;
        this.endedAt = endedAt == null ? this.endedAt : endedAt;
    }

    public void requestCancel() {
        if (taskType != null && taskType.isStreaming()) {
            throw new IllegalStateException("实时任务请使用停止操作");
        }
        if (status == TaskRunStatus.CANCEL_REQUESTED) return;
        if (status != TaskRunStatus.QUEUED && status != TaskRunStatus.RUNNING) {
            throw new IllegalStateException("任务运行状态不允许取消：" + status);
        }
        status = TaskRunStatus.CANCEL_REQUESTED;
    }

    public void cancel(String message, Instant startedAt, Instant endedAt) {
        if (status != TaskRunStatus.QUEUED && status != TaskRunStatus.RUNNING
                && status != TaskRunStatus.CANCEL_REQUESTED && status != TaskRunStatus.STOP_REQUESTED) return;
        status = TaskRunStatus.CANCELLED;
        this.startedAt = this.startedAt == null ? startedAt : this.startedAt;
        this.endedAt = endedAt == null ? Instant.now() : endedAt;
        this.message = normalizeMessage(message == null ? "任务执行已取消" : message);
    }

    public void cancel(SafeExecutionError error, Instant startedAt, Instant endedAt) {
        cancel(error == null ? "任务执行已取消" : error.message(), startedAt, endedAt);
        if (error != null) applyExecutionError(error);
    }

    public void cancel(
            SafeExecutionError error,
            Long affectedRows,
            Instant startedAt,
            Instant endedAt
    ) {
        cancel(error, startedAt, endedAt);
        if (status == TaskRunStatus.CANCELLED) this.affectedRows = affectedRows;
    }

    public void requestStop() {
        if (taskType == null || !taskType.isStreaming()) {
            throw new IllegalStateException("只有实时任务运行可以停止");
        }
        if (status == TaskRunStatus.STOP_REQUESTED) return;
        if (status != TaskRunStatus.QUEUED && status != TaskRunStatus.RUNNING) {
            throw new IllegalStateException("任务运行状态不允许停止：" + status);
        }
        status = TaskRunStatus.STOP_REQUESTED;
    }

    public void stop(String message, Instant stoppedAt) {
        if ((taskType == null || !taskType.isStreaming())
                || (status != TaskRunStatus.QUEUED && status != TaskRunStatus.RUNNING
                && status != TaskRunStatus.STOP_REQUESTED)) return;
        status = TaskRunStatus.STOPPED;
        endedAt = stoppedAt == null ? Instant.now() : stoppedAt;
        this.message = normalizeMessage(message == null ? "实时任务已停止" : message);
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getStreamingDeploymentId() {
        return streamingDeploymentId;
    }

    public TaskType getTaskType() {
        return taskType == null ? TaskType.LOCAL_SQL : taskType;
    }

    public UUID getExternalExecutionId() {
        return externalExecutionId;
    }

    public UUID getExecutionRunId() { return executionRunId; }

    public UUID getComputeEngineId() { return computeEngineId; }

    public String getCommandTopicSnapshot() { return commandTopicSnapshot; }

    public long getLastDispatcherEventSequence() { return lastDispatcherEventSequence; }

    public String getBackendApplicationId() { return backendApplicationId; }

    public String getTrackingUrl() { return trackingUrl; }

    public void recordDispatcherEvent(long sequence, String backendApplicationId, String trackingUrl) {
        if (sequence <= lastDispatcherEventSequence) {
            throw new IllegalArgumentException("Dispatcher 事件序号没有递增");
        }
        lastDispatcherEventSequence = sequence;
        if (!blank(backendApplicationId)) this.backendApplicationId = truncate(backendApplicationId, 300);
        if (!blank(trackingUrl)) this.trackingUrl = truncate(trackingUrl, 1000);
    }

    public void recordUserJobObservability(
            String phase,
            String statusMessage,
            Instant statusAt,
            String metricsJson
    ) {
        if (getTaskType() != TaskType.SPARK_JAR && getTaskType() != TaskType.SPARK_STREAMING_JAR) {
            throw new IllegalStateException("只有 Spark JAR 任务可以记录用户作业观测快照");
        }
        if (phase == null) {
            userJobPhase = null;
            userJobStatusMessage = null;
            userJobStatusAt = null;
        } else {
            if (!phase.matches("[A-Za-z][A-Za-z0-9._-]{0,99}")
                    || statusMessage == null || statusMessage.isBlank() || statusMessage.length() > 1000
                    || statusAt == null) {
                throw new IllegalArgumentException("用户作业状态无效");
            }
            userJobPhase = phase;
            userJobStatusMessage = statusMessage;
            userJobStatusAt = statusAt;
        }
        if (metricsJson == null || metricsJson.isBlank()) {
            throw new IllegalArgumentException("用户作业指标快照不能为空");
        }
        userJobMetrics = metricsJson;
    }

    public Integer getAttempt() {
        return attempt;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    public String getManifestObjectKey() {
        return manifestObjectKey;
    }

    public String getResultObjectKey() {
        return resultObjectKey;
    }

    public String getLogObjectKey() {
        return logObjectKey;
    }

    public String getUserJarFileName() { return userJarFileName; }
    public String getUserJarSha256() { return userJarSha256; }
    public Long getUserJarSizeBytes() { return userJarSizeBytes; }
    public String getRunUserJarObjectKey() { return runUserJarObjectKey; }
    public TaskRunJarCleanupStatus getUserJarCleanupStatus() { return userJarCleanupStatus; }
    public String getUserJobPhase() { return userJobPhase; }
    public String getUserJobStatusMessage() { return userJobStatusMessage; }
    public Instant getUserJobStatusAt() { return userJobStatusAt; }
    public String getUserJobMetrics() { return userJobMetrics; }

    public UUID getScheduleId() {
        return scheduleId;
    }

    public int getDefinitionVersion() {
        return definitionVersion;
    }

    public String getDefinitionSnapshot() {
        return definitionSnapshot;
    }

    public TaskRunTriggerType getTriggerType() {
        return triggerType;
    }

    public TaskRunExecutionMode getExecutionMode() {
        return executionMode;
    }

    public String getCanvasTrialTargetNodeId() { return canvasTrialTargetNodeId; }

    public String getCanvasTrialTableName() { return canvasTrialTableName; }

    public Integer getCanvasTrialSelectedColumnCount() { return canvasTrialSelectedColumnCount; }

    public TaskRunStatus getStatus() {
        return status;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public Instant getScheduledFireAt() {
        return scheduledFireAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public Long getAffectedRows() {
        return affectedRows;
    }

    public QualityConclusion getQualityConclusion() { return qualityConclusion; }
    public Long getQualityTotalRules() { return qualityTotalRules; }
    public Long getQualityPassedRules() { return qualityPassedRules; }
    public Long getQualityFailedRules() { return qualityFailedRules; }
    public Long getQualitySkippedRules() { return qualitySkippedRules; }
    public Long getQualityCheckedRows() { return qualityCheckedRows; }
    public UUID getQualityTargetModelId() { return qualityTargetModelId; }
    public Instant getQualityRuleSnapshotAt() { return qualityRuleSnapshotAt; }

    public String getMessage() {
        return message;
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    public String getErrorCode() { return errorCode; }

    public ExecutionErrorCategory getErrorCategory() { return errorCategory; }

    public Boolean getErrorRetryable() { return errorRetryable; }

    public ExecutionFailurePhase getErrorPhase() { return errorPhase; }

    public String getErrorSqlState() { return errorSqlState; }

    public String getErrorNodeId() { return errorNodeId; }

    public String getErrorNodeType() { return errorNodeType; }

    public String getErrorNodeName() { return errorNodeName; }

    public UUID getErrorDiagnosticId() { return errorDiagnosticId; }

    private void applyExecutionError(SafeExecutionError error) {
        errorCode = error.code();
        errorCategory = error.category();
        errorRetryable = error.retryable();
        errorPhase = error.phase();
        errorSqlState = error.sqlState();
        errorNodeId = error.nodeId();
        errorNodeType = error.nodeType();
        errorNodeName = error.nodeName();
        errorDiagnosticId = error.diagnosticId();
    }

    private void requireStatus(TaskRunStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("任务运行状态不允许当前操作：" + status);
        }
    }

    private static TaskRun scheduled(
            UUID taskId,
            UUID scheduleId,
            int definitionVersion,
            String definitionSnapshot,
            Instant scheduledFireAt
    ) {
        if (taskId == null || scheduleId == null || scheduledFireAt == null
                || definitionVersion < 1 || definitionSnapshot == null || definitionSnapshot.isBlank()) {
            throw new IllegalArgumentException("定时任务运行快照无效");
        }
        Instant now = Instant.now();
        TaskRun run = new TaskRun();
        run.taskId = taskId;
        run.scheduleId = scheduleId;
        run.definitionVersion = definitionVersion;
        run.definitionSnapshot = definitionSnapshot;
        run.triggerType = TaskRunTriggerType.SCHEDULED;
        run.executionMode = TaskRunExecutionMode.SIMULATED;
        run.scheduledFireAt = scheduledFireAt;
        run.queuedAt = now;
        run.startedAt = now;
        run.endedAt = now;
        return run;
    }

    private static TaskRun scheduledCanvasTerminal(
            UUID taskId,
            UUID scheduleId,
            int definitionVersion,
            String definitionSnapshot,
            Instant scheduledFireAt
    ) {
        if (taskId == null || scheduleId == null || scheduledFireAt == null
                || definitionVersion < 0 || definitionSnapshot == null || definitionSnapshot.isBlank()) {
            throw new IllegalArgumentException("Canvas 定时任务运行快照无效");
        }
        TaskRun run = new TaskRun();
        run.taskId = taskId;
        run.scheduleId = scheduleId;
        run.definitionVersion = definitionVersion;
        run.definitionSnapshot = definitionSnapshot;
        run.taskType = TaskType.SPARK_CANVAS;
        run.triggerType = TaskRunTriggerType.SCHEDULED;
        run.executionMode = TaskRunExecutionMode.REAL;
        run.status = TaskRunStatus.QUEUED;
        run.scheduledFireAt = scheduledFireAt;
        run.queuedAt = Instant.now();
        return run;
    }

    private static String normalizeMessage(String value) {
        if (value == null || value.isBlank()) {
            return "任务执行失败";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private static String normalizeErrorDetail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= 4000 ? value : value.substring(0, 4000);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String value, int length) {
        String normalized = value.trim();
        return normalized.substring(0, Math.min(length, normalized.length()));
    }
}
