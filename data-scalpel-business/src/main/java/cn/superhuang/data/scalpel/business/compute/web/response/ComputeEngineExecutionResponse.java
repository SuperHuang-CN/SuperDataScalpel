package cn.superhuang.data.scalpel.business.compute.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunTriggerType;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionState;
import cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/** Dispatcher execution enriched with Admin task-run information when it has already synchronized. */
@Schema(description = "Dispatcher 执行账本记录，并尽可能关联 Admin 中的任务和 TaskRun；即使事件尚未同步回 Admin，账本记录也会返回。")
public record ComputeEngineExecutionResponse(
        @Schema(description = "Dispatcher 执行记录 UUID，也是 Admin 与 Dispatcher 关联一次执行的稳定标识。")
        UUID executionId,
        @Schema(description = "提交给 Dispatcher 的运行关联 UUID；用于判断远端执行是否对应当前 Admin TaskRun。")
        UUID executionRunId,
        @Schema(description = "任务 UUID。")
        UUID taskId,
        @Schema(description = "从 Admin 当前任务记录补充的名称；任务记录已不存在时为空。")
        String taskName,
        @Schema(description = "提交时固化的执行任务类型：Spark Canvas、实时 Canvas、模型质检、Spark JAR 或实时 JAR。")
        ExecutionTaskType taskType,
        @Schema(description = "本次执行使用的任务定义版本，不代表任务当前最新版本。")
        int definitionVersion,
        @Schema(description = "Dispatcher 账本中的执行状态，从 QUEUED、SUBMITTING、SUBMITTED、RUNNING、CANCEL_REQUESTED 进入终态 SUCCESS、FAILED、TIMED_OUT、CANCELLED、STOPPED 或 LOST。")
        DispatcherExecutionState dispatcherState,
        @Schema(description = "匹配到的 Admin TaskRun 实体 UUID；尚未同步或运行关联 UUID 不一致时为空。")
        UUID taskRunId,
        @Schema(description = "Admin TaskRun 当前状态；未匹配到对应 TaskRun 时为空，可能暂时滞后于 dispatcherState。")
        TaskRunStatus taskRunStatus,
        @Schema(description = "Admin 记录的运行触发来源；未匹配到对应 TaskRun 时为空。")
        TaskRunTriggerType triggerType,
        @Schema(description = "Admin 是否存在 executionId 对应且运行关联 UUID 一致的 TaskRun；false 表示仍以 Dispatcher 账本为事实，不能据此判断执行不存在。")
        @JsonProperty("synchronized") boolean synchronizedWithAdmin,
        @Schema(description = "Docker 容器、YARN Application 或 Kubernetes SparkApplication 等外部后端执行标识；尚未提交成功时为空。")
        String backendExecutionId,
        @Schema(description = "执行引擎提供的跟踪页面地址；不可用时为空。")
        String trackingUrl,
        @Schema(description = "本次执行的绝对超时截止时间；未配置超时时为空。")
        Instant deadlineAt,
        @Schema(description = "Dispatcher 接受请求并进入队列的时间。")
        Instant queuedAt,
        @Schema(description = "Dispatcher 开始调用外部后端提交应用的时间；尚未开始提交时为空。")
        Instant submissionStartedAt,
        @Schema(description = "外部后端接受应用后的时间；尚未提交成功时为空。")
        Instant submittedAt,
        @Schema(description = "实际开始时间；尚未开始时为空。")
        Instant startedAt,
        @Schema(description = "结束时间；尚未结束时为空。")
        Instant endedAt,
        @Schema(description = "Dispatcher 最近一次观测外部后端状态的时间；尚未开始观测时为空。")
        Instant lastObservedAt,
        @Schema(description = "面向调用方的稳定错误码；执行未失败或没有可公开错误时为空。")
        String errorCode,
        @Schema(description = "已脱敏的执行错误说明；执行未失败或没有可公开错误时为空。")
        String errorMessage,
        @Schema(description = "QUEUED 范围查询中的一基全局队列位置；其他查询范围为空。")
        Long queuePosition
) {
}
