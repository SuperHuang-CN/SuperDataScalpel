package cn.superhuang.data.scalpel.contract.execution;

public record ExecutionUserJarArtifact(String objectKey, String sha256, long sizeBytes) {
    public static final long MAX_BYTES = 100L * 1024 * 1024;

    public ExecutionUserJarArtifact {
        objectKey = ExecutionContractValidation.objectKey(objectKey);
        sha256 = ExecutionContractValidation.sha256(sha256);
        if (sizeBytes < 1 || sizeBytes > MAX_BYTES) {
            throw new IllegalArgumentException("用户 JAR 大小无效");
        }
    }
}
