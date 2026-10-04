package cn.superhuang.data.scalpel.business.quality.web.response;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
@Schema(description="已发布模型最近正式有效质检结果的历史汇总，不证明当前数据或规则持续合格。")
public record ModelQualityStatisticsResponse(
 @Schema(description="快照采集时间，ISO UTC。") Instant collectedAt,
 @Schema(description="已发布模型总数，等于passed+failed+noResult。") long total,
 @Schema(description="最近正式有效结果通过的模型数。") long passed,
 @Schema(description="最近正式有效结果不通过的模型数。") long failed,
 @Schema(description="没有正式有效结果的模型数，包括仅执行失败的模型。") long noResult,
 @Schema(description="最近一次正式质检执行为FAILED或TIMED_OUT的模型数，与质量结果分类可能重叠。") long latestExecutionFailed,
 @Schema(description="所选有效结果最早的结束时间，无结果为空。") Instant oldestResultAt,
 @Schema(description="所选有效结果最晚的结束时间，无结果为空。") Instant newestResultAt
) {}
