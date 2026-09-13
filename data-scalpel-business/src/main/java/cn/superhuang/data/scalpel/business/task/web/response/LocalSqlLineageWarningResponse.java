package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
/** Non-blocking loss of confidence reported by Local SQL lineage analysis. */
@Schema(description = "Local SQL 血缘分析的非阻断告警，表示血缘结果可能不完整。")
public record LocalSqlLineageWarningResponse(
        @Schema(description = "稳定告警码，用于识别无法解析表达式、通配列或来源不完整等降级原因。")
        String code,
        @Schema(description = "说明 Local SQL 血缘为何降级或无法确认的可读信息。")
        String message,
        @Schema(description = "关联输出列的零基序号；全局告警为空。")
        Integer outputOrdinal
) {
}
