package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunTriggerType;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunExecutionMode;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;

import java.time.Instant;
import java.util.UUID;
import cn.superhuang.data.scalpel.contract.quality.QualityConclusion;
import cn.superhuang.data.scalpel.contract.execution.UserJobMetricSnapshot;
import cn.superhuang.data.scalpel.contract.execution.UserJobStatus;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourceSpec;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;

@Schema(description = "任务一次执行的状态、固定定义版本、运行资源和结果摘要。")

public record TaskRunResponse(
        @Schema(description = "任务运行 UUID，用于查询状态、日志、血缘和停止执行。")
        UUID id,
        @Schema(description = "所属任务 UUID。")
        UUID taskId,
        @Schema(description = "工作流父级运行 UUID；只有工作流节点创建的子运行非空，顶层运行为空。")
        UUID parentRunId,
        @Schema(description = "工作流节点标识；非工作流子运行为空。")
        String workflowNodeId,
        @Schema(description = "触发本次运行的计划 UUID；仅 triggerType=SCHEDULED 时非空。计划随后修改或删除不会改变该快照。")
        UUID scheduleId,
        @Schema(description = "所属流式部署 UUID；正式或试运行实时任务非空，批任务和工作流为空。")
        UUID streamingDeploymentId,
        @Schema(description = "任务类型，决定定义和执行协议。")
        TaskType taskType,
        @Schema(description = "平台为 Dispatcher 外部执行生成的稳定 UUID；本地 SQL、工作流父运行、跳过或尚未分发时为空。")
        UUID externalExecutionId,
        @Schema(description = "创建外部执行时固定的计算引擎 UUID；LOCAL_SQL、WORKFLOW 父运行、跳过或提交前失败时为空。任务后续改绑引擎不会改写该值。")
        UUID computeEngineId,
        @Schema(description = "执行后端确认后返回的应用标识；本地执行、不适用或尚未确认启动时为空。")
        String backendApplicationId,
        @Schema(description = "执行引擎提供的跟踪页面地址；本地执行、后端未提供或尚未启动时为空，客户端不应假定地址永久有效。")
        String trackingUrl,
        @Schema(description = "Dispatcher 为本次外部执行分配的尝试序号，从 1 开始；尚未提交或本地执行模式不使用该序号时为空。")
        Integer attempt,
        @Schema(description = "本次响应固定引用的任务定义版本；定义更新后旧运行仍保留原版本。")
        int definitionVersion,
        @Schema(description = "运行触发来源：MANUAL 用户手动，SCHEDULED Cron 调度计划，WORKFLOW 工作流节点。试运行由 executionMode 表达，不是独立触发来源。")
        TaskRunTriggerType triggerType,
        @Schema(description = "执行模式：REAL 正式运行，TRIAL 受限试运行，SIMULATED 模拟运行；非 REAL 的结果和状态不进入默认正式运行统计与告警。")
        TaskRunExecutionMode executionMode,
        @Schema(description = "Canvas 试运行的目标节点稳定 ID；仅 SPARK_CANVAS 且 executionMode=TRIAL 时非空，不是数据库 UUID。")
        String canvasTrialTargetNodeId,
        @Schema(description = "Canvas 试运行请求选择的目标逻辑表名；非 Canvas 试运行时为空，不表示预览已经生成。")
        String canvasTrialTableName,
        @Schema(description = "Canvas 试运行请求选择的列数；非 Canvas 试运行时为空，不等于实际返回行数。")
        Integer canvasTrialSelectedColumnCount,
        @Schema(description = "运行状态：QUEUED 排队，RUNNING 运行中，请求取消或停止后进入相应过渡态，最终为 STOPPED、SUCCESS、FAILED、TIMED_OUT、CANCELLED 或 SKIPPED。")
        TaskRunStatus status,
        @Schema(description = "Cron 计划原定触发时间，ISO-8601 UTC 时间戳；非 SCHEDULED 运行为空，可能早于实际 queuedAt。")
        Instant scheduledFireAt,
        @Schema(description = "TaskRun 与可靠提交记录进入 Admin 管理库的时间，ISO-8601 UTC 时间戳。")
        Instant queuedAt,
        @Schema(description = "执行器确认开始执行的时间，ISO-8601 UTC 时间戳；仍在排队或未启动即进入终态时为空。")
        Instant startedAt,
        @Schema(description = "进入 STOPPED、SUCCESS、FAILED、TIMED_OUT、CANCELLED 或 SKIPPED 终态的时间，ISO-8601 UTC 时间戳；非终态时为空。")
        Instant endedAt,
        @Schema(description = "基于本次固定超时配置计算的执行截止时间，ISO-8601 UTC 时间戳；没有执行超时上限时为空。")
        Instant deadlineAt,
        @Schema(description = "执行结果上报的处理或写入行数；具体粒度由任务类型决定，无法统计、尚未完成或没有统一行数语义时为空。")
        Long affectedRows,
        @Schema(description = "本次运行固定的用户 JAR 文件名；非 JAR 任务为空。")
        String userJarFileName,
        @Schema(description = "本次运行固定的用户 JAR SHA-256；非 JAR 任务为空。")
        String userJarSha256,
        @Schema(description = "本次运行固定的用户 JAR 大小，单位字节；非 JAR 任务为空。")
        Long userJarSizeBytes,
        @Schema(description = "JAR 任务提交时固定使用的 Spark Driver 与 Executor 资源规格；非 JAR 或旧版本运行快照无法解析时为空。")
        SparkExecutionResourceSpec executionResources,
        @Schema(description = "质量任务总体结论；非质量任务或未完成时为空。")
        QualityConclusion qualityConclusion,
        @Schema(description = "模型质检运行时固化的规则总数，等于通过、失败和跳过数量之和；非质检或未形成质量结果时为空。")
        Long qualityTotalRules,
        @Schema(description = "模型质检中已执行且指标未超过阈值的规则数；非质检或未形成质量结果时为空。")
        Long qualityPassedRules,
        @Schema(description = "模型质检中已执行且指标超过阈值的规则数；大于 0 时 qualityConclusion=FAILED，但 TaskRun 技术状态仍可为 SUCCESS。")
        Long qualityFailedRules,
        @Schema(description = "模型质检中因规则停用、失效或依赖不可用而未执行的规则数；非质检或未形成结果时为空。")
        Long qualitySkippedRules,
        @Schema(description = "模型质检从目标模型读取的总行数，也是行级违规比例的分母；非质检或未形成结果时为空。")
        Long qualityCheckedRows,
        @Schema(description = "用户作业通过 SDK 上报的最新状态和指标；未上报时为空。")
        UserJobObservabilityResponse userJobObservability,
        @Schema(description = "当前运行状态的可读补充说明，例如排队、跳过、取消或失败原因；没有补充信息时为空。")
        String message,
        @Schema(description = "经安全处理的详细错误说明；无错误时为空。")
        String errorDetail,
        @Schema(description = "结构化执行错误；未失败或旧运行记录可能为空。")
        TaskRunExecutionErrorResponse executionError,
        @Schema(description = "运行数据库记录创建时间，ISO-8601 UTC 时间戳；通常接近 queuedAt，但两者语义不同。")
        Instant createdAt,
        @Schema(description = "运行状态、后端标识、进度、结果或错误最后持久化时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {

    public static TaskRunResponse from(TaskRun run) {
        return new TaskRunResponse(
                run.getId(), run.getTaskId(), run.getParentRunId(), run.getWorkflowNodeId(), run.getScheduleId(), run.getStreamingDeploymentId(),
                run.getTaskType(), run.getExternalExecutionId(),
                run.getComputeEngineId(), run.getBackendApplicationId(), run.getTrackingUrl(),
                run.getAttempt(), run.getDefinitionVersion(), run.getTriggerType(),
                run.getExecutionMode(), run.getCanvasTrialTargetNodeId(), run.getCanvasTrialTableName(),
                run.getCanvasTrialSelectedColumnCount(), run.getStatus(), run.getScheduledFireAt(),
                run.getQueuedAt(), run.getStartedAt(),
                run.getEndedAt(), run.getDeadlineAt(), run.getAffectedRows(),
                run.getUserJarFileName(), run.getUserJarSha256(), run.getUserJarSizeBytes(),
                executionResourcesFrom(run),
                run.getQualityConclusion(), run.getQualityTotalRules(), run.getQualityPassedRules(),
                run.getQualityFailedRules(), run.getQualitySkippedRules(), run.getQualityCheckedRows(),
                observabilityFrom(run),
                run.getMessage(), run.getErrorDetail(),
                TaskRunExecutionErrorResponse.from(run), run.getCreatedAt(), run.getUpdatedAt()
        );
    }

    private static final ObjectMapper OBSERVABILITY_MAPPER = JsonMapper.builderWithJackson2Defaults()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public static UserJobObservabilityResponse observabilityFrom(TaskRun run) {
        if (run == null || run.getUserJobMetrics() == null || run.getUserJobMetrics().isBlank()) return null;
        List<UserJobMetricSnapshot> metrics;
        try {
            metrics = OBSERVABILITY_MAPPER.readValue(
                    run.getUserJobMetrics(), new TypeReference<List<UserJobMetricSnapshot>>() { });
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法读取用户作业指标快照", exception);
        }
        UserJobStatus status = run.getUserJobPhase() == null ? null : new UserJobStatus(
                run.getUserJobPhase(), run.getUserJobStatusMessage(), run.getUserJobStatusAt());
        return new UserJobObservabilityResponse(status, metrics);
    }

    private static SparkExecutionResourceSpec executionResourcesFrom(TaskRun run) {
        if (run == null || (!run.getTaskType().isJar()) || run.getDefinitionSnapshot() == null) return null;
        try {
            return OBSERVABILITY_MAPPER.readTree(run.getDefinitionSnapshot())
                    .path("executionResources").isMissingNode()
                    ? null
                    : OBSERVABILITY_MAPPER.treeToValue(
                            OBSERVABILITY_MAPPER.readTree(run.getDefinitionSnapshot()).path("executionResources"),
                            SparkExecutionResourceSpec.class);
        } catch (RuntimeException exception) {
            // The detailed run API stays available for snapshots created before
            // resource pinning. The persisted snapshot remains the authority.
            return null;
        }
    }
}
