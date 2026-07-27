package cn.superhuang.data.scalpel.dispatcher.artifact;

import java.net.URI;

public record ArtifactLaunchAccess(
        URI manifestGetUrl,
        URI resultPutUrl,
        URI logPutUrl,
        int maximumManifestBytes
) {
    public ArtifactLaunchAccess {
        if (manifestGetUrl == null || resultPutUrl == null || logPutUrl == null
                || maximumManifestBytes < 1 || maximumManifestBytes > 20 * 1024 * 1024) {
            throw new IllegalArgumentException("执行制品访问地址不能为空");
        }
    }
}
