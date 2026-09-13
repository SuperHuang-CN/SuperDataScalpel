package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageCoverage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "一次任务运行的血缘分析与快照发布结果。")

public record TaskRunLineageResponse(
        @Schema(description = "任务运行 UUID。")
        UUID runId,
        @Schema(description = "血缘摄取状态，与任务运行状态相互独立：PENDING 尚无摄取记录且运行未结束；NOT_AVAILABLE 运行已结束但无可摄取结果；QUEUED、RUNNING、SUCCEEDED、FAILED、STALE 为持久化摄取状态。")
        String status,
        @Schema(description = "本次运行产生的字段血缘覆盖程度；尚未成功分析时为空。")
        LineageCoverage coverage,
        @Schema(description = "是否已将本次血缘发布为该任务当前查询快照；摄取成功也可能因版本或覆盖策略而为 false。")
        boolean publishedSnapshot,
        @Schema(description = "成功分析出的字段流数量；分析未完成时为空。")
        Integer flowCount,
        @Schema(description = "血缘告警数量；分析未完成时为空。")
        Integer warningCount,
        @Schema(description = "血缘分析的非阻断告警；没有或尚未分析时为空列表。")
        List<Warning> warnings,
        @Schema(description = "稳定错误码；未失败时为空。")
        String errorCode,
        @Schema(description = "经安全处理的详细错误说明；无错误时为空。")
        String errorDetail,
        @Schema(description = "血缘摄取进入持久化终态的时间，ISO-8601 UTC 时间戳；PENDING、NOT_AVAILABLE、QUEUED 或 RUNNING 时为空。")
        Instant completedAt
) {
    public TaskRunLineageResponse {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    @Schema(description = "血缘分析过程中产生的非阻断告警。")

    public record Warning(
            @Schema(description = "稳定血缘告警码，用于识别表达式降级、字段缺失或覆盖不完整等原因。")
            String code,
            @Schema(description = "说明本次运行血缘为何降级、缺失或无法确认的可读信息。")
            String message,
            @Schema(description = "告警关联的稳定字段流标识；全局告警为空。")
            String flowKey
    ) {
    }
}
