package cn.superhuang.data.scalpel.dialect.model;

/** Hard limits applied while reading a single spatial preview image. */
public record SpatialPreviewLimits(int maximumFeatures, long maximumWkbBytes, long unindexedMaximumRows) {
    public SpatialPreviewLimits {
        if (maximumFeatures <= 0 || maximumWkbBytes <= 0 || unindexedMaximumRows < 0) {
            throw new IllegalArgumentException("Spatial preview limits must be positive");
        }
    }
}
