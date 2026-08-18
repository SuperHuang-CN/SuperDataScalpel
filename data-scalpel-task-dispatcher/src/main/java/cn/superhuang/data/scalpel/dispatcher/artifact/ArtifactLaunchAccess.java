package cn.superhuang.data.scalpel.dispatcher.artifact;

import java.net.URI;
import java.util.List;
import cn.superhuang.data.scalpel.contract.execution.QualitySampleArtifactUpload;
import cn.superhuang.data.scalpel.contract.execution.LaunchUserJarDownload;

public record ArtifactLaunchAccess(
        URI manifestGetUrl,
        URI resultPutUrl,
        URI logPutUrl,
        int maximumManifestBytes,
        List<QualitySampleArtifactUpload> qualitySamples,
        LaunchUserJarDownload userJar
) {
    public ArtifactLaunchAccess {
        qualitySamples = qualitySamples == null ? List.of() : List.copyOf(qualitySamples);
        if (manifestGetUrl == null || resultPutUrl == null || logPutUrl == null
                || maximumManifestBytes < 1 || maximumManifestBytes > 20 * 1024 * 1024) {
            throw new IllegalArgumentException("执行制品访问地址不能为空");
        }
    }

    public ArtifactLaunchAccess(URI manifestGetUrl, URI resultPutUrl, URI logPutUrl, int maximumManifestBytes) {
        this(manifestGetUrl, resultPutUrl, logPutUrl, maximumManifestBytes, List.of(), null);
    }

    public ArtifactLaunchAccess(URI manifestGetUrl, URI resultPutUrl, URI logPutUrl,
                                int maximumManifestBytes, List<QualitySampleArtifactUpload> qualitySamples) {
        this(manifestGetUrl, resultPutUrl, logPutUrl, maximumManifestBytes, qualitySamples, null);
    }
}
