package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.ModelWarehouseLayer;

import java.util.UUID;

public record ModelWarehouseLayerSummaryResponse(
        UUID id,
        String code,
        String name,
        String color,
        boolean enabled,
        String modelCodePrefix
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
