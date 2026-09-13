package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.ModelWarehouseLayer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "模型表单和关联展示使用的数仓分层摘要")
public record ModelWarehouseLayerSummaryResponse(
        @Schema(description = "分层 UUID") UUID id,
        @Schema(description = "全局唯一的大写分层编码") String code,
        @Schema(description = "分层显示名称") String name,
        @Schema(description = "界面展示颜色，格式为 #RRGGBB") String color,
        @Schema(description = "是否允许新模型分配到该分层") boolean enabled,
        @Schema(description = "新建模型编码的建议前缀；为空表示不建议前缀") String modelCodePrefix
) {
    public static ModelWarehouseLayerSummaryResponse from(ModelWarehouseLayer layer) {
        return layer == null ? null : new ModelWarehouseLayerSummaryResponse(
                layer.getId(),
                layer.getCode(),
                layer.getName(),
                layer.getColor(),
                layer.isEnabled(),
                layer.getModelCodePrefix()
        );
    }
}
