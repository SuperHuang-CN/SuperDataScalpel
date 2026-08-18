package cn.superhuang.data.scalpel.contract.execution;

import java.net.URI;

public record LaunchUserJarDownload(URI getUrl, String sha256, long sizeBytes) {
    public LaunchUserJarDownload {
        getUrl = ExecutionContractValidation.httpUri(getUrl, "用户 JAR 下载地址");
        sha256 = ExecutionContractValidation.sha256(sha256);
        if (sizeBytes < 1 || sizeBytes > ExecutionUserJarArtifact.MAX_BYTES) {
            throw new IllegalArgumentException("用户 JAR 下载大小无效");
        }
    }
}
