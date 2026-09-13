package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.Instant;
import java.util.UUID;

/** Read-only execution row used by Dispatcher and Admin operational lists. */
public record DispatcherExecutionSummaryResponse(
        @JsonPropertyDescription("Dispatcher 执行记录 UUID，也是 Admin 与 Dispatcher 关联一次执行的稳定标识。")
        UUID executionId,
        @JsonPropertyDescription("提交方生成的运行关联 UUID；同一 executionId 的后续事件必须保持该值不变。")
        UUID runId,
        @JsonPropertyDescription("任务 UUID。")
        UUID taskId,
        @JsonPropertyDescription("提交时固化的执行任务类型：SPARK_CANVAS、SPARK_STREAMING_CANVAS、SPARK_MODEL_QUALITY、SPARK_JAR 或 SPARK_STREAMING_JAR。")
        ExecutionTaskType taskType,
        @JsonPropertyDescription("本次执行使用的任务定义版本，不代表任务当前最新版本。")
        int definitionVersion,
        @JsonPropertyDescription("Dispatcher 账本状态：QUEUED、SUBMITTING、SUBMITTED、RUNNING、CANCEL_REQUESTED，或终态 SUCCESS、FAILED、TIMED_OUT、CANCELLED、STOPPED、LOST。")
        DispatcherExecutionState state,
        @JsonPropertyDescription("Docker 容器、YARN Application 或 Kubernetes SparkApplication 等外部后端执行标识；尚未提交成功时为空。")
        String externalExecutionId,
        @JsonPropertyDescription("执行引擎提供的跟踪页面地址；不可用时为空。")
        String trackingUrl,
        @JsonPropertyDescription("本次执行的绝对超时截止时间；未配置超时时为空。")
        Instant deadlineAt,
        @JsonPropertyDescription("Dispatcher 接受请求并进入队列的时间。")
        Instant queuedAt,
        @JsonPropertyDescription("Dispatcher 开始调用外部后端提交应用的时间；尚未开始提交时为空。")
        Instant submissionStartedAt,
        @JsonPropertyDescription("外部后端接受应用后的时间；尚未提交成功时为空。")
        Instant submittedAt,
        @JsonPropertyDescription("开始时间；尚未开始时为空。")
        Instant startedAt,
        @JsonPropertyDescription("结束时间；尚未结束时为空。")
        Instant endedAt,
        @JsonPropertyDescription("Dispatcher 最近一次观测外部后端状态的时间；尚未开始观测时为空。")
        Instant lastObservedAt,
        @JsonPropertyDescription("稳定错误码；未失败时为空。")
        String errorCode,
        @JsonPropertyDescription("安全错误说明；未失败时为空。")
        String errorMessage,
        @JsonPropertyDescription("QUEUED 范围查询中的一基全局队列位置；其他查询范围为空。")
        Long queuePosition
) {
}
