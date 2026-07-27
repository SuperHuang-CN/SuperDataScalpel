package cn.superhuang.data.scalpel.contract.execution;

import java.net.URI;

public record LaunchArtifactDownload(URI getUrl, String sha256, int maxBytes) {
    public LaunchArtifactDownload {
        getUrl = ExecutionContractValidation.httpUri(getUrl, "制品下载地址");
        sha256 = ExecutionContractValidation.sha256(sha256);
        if (maxBytes < 1 || maxBytes > 20 * 1024 * 1024) {
            throw new IllegalArgumentException("制品下载大小限制无效");
        }
    }
}
