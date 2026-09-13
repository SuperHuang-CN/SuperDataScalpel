package cn.superhuang.data.scalpel.business.operations.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import java.util.*;
@Schema(description = "运行工作台中的任务数量、成功率和趋势指标。")
public record RuntimeTaskMetrics(
        @Schema(description = "查询时所有 REAL 正式运行中，状态为 QUEUED、RUNNING、CANCEL_REQUESTED 或 STOP_REQUESTED 的实例数量，按 TaskRunStatus 分组；不受 from/to 历史窗口限制，缺失键表示 0。")
        Map<TaskRunStatus, Long> current,
        @Schema(description = "endedAt 落入 [from,to) 的 REAL 正式批处理运行数量，按其最终 TaskRunStatus 分组；排除实时部署，缺失键表示 0。")
        Map<TaskRunStatus, Long> completed,
        @Schema(description = "统计窗口内任务执行成功但质量结论未通过的运行数量。")
        long qualityFailed,
        @Schema(description = "技术成功率，等于 SUCCESS / (SUCCESS + FAILED + TIMED_OUT)，范围 0 到 1；CANCELLED、STOPPED 和 SKIPPED 不进入分母，分母为 0 时为空。")
        Double successRate,
        @Schema(description = "按 endedAt 统计的 REAL 正式批处理终态趋势。范围不超过 3 天时按小时分桶，超过 3 天时按 UTC 自然日分桶；桶边界裁剪到 [from,to)，每个状态为独立数据点。")
        List<RuntimeTrendResponse> trend
) {}
