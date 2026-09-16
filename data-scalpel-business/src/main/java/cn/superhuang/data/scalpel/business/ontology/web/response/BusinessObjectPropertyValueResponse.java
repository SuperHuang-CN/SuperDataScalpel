package cn.superhuang.data.scalpel.business.ontology.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "一个业务属性的当前读取值及来源状态。")
public record BusinessObjectPropertyValueResponse(
        @Schema(description = "属性稳定 UUID。") UUID propertyId,
        @Schema(description = "业务编码。") String code,
        @Schema(description = "业务名称。") String name,
        @Schema(description = "属性组 UUID；为空表示未分组。") UUID groupId,
        @Schema(description = "展示单位；未配置时为空，不进行单位换算。") String unit,
        @Schema(description = "来源字段的平台类型；字段不可用时为空。") String fieldType,
        @Schema(description = "来源原始标量值；为空时须结合来源状态区分空值、未匹配或错误。") Object value,
        @Schema(description = "MATCHED 已匹配非空值、NULL 已匹配但字段为空、NO_MATCH 补充来源无记录、ERROR 来源读取失败。") String sourceStatus,
        @Schema(description = "来源模型 UUID。") UUID sourceModelId,
        @Schema(description = "来源模型名称；不可用时为空。") String sourceModelName,
        @Schema(description = "来源字段 UUID。") UUID sourceFieldId,
        @Schema(description = "来源字段编码；不可用时为空。") String sourceFieldCode,
        @Schema(description = "来源字段显示名称；不可用时为空。") String sourceFieldName,
        @Schema(description = "配置的数据时间字段值；未配置或不可读取时为空，不能用读取时间替代。") Object sourceDataTime,
        @Schema(description = "该属性的诊断信息；正常时为空。") String diagnostic
) {
}
