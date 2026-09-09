package cn.superhuang.data.scalpel.dispatcher.artifact;

import cn.superhuang.data.scalpel.dispatcher.backend.BackendReadiness;
import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionLaunch;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("test")
public class InMemoryDispatcherArtifactService implements DispatcherArtifactService {
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override public BackendReadiness readiness() { return BackendReadiness.up(); }

    @Override
    public ArtifactLaunchAccess prepareLaunch(ExecutionLaunch launch) {
        var userJar = launch.userJar() == null ? null
                : new cn.superhuang.data.scalpel.contract.execution.LaunchUserJarDownload(
                URI.create("http://artifact.test/user-job.jar"), launch.userJar().sha256(), launch.userJar().sizeBytes());
        return new ArtifactLaunchAccess(
                URI.create("http://artifact.test/manifest"),
                URI.create("http://artifact.test/result"),
                URI.create("http://artifact.test/log"),
                URI.create("http://artifact.test/trial-preview"),
                10 * 1024 * 1024, java.util.List.of(), userJar);
    }

    @Override
    public Optional<byte[]> readIfPresent(String objectKey, int maximumBytes) {
        byte[] content = objects.get(objectKey);
        if (content == null) return Optional.empty();
        if (content.length > maximumBytes) throw new IllegalStateException("测试制品超过允许大小");
        return Optional.of(content.clone());
    }

    @Override
    public void store(String objectKey, byte[] content, String contentType) {
        objects.put(objectKey, content.clone());
    }
}
