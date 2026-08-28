package cn.superhuang.data.scalpel.business.task.service;

import java.net.URI;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/** Private object storage used for immutable task manifests and execution artifacts. */
public interface TaskRunArtifactStorage {

    void store(String objectKey, byte[] content, String contentType);

    default void store(String objectKey, Path file, String contentType) {
        try { store(objectKey, java.nio.file.Files.readAllBytes(file), contentType); }
        catch (java.io.IOException exception) { throw new IllegalStateException("无法读取待上传制品", exception); }
    }

    void delete(String objectKey);

    URI presignGet(String objectKey, Duration lifetime);

    URI presignPut(String objectKey, String contentType, Duration lifetime);

    Optional<byte[]> readIfPresent(String objectKey, int maximumBytes);

    default Optional<ArtifactContent> openIfPresent(String objectKey) {
        return readIfPresent(objectKey, 512 * 1024 * 1024).map(bytes ->
                new ArtifactContent(new java.io.ByteArrayInputStream(bytes), bytes.length, "application/octet-stream"));
    }

    record ArtifactContent(InputStream inputStream, long contentLength, String contentType) implements AutoCloseable {
        @Override public void close() throws java.io.IOException { inputStream.close(); }
    }
}
