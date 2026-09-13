package cn.superhuang.data.scalpel.business.metric.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "指标工作簿某一原始行的校验错误或告警。")

public record MetricImportIssueResponse(
        @Schema(description = "问题所在的 Excel 原始行号。")
        int rowNumber,
        @Schema(description = "问题对应的模板列名或业务字段路径。")
        String column,
        @Schema(description = "说明该工作簿行或单元格为何不能导入或需要修正的可读信息。")
        String message,
        @Schema(description = "该问题是否会阻止导入或发布。")
        boolean blocking
) {}
