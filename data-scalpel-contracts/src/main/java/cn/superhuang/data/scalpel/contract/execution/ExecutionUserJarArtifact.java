package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
public record ExecutionUserJarArtifact(
        @JsonPropertyDescription("平台指定的对象存储 Key，Runner 不得改写。")
        String objectKey,
        @JsonPropertyDescription("内容的 SHA-256 十六进制摘要。")
        String sha256,
        @JsonPropertyDescription("大小，单位字节。")
        long sizeBytes
) {
    public static final long MAX_BYTES = 100L * 1024 * 1024;

    public ExecutionUserJarArtifact {
        objectKey = ExecutionContractValidation.objectKey(objectKey);
        sha256 = ExecutionContractValidation.sha256(sha256);
        if (sizeBytes < 1 || sizeBytes > MAX_BYTES) {
            throw new IllegalArgumentException("用户 JAR 大小无效");
        }
    }
}
