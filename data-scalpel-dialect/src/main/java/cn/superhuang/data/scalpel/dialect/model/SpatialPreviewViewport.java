package cn.superhuang.data.scalpel.dialect.model;

/** EPSG:3857 viewport in metres. */
public record SpatialPreviewViewport(
        double minX,
        double minY,
        double maxX,
        double maxY,
        int width,
        int height
) {
    public SpatialPreviewViewport {
        if (!Double.isFinite(minX) || !Double.isFinite(minY)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY)
                || minX >= maxX || minY >= maxY) {
            throw new IllegalArgumentException("空间预览范围无效");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("空间预览图片尺寸无效");
        }
    }

    public double simplificationTolerance() {
        return ((maxX - minX) / width) * 0.75d;
    }
}
