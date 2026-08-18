package cn.superhuang.data.scalpel.dialect.model;

/** Runtime PostGIS capabilities for one model Geometry field. */
public record SpatialPreviewColumnMetadata(
        String name,
        boolean spatialIndexAvailable,
        Long estimatedRowCount,
        boolean previewAllowed,
        String message
) {
}
