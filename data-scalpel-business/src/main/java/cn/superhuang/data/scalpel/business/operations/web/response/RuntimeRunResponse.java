package cn.superhuang.data.scalpel.business.operations.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.task.domain.*;
import cn.superhuang.data.scalpel.contract.quality.QualityConclusion;
import java.time.Instant;
import java.util.UUID;
@Schema(description = "运维工作台中的任务运行摘要，保留来源任务删除后的诊断信息。")
public record RuntimeRunResponse(
        @Schema(description = "任务运行 UUID。")
        UUID id,
        @Schema(description = "任务 UUID。")
        UUID taskId,
        @Schema(description = "工作流父运行 UUID；独立任务运行时为空。")
        UUID parentRunId,
        @Schema(description = "该子运行对应的工作流节点 id；独立任务运行时为空。")
        String workflowNodeId,
        @Schema(description = "运行所引用任务的显示名称；来源任务已删除时使用保存的快照或占位名称。")
        String taskName,
        @Schema(description = "所属目录 UUID；位于根目录时为空。")
        UUID directoryId,
        @Schema(description = "来源任务当前是否仍存在；false 表示任务已删除，本响应仅展示运行时保存的快照信息。")
        boolean sourceExists,
        @Schema(description = "任务类型，决定定义和执行协议。")
        TaskType taskType,
        @Schema(description = "实际承载本次 Spark 执行的计算引擎 UUID；本地 SQL 等模式可为空。")
        UUID computeEngineId,
        @Schema(description = "执行时保存的计算引擎显示名称；未使用计算引擎时为空。")
        String engineName,
        @Schema(description = "实时运行关联的 Deployment UUID；批任务和工作流父运行为空。")
        UUID streamingDeploymentId,
        @Schema(description = "运行状态：QUEUED 排队，RUNNING 运行中，CANCEL/STOP_REQUESTED 已请求终止，STOPPED/SUCCESS/FAILED/TIMED_OUT/CANCELLED/SKIPPED 为终态。")
        TaskRunStatus status,
        @Schema(description = "执行模式：REAL 正式运行；TRIAL 试运行；SIMULATED 模拟运行。非正式运行的结果和 Checkpoint 与 REAL 隔离，且默认不进入运行统计和告警。")
        TaskRunExecutionMode executionMode,
        @Schema(description = "运行触发来源：MANUAL 用户手动，SCHEDULED 调度计划，WORKFLOW 工作流节点；试运行由 executionMode 表达，不是触发来源。")
        TaskRunTriggerType triggerType,
        @Schema(description = "运行进入 Admin 队列的时间，ISO-8601 UTC 时间戳。")
        Instant queuedAt,
        @Schema(description = "执行器确认开始执行的时间，ISO-8601 UTC 时间戳；仍在排队或未启动即结束时为空。")
        Instant startedAt,
        @Schema(description = "运行进入终态的时间，ISO-8601 UTC 时间戳；非终态时为空。")
        Instant endedAt,
        @Schema(description = "模型质检运行的总体结论；非质检任务或尚未完成时为空。")
        QualityConclusion qualityConclusion,
        @Schema(description = "模型质检运行中判定为 FAILED 的规则数量；非质检任务、尚未形成质量结果或技术执行失败时为空。")
        Long qualityFailedRules,
        @Schema(description = "稳定错误码；没有错误时为空。")
        String errorCode,
        @Schema(description = "可关联内部安全诊断记录的 UUID；没有诊断记录时为空。")
        UUID diagnosticId
) {}
