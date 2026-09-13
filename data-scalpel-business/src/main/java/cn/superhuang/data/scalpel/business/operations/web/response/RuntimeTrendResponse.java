package cn.superhuang.data.scalpel.business.operations.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import java.time.Instant;
@Schema(description = "指定时间桶和任务运行状态对应的运行数量趋势点。")
public record RuntimeTrendResponse(
        @Schema(description = "该趋势桶与查询范围裁剪后的起点，ISO-8601 UTC 时间戳，包含该时刻。")
        Instant from,
        @Schema(description = "该趋势桶与查询范围裁剪后的终点，ISO-8601 UTC 时间戳，不包含该时刻。")
        Instant to,
        @Schema(description = "该时间桶统计的任务运行状态，例如 QUEUED、RUNNING、SUCCESS、FAILED、TIMED_OUT 或 CANCELLED。")
        TaskRunStatus status,
        @Schema(description = "endedAt 落入该桶且最终状态等于 status 的 REAL 正式批处理运行数量。")
        long count
) {}
