package cn.superhuang.data.scalpel.shapefile.s3;

import cn.superhuang.data.scalpel.shapefile.ShapefileComponent;

interface S3ObjectAccess {
    S3ObjectSnapshot head(ShapefileComponent component, String objectKey, boolean allowMissing);

    S3RangeResponse readRange(
            ShapefileComponent component,
            S3ObjectSnapshot snapshot,
            long startInclusive,
            long endInclusive);
}

record S3ObjectSnapshot(String objectKey, long size, String eTag, String versionId) {
    String versionToken() {
        return versionId != null ? "version:" + versionId : "etag:" + eTag;
    }
}

record S3RangeResponse(
        byte[] bytes,
        Long contentLength,
        String contentRange,
        String eTag,
        String versionId) {
}
