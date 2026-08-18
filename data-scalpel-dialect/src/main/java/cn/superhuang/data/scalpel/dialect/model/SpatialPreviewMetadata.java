package cn.superhuang.data.scalpel.dialect.model;

import java.util.List;

/** Table-scoped PostGIS preview inspection, produced without scanning model data. */
public record SpatialPreviewMetadata(
        boolean supported,
        String message,
        List<SpatialPreviewColumnMetadata> columns
) {
    public SpatialPreviewMetadata {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }

    public static SpatialPreviewMetadata unsupported(String message) {
        return new SpatialPreviewMetadata(false, message, List.of());
    }
}
