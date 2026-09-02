package cn.superhuang.data.scalpel.dialect.model;

/** Hard limits and the model-statistics safety gate applied while reading one spatial preview image. */
public record SpatialPreviewLimits(
        int maximumFeatures,
        long maximumWkbBytes,
        Long unindexedTableRowCount,
        long unindexedMaximumRows
) {
    public SpatialPreviewLimits {
        if (maximumFeatures <= 0 || maximumWkbBytes <= 0 || unindexedMaximumRows < 0
                || (unindexedTableRowCount != null && unindexedTableRowCount < 0)) {
            throw new IllegalArgumentException("Spatial preview limits must be positive");
        }
    }
}
