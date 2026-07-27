package cn.superhuang.data.scalpel.admin.filedataset;

import cn.superhuang.data.scalpel.business.task.service.CanvasFileStorageRuntimeProvider;
import cn.superhuang.data.scalpel.business.task.service.CanvasTaskRunManifest;

final class S3CanvasFileStorageRuntimeProvider implements CanvasFileStorageRuntimeProvider {

    private final CanvasTaskRunManifest.RuntimeFileStorage runtimeStorage;
    private final String rootPrefix;

    S3CanvasFileStorageRuntimeProvider(S3FileStorageProperties properties) {
        this.runtimeStorage = new CanvasTaskRunManifest.RuntimeFileStorage(
                requireText(defaultIfBlank(properties.runnerEndpoint(), properties.endpoint()), "S3 Runner endpoint"),
                defaultIfBlank(properties.region(), "us-east-1"),
                requireText(properties.bucket(), "S3 Bucket"),
                properties.pathStyleAccess(),
                requireText(properties.accessKey(), "S3 AccessKey"),
                requireText(properties.secretKey(), "S3 SecretKey")
        );
        this.rootPrefix = normalizePrefix(properties.rootPrefix());
    }

    @Override
    public CanvasTaskRunManifest.RuntimeFileStorage runtimeStorage() {
        return runtimeStorage;
    }

    @Override
    public String resolveObjectKey(String objectKey) {
        String normalized = requireText(objectKey, "S3 object key").replaceFirst("^/+", "");
        if (normalized.contains("..")) {
            throw new IllegalArgumentException("S3 object key cannot contain parent path segments");
        }
        return rootPrefix.isEmpty() ? normalized : rootPrefix + "/" + normalized;
    }

    private static String normalizePrefix(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().replaceFirst("^/+", "").replaceFirst("/+$", "");
        if (normalized.contains("..")) {
            throw new IllegalArgumentException("S3 root prefix cannot contain parent path segments");
        }
        return normalized;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + " 尚未配置");
        }
        return value.trim();
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
