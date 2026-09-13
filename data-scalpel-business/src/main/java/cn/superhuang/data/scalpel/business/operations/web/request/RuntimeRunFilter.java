package cn.superhuang.data.scalpel.business.operations.web.request;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.task.domain.TaskRunExecutionMode;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
@Schema(description = "运行工作台对任务运行列表追加的固定业务筛选条件")
public record RuntimeRunFilter(
        @Schema(description = "按任务名称进行不区分大小写的片段筛选。")
        @Size(max=150) String taskName,
        @Schema(description = "所属目录 UUID；位于根目录时为空。")
        UUID directoryId,
        @Schema(description = "是否只返回仍在排队或运行中的非终态记录。")
        boolean activeOnly,
        @Schema(description = "查询时间范围起点，包含该时刻。")
        Instant from,
        @Schema(description = "查询时间范围终点，不包含该时刻。")
        Instant to,
        @Schema(description = "时间范围作用字段：queuedAt 按排队时间，endedAt 按结束时间。")
        @Pattern(regexp="queuedAt|endedAt") String timeField,
        @Schema(description = "执行模式筛选；为空时同时包含普通运行与试运行。")
        TaskRunExecutionMode mode,
        @Schema(description = "是否只返回批处理运行，排除实时任务运行。")
        boolean batchOnly
) {}
