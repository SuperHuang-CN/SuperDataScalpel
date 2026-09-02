package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;

import java.util.List;

public record DataModelSpatialPreviewResponse(
        boolean supported,
        String message,
        List<GeometryField> geometryFields,
        String displayCrs,
        List<Double> initialBounds,
        Limits limits
) {
    public DataModelSpatialPreviewResponse {
        geometryFields = geometryFields == null ? List.of() : List.copyOf(geometryFields);
        initialBounds = List.copyOf(initialBounds);
    }

    public record GeometryField(
            String code,
            String name,
            GeometryKind kind,
            CrsReference sourceCrs,
            boolean spatialIndexAvailable,
            Long estimatedRowCount,
            boolean physicalStatisticsRefreshRequired,
            boolean previewAllowed,
            String message
    ) {
    }

    public record Limits(
            int minimumWidth,
            int maximumWidth,
            int minimumHeight,
            int maximumHeight,
            int maximumFeatures,
            int maximumCoordinates,
            long maximumWkbBytes
    ) {
    }
}
