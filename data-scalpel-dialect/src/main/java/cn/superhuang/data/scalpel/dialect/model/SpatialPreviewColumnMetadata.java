package cn.superhuang.data.scalpel.dialect.model;

/** Runtime PostGIS capabilities for one model Geometry field. */
public record SpatialPreviewColumnMetadata(
        String name,
        boolean spatialIndexAvailable,
        boolean previewSupported,
        String message
) {
}
