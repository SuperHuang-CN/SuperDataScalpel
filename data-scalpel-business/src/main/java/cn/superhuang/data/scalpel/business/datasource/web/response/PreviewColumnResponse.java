package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.PreviewColumn;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "表数据预览中的列定义")
public record PreviewColumnResponse(
        @Schema(description = "数据库结果列名；对应预览行中同一位置的值。") String name,
        @Schema(description = "数据库驱动报告的原生类型名称。") String nativeType,
        @Schema(description = "方言归一化后的逻辑类型编码。") String logicalType
) {
    static PreviewColumnResponse from(PreviewColumn column) {
        return new PreviewColumnResponse(column.name(), column.nativeType(), column.logicalType().name());
    }
}
