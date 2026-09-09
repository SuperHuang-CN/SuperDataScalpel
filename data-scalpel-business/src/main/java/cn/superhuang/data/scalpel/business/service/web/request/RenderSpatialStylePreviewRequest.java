package cn.superhuang.data.scalpel.business.service.web.request;

import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RenderSpatialStylePreviewRequest(
        @NotNull @Valid SpatialStyleDocument styleDocument,
        @NotNull @Size(min = 4, max = 4) List<Double> bbox,
        @Min(256) @Max(1600) int width,
        @Min(256) @Max(1200) int height
) {
}
