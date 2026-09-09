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

    /**
     * Reads at most {@code maximumBytes} starting at the requested object offset.
     * Implementations backed by remote object stores should override this with a range request.
     */
    default Optional<byte[]> readRangeIfPresent(String objectKey, long startInclusive, int maximumBytes) {
        if (startInclusive < 0) throw new IllegalArgumentException("任务运行制品读取位置无效");
        if (maximumBytes < 1) throw new IllegalArgumentException("任务运行制品读取大小无效");
        try (ArtifactContent content = openIfPresent(objectKey).orElse(null)) {
            if (content == null) return Optional.empty();
            InputStream input = content.inputStream();
            long remaining = startInclusive;
            while (remaining > 0) {
                long skipped = input.skip(remaining);
                if (skipped > 0) {
                    remaining -= skipped;
                    continue;
                }
                if (input.read() < 0) return Optional.of(new byte[0]);
                remaining--;
            }
            return Optional.of(input.readNBytes(maximumBytes));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("无法读取任务运行制品范围", exception);
        }
    }

    /** Returns object metadata without loading its body when the backing store supports it. */
    default Optional<ArtifactMetadata> metadataIfPresent(String objectKey) {
        try (ArtifactContent content = openIfPresent(objectKey).orElse(null)) {
            return content == null
                    ? Optional.empty()
                    : Optional.of(new ArtifactMetadata(content.contentLength(), content.contentType()));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("无法关闭任务运行制品流", exception);
        }
    }

    default Optional<ArtifactContent> openIfPresent(String objectKey) {
        return readIfPresent(objectKey, 512 * 1024 * 1024).map(bytes ->
                new ArtifactContent(new java.io.ByteArrayInputStream(bytes), bytes.length, "application/octet-stream"));
    }

    record ArtifactContent(InputStream inputStream, long contentLength, String contentType) implements AutoCloseable {
        @Override public void close() throws java.io.IOException { inputStream.close(); }
    }

    record ArtifactMetadata(long contentLength, String contentType) {
        public ArtifactMetadata {
            if (contentLength < 0) throw new IllegalArgumentException("任务运行制品大小无效");
            contentType = contentType == null || contentType.isBlank()
                    ? "application/octet-stream" : contentType;
        }
    }

    /** Signals that a caller-requested safety limit was exceeded before the object was buffered. */
    final class ArtifactSizeLimitExceededException extends RuntimeException {
        public ArtifactSizeLimitExceededException(long maximumBytes) {
            super("任务运行制品超过允许大小：%s 字节".formatted(maximumBytes));
        }
    }
}
