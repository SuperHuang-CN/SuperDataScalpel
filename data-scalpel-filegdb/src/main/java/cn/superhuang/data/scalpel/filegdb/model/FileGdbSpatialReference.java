package cn.superhuang.data.scalpel.filegdb.model;

import java.util.Objects;

/** Raw FileGDB spatial-reference and coordinate quantization metadata. */
public record FileGdbSpatialReference(
        String wkt,
        boolean hasZ,
        boolean hasM,
        double xOrigin,
        double yOrigin,
        double xyScale,
        Double zOrigin,
        Double zScale,
        Double mOrigin,
        Double mScale,
        double xyTolerance,
        Double zTolerance,
        Double mTolerance,
        FileGdbEnvelope extent,
        Double zMin,
        Double zMax,
        Double mMin,
        Double mMax) {

    public FileGdbSpatialReference {
        Objects.requireNonNull(wkt, "wkt");
        Objects.requireNonNull(extent, "extent");
    }
}
