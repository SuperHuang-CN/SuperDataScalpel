package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument;

@Schema(description = "模型字段在空间制图中的值类型及可用渲染能力。")

public record SpatialStyleFieldResponse(
        @Schema(description = "模型字段稳定编码，样式规则通过该值引用字段。")
        String code,
        @Schema(description = "模型字段显示名称。")
        String name,
        @Schema(description = "模型字段的平台数据类型名称。")
        String dataType,
        @Schema(description = "样式表达使用的值类型：STRING、NUMBER 或 BOOLEAN。")
        SpatialStyleDocument.ValueType valueType,
        @Schema(description = "该字段是否可用于唯一值分类渲染。")
        boolean uniqueValueSupported,
        @Schema(description = "该字段是否为可计算数值分级断点的数值类型。")
        boolean classBreaksSupported,
        @Schema(description = "该字段是否可作为地图文字标注内容。")
        boolean labelSupported
) {
}
