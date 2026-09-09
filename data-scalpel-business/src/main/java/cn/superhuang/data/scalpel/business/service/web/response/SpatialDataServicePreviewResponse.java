package cn.superhuang.data.scalpel.business.service.web.response;

import java.util.List;

public record SpatialDataServicePreviewResponse(
        boolean available,
        String message,
        String qualifiedLayerName,
        String displayCrs,
        List<Double> initialBounds,
        Limits limits
) {
    public SpatialDataServicePreviewResponse {
        initialBounds = initialBounds == null ? List.of() : List.copyOf(initialBounds);
    }

    public record Limits(
            int minimumWidth,
            int maximumWidth,
            int minimumHeight,
            int maximumHeight
    ) {
    }
}
