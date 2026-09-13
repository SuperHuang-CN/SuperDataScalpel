package cn.superhuang.data.scalpel.business.metric.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "指标工作簿导入完成后的创建、更新和未变化数量。")

public record MetricImportResultResponse(
        @Schema(description = "本次导入新建的数量。")
        int created,
        @Schema(description = "本次导入更新的数量。")
        int updated,
        @Schema(description = "内容与当前系统一致、未执行写入的行数。")
        int unchanged
) {}
