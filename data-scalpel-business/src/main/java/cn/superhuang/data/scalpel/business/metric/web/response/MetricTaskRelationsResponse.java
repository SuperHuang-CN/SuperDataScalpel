package cn.superhuang.data.scalpel.business.metric.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.metric.domain.*;
import java.util.*;
import java.time.Instant;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
@Schema(description = "指定任务与业务指标之间根据输出模型和字段血缘解析出的关系。")
public record MetricTaskRelationsResponse(
        @Schema(description = "任务 UUID。")
        UUID taskId,
        @Schema(description = "当前任务类型专属定义的内容版本；尚未配置可分析定义时为空或 0，具体空值规则与任务类型一致。")
        Integer definitionVersion,
        @Schema(description = "当前任务关系解析结论：UNCONFIGURED 尚无可分析定义；NO_RESOLVABLE_OUTPUT 当前定义未解析出输出模型；MODEL_RELATIONS 已按当前输出模型筛选指标。")
        String resolution,
        @Schema(description = "与该任务匹配的业务指标分页结果。")
        PageResponse<MetricResponse> metrics,
        @Schema(description = "任务当前定义能够确认的输出模型列表。")
        List<cn.superhuang.data.scalpel.business.task.web.response.TaskRelatedModelResponse> outputModels,
        @Schema(description = "用于匹配指标结果字段的当前或历史任务血缘证据。")
        List<MetricFieldEvidenceResponse> fieldEvidence
) {}
