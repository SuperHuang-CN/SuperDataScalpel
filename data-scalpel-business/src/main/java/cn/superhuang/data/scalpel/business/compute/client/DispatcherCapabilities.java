package cn.superhuang.data.scalpel.business.compute.client;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dispatcher 对执行控制和恢复能力的声明；该声明描述功能是否受当前部署支持，不表示依赖当前均已就绪。")
public record DispatcherCapabilities(
        @Schema(description = "是否支持取消排队或运行中的执行。")
        boolean cancellation,
        @Schema(description = "是否支持收集并读取 Runner 日志。")
        boolean logCollection,
        @Schema(description = "Dispatcher 重启后是否能从持久化账本恢复并继续协调未终止执行。")
        boolean restartReconciliation,
        @Schema(description = "当前部署是否支持 Spark 实时任务。")
        boolean streaming,
        @Schema(description = "当前部署是否为实时任务提供持久化 Checkpoint。")
        boolean durableCheckpoint
) {
    public DispatcherCapabilities(
            boolean cancellation,
            boolean logCollection,
            boolean restartReconciliation
    ) {
        this(cancellation, logCollection, restartReconciliation, false, false);
    }
}
