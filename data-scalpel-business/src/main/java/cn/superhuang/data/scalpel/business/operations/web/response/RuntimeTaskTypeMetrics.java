package cn.superhuang.data.scalpel.business.operations.web.response;
import cn.superhuang.data.scalpel.business.task.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
@Schema(description="一种任务类型的正式运行摘要；定义数、运行次数、部署数不能相加。")
public record RuntimeTaskTypeMetrics(
 @Schema(description="原始任务类型。") TaskType type,
 @Schema(description="全部当前正式活动运行，按状态计数，不受历史时间窗限制，缺失键为0。") Map<TaskRunStatus,Long> current,
 @Schema(description="概览历史窗口内按结束时间统计的正式运行数，实时类型为空，缺失键为0。") Map<TaskRunStatus,Long> completed,
 @Schema(description="当前正式实时部署范围按实际状态计数；非实时类型为空，缺失键为0。") Map<StreamingDeploymentActualState,Long> deployments
) {}
