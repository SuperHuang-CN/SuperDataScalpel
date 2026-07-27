package cn.superhuang.data.scalpel.filegdb.s3;

interface S3ObjectAccess {
    S3ObjectMetadata head(String objectKey, String fileName, boolean allowMissing);

    S3RangeResponse readRange(
            S3ObjectMetadata metadata,
            String fileName,
            long startInclusive,
            long endInclusive);
}

record S3ObjectMetadata(String objectKey, long size, String eTag, String versionId) {
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
