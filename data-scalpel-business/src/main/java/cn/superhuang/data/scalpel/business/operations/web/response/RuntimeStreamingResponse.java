package cn.superhuang.data.scalpel.business.operations.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.task.domain.*;
import cn.superhuang.data.scalpel.contract.execution.StreamingSourceKind;
import java.time.Instant;
import java.util.*;
@Schema(description = "运维工作台中的正式流式部署状态、查询进度和来源游标延迟。")
public record RuntimeStreamingResponse(
        @Schema(description = "流式部署 UUID。")
        UUID id,
        @Schema(description = "任务 UUID。")
        UUID taskId,
        @Schema(description = "实时任务当前名称；来源任务已删除时保留安全占位名称。")
        String taskName,
        @Schema(description = "任务类型，决定定义和执行协议。")
        TaskType taskType,
        @Schema(description = "来源任务当前是否仍存在；false 表示任务已删除，本响应仅展示部署时保存的信息。")
        boolean sourceExists,
        @Schema(description = "当前正式流式部署关联的任务运行 UUID；尚未启动时为空。")
        UUID currentRunId,
        @Schema(description = "承载当前正式流式部署的计算引擎 UUID；未分配时为空。")
        UUID computeEngineId,
        @Schema(description = "承载部署的计算引擎显示名称；未分配时为空。")
        String engineName,
        @Schema(description = "Dispatcher 观测到的实际部署状态，可能在启动、运行、停止、失败或终止状态间变化。")
        StreamingDeploymentActualState actualState,
        @Schema(description = "控制面期望部署最终达到的运行或停止状态；与 actualState 不一致表示仍在收敛。")
        StreamingDeploymentDesiredState desiredState,
        @Schema(description = "当前部署开始运行的时间，ISO-8601 UTC 时间戳；尚未启动成功时为空。")
        Instant startedAt,
        @Schema(description = "当前部署停止或异常终止的时间，ISO-8601 UTC 时间戳；仍在启动、运行或停止过程中时为空。")
        Instant stoppedAt,
        @Schema(description = "任一 Structured Streaming Query 最近一次进度时间，ISO-8601 UTC 时间戳；尚无进度时为空。")
        Instant lastProgressAt,
        @Schema(description = "最近进度是否超过允许时效；true 时速率、批次和游标信息不能代表当前实时状态。")
        boolean progressStale,
        @Schema(description = "可观测来源游标类型：JDBC_INCREMENTAL 增量 JDBC 或 TDENGINE_TMQ；当前部署没有可观测游标时为空。")
        StreamingSourceKind sourceKind,
        @Schema(description = "来源游标相对最新可消费位置的延迟，单位毫秒；无法观测时为空。该值是特定来源的技术延迟，不能直接当作统一业务数据新鲜度。")
        Long cursorLagMillis,
        @Schema(description = "最近一次从 Dispatcher 拉取并保存该部署状态的时间，ISO-8601 UTC 时间戳；尚未轮询时为空，可能早于本响应生成时间。")
        Instant pollTime,
        @Schema(description = "该部署内各 Spark Structured Streaming Query 的最近进度。")
        List<RuntimeStreamingQueryResponse> queries,
        @Schema(description = "最近一次实时运行异常结束的时间，ISO-8601 UTC 时间戳；没有已知异常时为空。")
        Instant lastErrorAt,
        @Schema(description = "经安全处理的错误摘要；没有错误时为空。")
        String errorSummary
) {}
