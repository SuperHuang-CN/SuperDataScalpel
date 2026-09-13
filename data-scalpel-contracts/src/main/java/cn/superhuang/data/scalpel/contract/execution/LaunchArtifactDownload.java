package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.net.URI;

public record LaunchArtifactDownload(
        @JsonPropertyDescription("短期有效且仅允许 GET 的预签名对象下载地址。")
        URI getUrl,
        @JsonPropertyDescription("内容的 SHA-256 十六进制摘要。")
        String sha256,
        @JsonPropertyDescription("允许下载或上传的最大字节数，超过时必须拒绝。")
        int maxBytes
) {
    public LaunchArtifactDownload {
        getUrl = ExecutionContractValidation.httpUri(getUrl, "制品下载地址");
        sha256 = ExecutionContractValidation.sha256(sha256);
        if (maxBytes < 1 || maxBytes > 20 * 1024 * 1024) {
            throw new IllegalArgumentException("制品下载大小限制无效");
        }
    }
}
