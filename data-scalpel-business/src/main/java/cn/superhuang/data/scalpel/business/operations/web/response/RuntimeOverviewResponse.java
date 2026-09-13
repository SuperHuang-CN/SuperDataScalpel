package cn.superhuang.data.scalpel.business.operations.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import java.time.Instant;
@Schema(description = "运维工作台指定时间范围内的任务、引擎、告警和待处理信号概览。")
public record RuntimeOverviewResponse(
        @Schema(description = "历史指标查询范围起点，ISO-8601 UTC 时间戳，包含该时刻；未提供时默认为 to 前 24 小时。")
        Instant from,
        @Schema(description = "历史指标查询范围终点，ISO-8601 UTC 时间戳，不包含该时刻；未提供时使用本次查询时刻。")
        Instant to,
        @Schema(description = "本响应开始汇总时的服务端时间，ISO-8601 UTC 时间戳；当前量以该时刻附近的管理库快照为准。")
        Instant collectedAt,
        @Schema(description = "用户拥有 task.view 时返回任务运行汇总；无该权限时为空，不能将空值解释为零运行。")
        RuntimeTaskMetrics tasks,
        @Schema(description = "用户拥有 compute.engine.view 时返回计算引擎汇总；无该权限时为空，不能将空值解释为零引擎。")
        RuntimeEngineMetrics engines,
        @Schema(description = "当前未关闭的告警事件数量。")
        long openAlerts,
        @Schema(description = "Dispatcher 尚未消费的运行状态信号数量；暂时无法获取时为空。")
        Long pendingSignals,
        @Schema(description = "当前仍处于失败状态的告警通知投递数量；暂时无法获取时为空。")
        Long failedDeliveries
) {}
