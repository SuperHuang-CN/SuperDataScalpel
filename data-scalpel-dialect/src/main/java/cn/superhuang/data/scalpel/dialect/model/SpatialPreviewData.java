package cn.superhuang.data.scalpel.dialect.model;

import java.util.List;

/** Clipped and simplified EPSG:3857 WKB rows returned by a controlled PostGIS query. */
public record SpatialPreviewData(List<byte[]> geometries, int skippedCount, boolean truncated) {
    public SpatialPreviewData {
        geometries = geometries == null ? List.of() : List.copyOf(geometries);
        if (skippedCount < 0) {
            throw new IllegalArgumentException("Skipped Geometry count cannot be negative");
        }
    }
}
