package cn.superhuang.data.scalpel.business.metric.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "指标工作簿单行中一个字段的导入前后差异。")

public record MetricImportChangeResponse(
        @Schema(description = "发生变化的模板列名或指标字段路径。")
        String column,
        @Schema(description = "当前系统中的规范化显示值；新建字段时为空。")
        String before,
        @Schema(description = "Excel 导入后预计保存的规范化显示值。")
        String after
) {}
