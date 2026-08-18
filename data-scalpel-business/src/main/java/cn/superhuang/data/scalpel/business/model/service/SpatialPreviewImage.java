package cn.superhuang.data.scalpel.business.model.service;

public record SpatialPreviewImage(byte[] png, int featureCount, int skippedCount, boolean truncated) {
    public SpatialPreviewImage {
        png = png.clone();
    }
}
