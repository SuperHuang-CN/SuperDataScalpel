package cn.superhuang.data.scalpel.business.task.service;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/** Private object storage used for immutable task manifests and execution artifacts. */
public interface TaskRunArtifactStorage {

    void store(String objectKey, byte[] content, String contentType);

    void delete(String objectKey);

    URI presignGet(String objectKey, Duration lifetime);

    URI presignPut(String objectKey, String contentType, Duration lifetime);

    Optional<byte[]> readIfPresent(String objectKey, int maximumBytes);
}
