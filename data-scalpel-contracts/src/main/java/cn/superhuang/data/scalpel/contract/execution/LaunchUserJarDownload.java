package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.net.URI;

public record LaunchUserJarDownload(
        @JsonPropertyDescription("短期有效且仅允许 GET 的预签名对象下载地址。")
        URI getUrl,
        @JsonPropertyDescription("内容的 SHA-256 十六进制摘要。")
        String sha256,
        @JsonPropertyDescription("大小，单位字节。")
        long sizeBytes
) {
    public LaunchUserJarDownload {
        getUrl = ExecutionContractValidation.httpUri(getUrl, "用户 JAR 下载地址");
        sha256 = ExecutionContractValidation.sha256(sha256);
        if (sizeBytes < 1 || sizeBytes > ExecutionUserJarArtifact.MAX_BYTES) {
            throw new IllegalArgumentException("用户 JAR 下载大小无效");
        }
    }
}
