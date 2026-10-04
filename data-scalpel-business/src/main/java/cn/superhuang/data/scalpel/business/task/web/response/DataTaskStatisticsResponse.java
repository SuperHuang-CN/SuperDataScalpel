package cn.superhuang.data.scalpel.business.task.web.response;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.*;
@Schema(description = "任务定义数量，包含各生命周期状态，不等同投产数量。")
public record DataTaskStatisticsResponse(
    @Schema(description = "采集时间，ISO UTC。") Instant collectedAt,
    @Schema(description = "任务定义总数，单位个。") long total,
    @Schema(description = "全部原始任务类型计数，包含零值。") List<TypeCount> types
) { @Schema(name = "DataTaskTypeStatistics", description = "任务类型计数。")
public record TypeCount(@Schema(description = "任务定义类型。") cn.superhuang.data.scalpel.business.task.domain.TaskType type,
 @Schema(description = "定义数，单位个，最小0。") long count) {} }

