package cn.superhuang.data.scalpel.dispatcher.artifact;

import cn.superhuang.data.scalpel.dispatcher.backend.BackendException;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendReadiness;
import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionLaunch;

import java.util.Optional;

public interface DispatcherArtifactService {
    BackendReadiness readiness();
    ArtifactLaunchAccess prepareLaunch(ExecutionLaunch launch) throws BackendException;
    Optional<byte[]> readIfPresent(String objectKey, int maximumBytes) throws BackendException;
    void store(String objectKey, byte[] content, String contentType) throws BackendException;
}
