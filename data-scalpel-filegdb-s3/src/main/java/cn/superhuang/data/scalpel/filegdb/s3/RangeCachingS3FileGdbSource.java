package cn.superhuang.data.scalpel.filegdb.s3;

import cn.superhuang.data.scalpel.filegdb.FileGdbErrorCode;
import cn.superhuang.data.scalpel.filegdb.FileGdbException;
import cn.superhuang.data.scalpel.filegdb.FileGdbRandomAccessObject;
import cn.superhuang.data.scalpel.filegdb.FileGdbSource;
import cn.superhuang.data.scalpel.filegdb.FileGdbSourceInfo;
import cn.superhuang.data.scalpel.filegdb.FileGdbSourceType;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class RangeCachingS3FileGdbSource implements FileGdbSource {
    private final S3ObjectAccess objectAccess;
    private final S3FileGdbLocation location;
    private final int blockSizeBytes;
    private final long maxCacheBytes;
    private final FileGdbSourceInfo sourceInfo;
    private final Map<String, S3ObjectMetadata> metadata = new HashMap<>();
    private final Set<String> missingObjects = new HashSet<>();
    private final LinkedHashMap<BlockKey, byte[]> blocks = new LinkedHashMap<>(16, 0.75f, true);
    private long cachedBytes;
    private boolean closed;

    RangeCachingS3FileGdbSource(
            S3ObjectAccess objectAccess,
            S3FileGdbLocation location,
            S3FileGdbOptions options) {
        this.objectAccess = objectAccess;
        this.location = location;
        this.blockSizeBytes = options.blockSizeBytes();
        this.maxCacheBytes = options.maxCacheBytes();
        this.sourceInfo = new FileGdbSourceInfo(FileGdbSourceType.S3, location.safeLocation());
    }

    @Override
    public FileGdbSourceInfo info() {
        ensureOpen();
        return sourceInfo;
    }

    @Override
    public boolean exists(String fileName) {
        ensureOpen();
        validateFileName(fileName);
        if (metadata.containsKey(fileName)) {
            return true;
        }
        if (missingObjects.contains(fileName)) {
            return false;
        }
        S3ObjectMetadata objectMetadata = objectAccess.head(
                location.objectKey(fileName),
                fileName,
                true);
        if (objectMetadata == null) {
            missingObjects.add(fileName);
            return false;
        }
        metadata.put(fileName, objectMetadata);
        return true;
    }

    @Override
    public FileGdbRandomAccessObject open(String fileName) {
        ensureOpen();
        validateFileName(fileName);
        S3ObjectMetadata objectMetadata = metadata.get(fileName);
        if (objectMetadata == null) {
            objectMetadata = objectAccess.head(location.objectKey(fileName), fileName, false);
            metadata.put(fileName, objectMetadata);
            missingObjects.remove(fileName);
        }
        return new S3RandomAccessObject(fileName, objectMetadata);
    }

    long cachedBytes() {
        return cachedBytes;
    }

    int cachedBlockCount() {
        return blocks.size();
    }

    private byte[] block(String fileName, S3ObjectMetadata objectMetadata, long blockIndex) {
        BlockKey blockKey = new BlockKey(objectMetadata.objectKey(), objectMetadata.versionToken(), blockIndex);
        byte[] cached = blocks.get(blockKey);
        if (cached != null) {
            return cached;
        }
        final long start;
        final long end;
        try {
            start = Math.multiplyExact(blockIndex, (long) blockSizeBytes);
            end = Math.min(
                    Math.subtractExact(objectMetadata.size(), 1),
                    Math.addExact(start, blockSizeBytes - 1L));
        } catch (ArithmeticException exception) {
            throw new FileGdbException(
                    FileGdbErrorCode.INVALID_OFFSET,
                    "S3 FileGDB block range overflow for file " + fileName,
                    exception);
        }
        if (start < 0 || end < start || end >= objectMetadata.size()) {
            throw new FileGdbException(
                    FileGdbErrorCode.TRUNCATED_INPUT,
                    "Invalid S3 FileGDB block range for file " + fileName);
        }
        S3RangeResponse response = objectAccess.readRange(objectMetadata, fileName, start, end);
        int expectedLength = Math.toIntExact(end - start + 1);
        validateResponse(fileName, objectMetadata, response, start, end, expectedLength);
        byte[] loaded = response.bytes();
        evictFor(loaded.length);
        blocks.put(blockKey, loaded);
        cachedBytes += loaded.length;
        return loaded;
    }

    private void validateResponse(
            String fileName,
            S3ObjectMetadata objectMetadata,
            S3RangeResponse response,
            long start,
            long end,
            int expectedLength) {
        byte[] bytes = response.bytes();
        String expectedRange = "bytes " + start + "-" + end + "/" + objectMetadata.size();
        if (bytes == null
                || bytes.length != expectedLength
                || response.contentLength() == null
                || response.contentLength() != expectedLength
                || !expectedRange.equals(response.contentRange())) {
            throw new FileGdbException(
                    FileGdbErrorCode.TRUNCATED_INPUT,
                    "S3 returned an incomplete range for FileGDB file " + fileName);
        }
        if (objectMetadata.versionId() != null) {
            if (!objectMetadata.versionId().equals(response.versionId())) {
                throw sourceChanged(fileName);
            }
        }
        if (objectMetadata.eTag() != null
                && !objectMetadata.eTag().equals(response.eTag())) {
            throw sourceChanged(fileName);
        }
    }

    private FileGdbException sourceChanged(String fileName) {
        return new FileGdbException(
                FileGdbErrorCode.SOURCE_CHANGED,
                location.safeLocation() + " changed while reading FileGDB file " + fileName);
    }

    private void evictFor(int byteCount) {
        var iterator = blocks.entrySet().iterator();
        while (cachedBytes > maxCacheBytes - byteCount && iterator.hasNext()) {
            Map.Entry<BlockKey, byte[]> eldest = iterator.next();
            cachedBytes -= eldest.getValue().length;
            iterator.remove();
        }
    }

    private static void validateFileName(String fileName) {
        if (fileName == null
                || fileName.isBlank()
                || fileName.equals(".")
                || fileName.equals("..")
                || fileName.contains("/")
                || fileName.contains("\\")
                || fileName.chars().anyMatch(Character::isISOControl)) {
            throw new FileGdbException(
                    FileGdbErrorCode.INVALID_DIRECTORY,
                    "Invalid FileGDB physical file name");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new FileGdbException(FileGdbErrorCode.CLOSED, "S3 FileGDB source is closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        blocks.clear();
        metadata.clear();
        missingObjects.clear();
        cachedBytes = 0;
    }

    private final class S3RandomAccessObject implements FileGdbRandomAccessObject {
        private final String fileName;
        private final S3ObjectMetadata objectMetadata;
        private boolean objectClosed;

        private S3RandomAccessObject(String fileName, S3ObjectMetadata objectMetadata) {
            this.fileName = fileName;
            this.objectMetadata = objectMetadata;
        }

        @Override
        public long size() {
            ensureObjectOpen();
            return objectMetadata.size();
        }

        @Override
        public int read(long position, ByteBuffer target) {
            ensureObjectOpen();
            if (position < 0) {
                throw new FileGdbException(
                        FileGdbErrorCode.INVALID_OFFSET,
                        fileName + " has a negative read position");
            }
            if (!target.hasRemaining()) {
                return 0;
            }
            if (position >= objectMetadata.size()) {
                return -1;
            }
            int requested = (int) Math.min(target.remaining(), objectMetadata.size() - position);
            int copied = 0;
            while (copied < requested) {
                long currentPosition = position + copied;
                long blockIndex = currentPosition / blockSizeBytes;
                byte[] bytes = block(fileName, objectMetadata, blockIndex);
                int offsetInBlock = (int) (currentPosition % blockSizeBytes);
                int count = Math.min(requested - copied, bytes.length - offsetInBlock);
                if (count <= 0) {
                    throw new FileGdbException(
                            FileGdbErrorCode.TRUNCATED_INPUT,
                            "S3 block is shorter than expected for FileGDB file " + fileName);
                }
                target.put(bytes, offsetInBlock, count);
                copied += count;
            }
            return copied;
        }

        private void ensureObjectOpen() {
            ensureOpen();
            if (objectClosed) {
                throw new FileGdbException(FileGdbErrorCode.CLOSED, fileName + " is closed");
            }
        }

        @Override
        public void close() {
            objectClosed = true;
        }
    }

    private record BlockKey(String objectKey, String versionToken, long blockIndex) {
    }
}
