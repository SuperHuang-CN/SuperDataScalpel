package cn.superhuang.data.scalpel.business.dataentry.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "表单目标字段的关联模型下拉配置")

public record DataEntryLookupResponse(
        @Schema(description = "关联配置 UUID。")
        UUID id,
        @Schema(description = "目标模型中使用该下拉配置的字段 UUID。")
        UUID targetFieldId,
        @Schema(description = "提供选项的来源模型 UUID。")
        UUID sourceModelId,
        @Schema(description = "查找关系引用的来源模型编码。")
        String sourceModelCode,
        @Schema(description = "查找关系引用的来源模型名称。")
        String sourceModelName,
        @Schema(description = "来源模型唯一单字段业务主键 UUID；选中后保存该字段值。")
        UUID sourceValueFieldId,
        @Schema(description = "查找关系中作为实际保存值的来源字段编码。")
        String sourceValueFieldCode,
        @Schema(description = "来源模型中用于展示选项标签的 STRING 字段 UUID。")
        UUID sourceLabelFieldId,
        @Schema(description = "查找关系中作为显示标签的来源字段编码。")
        String sourceLabelFieldCode,
        @Schema(description = "查找关系中作为显示标签的来源字段名称。")
        String sourceLabelFieldName
) {
}
