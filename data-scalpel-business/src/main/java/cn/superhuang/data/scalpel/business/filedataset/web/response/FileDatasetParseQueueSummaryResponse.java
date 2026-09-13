package cn.superhuang.data.scalpel.business.filedataset.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "文件准备和表来源解析后台队列的配置、积压、运行和近期终态统计。")

public record FileDatasetParseQueueSummaryResponse(
        @Schema(description = "后台解析队列是否允许领取并执行新作业。")
        boolean queueEnabled,
        @Schema(description = "配置的后台解析工作线程并发数。")
        int configuredWorkerConcurrency,
        @Schema(description = "已完成解析作业的历史保留天数。")
        int historyRetentionDays,
        @Schema(description = "当前状态为 QUEUED 的作业总数，包含尚未到可执行时间的重试作业。")
        long queuedCount,
        @Schema(description = "当前已到 availableAt、可以立即被工作线程领取的排队作业数。")
        long runnableQueuedCount,
        @Schema(description = "因重试退避而等待 availableAt 的排队作业数。")
        long retryWaitingCount,
        @Schema(description = "当前状态为 RUNNING 的作业数；可能包含租约已过期但尚未被恢复调度器处理的记录。")
        long runningCount,
        @Schema(description = "当前保留记录中状态为 SUCCEEDED 的作业数；历史清理任务按 historyRetentionDays 定期删除终态记录。")
        long succeededCount,
        @Schema(description = "当前保留记录中状态为 FAILED 的作业数，包括不可重试错误和耗尽最大尝试次数两类。")
        long failedCount,
        @Schema(description = "当前保留记录中状态为 CANCELLED 的作业数。")
        long cancelledCount,
        @Schema(description = "当前 QUEUED 作业中最早的首次入队时间，ISO-8601 UTC 时间戳；没有排队作业时为空。该值不是最早 availableAt，可能包含正在重试退避的旧作业。")
        Instant oldestQueuedAt,
        @Schema(description = "当前 RUNNING 作业中最早的最近一次尝试开始时间，ISO-8601 UTC 时间戳；没有运行记录时为空，可能包含租约已过期而尚未恢复的作业。")
        Instant oldestRunningAt,
        @Schema(description = "本次队列统计生成时间，ISO-8601 UTC 时间戳。")
        Instant generatedAt
) {
}
