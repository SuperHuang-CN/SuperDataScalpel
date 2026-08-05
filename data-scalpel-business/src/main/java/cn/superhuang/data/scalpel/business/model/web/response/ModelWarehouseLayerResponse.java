package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.ModelWarehouseLayer;
import cn.superhuang.data.scalpel.business.model.domain.ModelWarehouseLayerInputPolicy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ModelWarehouseLayerResponse(
        UUID id,
        String code,
        String name,
        String description,
        String color,
        int sortOrder,
        boolean enabled,
        String modelCodePrefix,
        ModelWarehouseLayerInputPolicy inputLayerPolicy,
        List<ModelWarehouseLayerSummaryResponse> allowedInputLayers,
        long referencedModelCount,
        long referencedAsInputByLayerCount,
        boolean deletable,
        Instant createdAt,
        Instant updatedAt
) {
    public static ModelWarehouseLayerResponse from(
            ModelWarehouseLayer layer,
            List<ModelWarehouseLayerSummaryResponse> allowedInputLayers,
            long referencedModelCount,
            long referencedAsInputByLayerCount
    ) {
        return new ModelWarehouseLayerResponse(
                layer.getId(),
                layer.getCode(),
                layer.getName(),
                layer.getDescription(),
                layer.getColor(),
                layer.getSortOrder(),
                layer.isEnabled(),
                layer.getModelCodePrefix(),
                layer.getInputLayerPolicy(),
                List.copyOf(allowedInputLayers),
                referencedModelCount,
                referencedAsInputByLayerCount,
                referencedModelCount == 0 && referencedAsInputByLayerCount == 0,
                layer.getCreatedAt(),
                layer.getUpdatedAt()
        );
    }
}
