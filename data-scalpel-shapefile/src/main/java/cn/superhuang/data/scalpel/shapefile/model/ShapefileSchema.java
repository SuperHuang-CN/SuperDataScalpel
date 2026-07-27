package cn.superhuang.data.scalpel.shapefile.model;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;

/** Immutable schema and source metadata for one Shapefile component set. */
public record ShapefileSchema(
        ShapefileShapeType shapeType,
        long recordCount,
        List<ShapefileField> fields,
        ShapefileEnvelope envelope,
        Charset dbfCharset,
        ShapefileSpatialReference spatialReference) {
    public ShapefileSchema {
        Objects.requireNonNull(shapeType, "shapeType");
        if (recordCount < 0) {
            throw new IllegalArgumentException("recordCount must not be negative");
        }
        fields = List.copyOf(fields);
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(dbfCharset, "dbfCharset");
    }
}
