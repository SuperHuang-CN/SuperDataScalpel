package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;

@Schema(description = "Local SQL 预检得到的结果列及其与输出模型字段的匹配情况。")

public record LocalSqlDefinitionValidationColumnResponse(
        @Schema(description = "查询结果列顺序，从 1 开始。")
        int ordinal,
        @Schema(description = "数据库返回的结果列标签。")
        String label,
        @Schema(description = "方言归一化后的逻辑数据类型。")
        LogicalType logicalType,
        @Schema(description = "数据库返回的原生类型名称。")
        String nativeType,
        @Schema(description = "结果列是否允许为空。")
        boolean nullable,
        @Schema(description = "按不区分大小写的结果列标签匹配到的输出模型字段编码；未匹配时为空。类型不兼容时仍返回字段编码，并在 problems 中报告。")
        String matchedOutputFieldCode
) {
}
