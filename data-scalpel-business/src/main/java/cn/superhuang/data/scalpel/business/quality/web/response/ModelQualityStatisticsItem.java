package cn.superhuang.data.scalpel.business.quality.web.response;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import cn.superhuang.data.scalpel.contract.quality.QualityConclusion;
@Schema(description="已发布模型的最近正式有效结果与最新正式执行状态。")
public record ModelQualityStatisticsItem(
 @Schema(description="模型UUID。") UUID modelId,
 @Schema(description="模型当前名称。") String modelName,
 @Schema(description="最近有效正式运行UUID，无结果为空。") UUID runId,
 @Schema(description="历史检查结论，无有效结果为空。") QualityConclusion conclusion,
 @Schema(description="有效结果结束时间，ISO UTC；无结果为空。") Instant endedAt,
 @Schema(description="检查规则快照时间，ISO UTC；无结果为空。") Instant ruleSnapshotAt,
 @Schema(description="最近正式执行是否失败或超时，不改写历史质量结论。") boolean latestExecutionFailed
) {}
