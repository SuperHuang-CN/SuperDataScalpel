package cn.superhuang.data.scalpel.shapefile.s3;

import cn.superhuang.data.scalpel.shapefile.ShapefileComponent;
import cn.superhuang.data.scalpel.shapefile.ShapefileErrorCode;
import cn.superhuang.data.scalpel.shapefile.ShapefileException;
import cn.superhuang.data.scalpel.shapefile.ShapefileRandomAccessObject;
import cn.superhuang.data.scalpel.shapefile.ShapefileSource;
import cn.superhuang.data.scalpel.shapefile.ShapefileSourceInfo;
import cn.superhuang.data.scalpel.shapefile.ShapefileSourceType;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

final class RangeCachingS3ShapefileSource implements ShapefileSource {
    private final S3ObjectAccess objectAccess;
    private final S3ShapefileLocation location;
    private final int blockSizeBytes;
    private final long maxCacheBytes;
    private final ShapefileSourceInfo sourceInfo;
    private final Map<ShapefileComponent, S3ObjectSnapshot> snapshots =
            new EnumMap<>(ShapefileComponent.class);
    private final LinkedHashMap<BlockKey, byte[]> blocks = new LinkedHashMap<>(16, 0.75f, true);
    private long cachedBytes;
    private ShapefileException terminalFailure;
    private boolean closed;

    RangeCachingS3ShapefileSource(
            S3ObjectAccess objectAccess,
            S3ShapefileLocation location,
            S3ShapefileOptions options) {
        this.objectAccess = objectAccess;
        this.location = location;
        this.blockSizeBytes = options.blockSizeBytes();
        this.maxCacheBytes = options.maxCacheBytes();
        this.sourceInfo = new ShapefileSourceInfo(ShapefileSourceType.S3, location.safeLocation());
        captureSnapshots();
    }

    private void captureSnapshots() {
        for (ShapefileComponent component : ShapefileComponent.values()) {
            String objectKey = location.componentKey(component).orElse(null);
            if (objectKey == null) {
                continue;
            }
            S3ObjectSnapshot snapshot = objectAccess.head(component, objectKey, !component.required());
            if (snapshot != null) {
                snapshots.put(component, snapshot);
            }
        }
    }

    @Override
    public ShapefileSourceInfo info() {
        ensureReadable();
        return sourceInfo;
    }

    @Override
    public boolean exists(ShapefileComponent component) {
        ensureReadable();
        if (component == null) {
            throw new NullPointerException("component");
        }
        return snapshots.containsKey(component);
    }

    @Override
    public ShapefileRandomAccessObject open(ShapefileComponent component) {
        ensureReadable();
        if (component == null) {
            throw new NullPointerException("component");
        }
        S3ObjectSnapshot snapshot = snapshots.get(component);
        if (snapshot == null) {
            throw new ShapefileException(
                    ShapefileErrorCode.MISSING_COMPONENT,
                    "Missing Shapefile ." + component.extension() + " component at " + location.safeLocation());
        }
        return new S3RandomAccessObject(component, snapshot);
    }

    long cachedBytes() {
        return cachedBytes;
    }

    int cachedBlockCount() {
        return blocks.size();
    }

    private byte[] block(
            ShapefileComponent component,
            S3ObjectSnapshot snapshot,
            long blockIndex) {
        ensureReadable();
        BlockKey blockKey = new BlockKey(
                location.bucket(), snapshot.objectKey(), snapshot.versionToken(), blockIndex);
        byte[] cached = blocks.get(blockKey);
        if (cached != null) {
            return cached;
        }
        final long start;
        final long end;
        try {
            start = Math.multiplyExact(blockIndex, (long) blockSizeBytes);
            end = Math.min(
                    Math.subtractExact(snapshot.size(), 1),
                    Math.addExact(start, blockSizeBytes - 1L));
        } catch (ArithmeticException exception) {
            throw new ShapefileException(
                    ShapefileErrorCode.INVALID_OFFSET,
                    "S3 Shapefile block range overflow for " + component + " component",
                    exception);
        }
        if (start < 0 || end < start || end >= snapshot.size()) {
            throw new ShapefileException(
                    ShapefileErrorCode.TRUNCATED_INPUT,
                    "Invalid S3 Shapefile block range for " + component + " component");
        }
        try {
            S3RangeResponse response = objectAccess.readRange(component, snapshot, start, end);
            int expectedLength = Math.toIntExact(end - start + 1);
            validateResponse(component, snapshot, response, start, end, expectedLength);
            byte[] loaded = response.bytes();
            evictFor(loaded.length);
            blocks.put(blockKey, loaded);
            cachedBytes += loaded.length;
            return loaded;
        } catch (ShapefileException exception) {
            if (exception.errorCode() == ShapefileErrorCode.SOURCE_CHANGED) {
                terminalFailure = exception;
            }
            throw exception;
        }
    }

    private void validateResponse(
            ShapefileComponent component,
            S3ObjectSnapshot snapshot,
            S3RangeResponse response,
            long start,
            long end,
            int expectedLength) {
        byte[] bytes = response.bytes();
        if (bytes == null
                || bytes.length != expectedLength
                || response.contentLength() == null
                || response.contentLength() != expectedLength) {
            throw new ShapefileException(
                    ShapefileErrorCode.TRUNCATED_INPUT,
                    "S3 returned an incomplete range for Shapefile " + component + " component");
        }
        String expectedRange = "bytes " + start + "-" + end + "/" + snapshot.size();
        if (!expectedRange.equals(response.contentRange())) {
            if (hasDifferentTotalLength(response.contentRange(), start, end, snapshot.size())) {
                throw sourceChanged(component);
            }
            throw new ShapefileException(
                    ShapefileErrorCode.IO_ERROR,
                    "S3 returned invalid Content-Range metadata for Shapefile " + component + " component");
        }
        if (snapshot.versionId() != null && !snapshot.versionId().equals(response.versionId())) {
            throw sourceChanged(component);
        }
        if (snapshot.eTag() != null && !snapshot.eTag().equals(response.eTag())) {
            throw sourceChanged(component);
        }
    }

    private static boolean hasDifferentTotalLength(
            String contentRange,
            long start,
            long end,
            long expectedTotal) {
        String prefix = "bytes " + start + "-" + end + "/";
        if (contentRange == null || !contentRange.startsWith(prefix)) {
            return false;
        }
        try {
            return Long.parseLong(contentRange.substring(prefix.length())) != expectedTotal;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private ShapefileException sourceChanged(ShapefileComponent component) {
        return new ShapefileException(
                ShapefileErrorCode.SOURCE_CHANGED,
                location.safeLocation() + " changed while reading Shapefile " + component + " component");
    }

    private void evictFor(int byteCount) {
        var iterator = blocks.entrySet().iterator();
        while (cachedBytes > maxCacheBytes - byteCount && iterator.hasNext()) {
            Map.Entry<BlockKey, byte[]> eldest = iterator.next();
            cachedBytes -= eldest.getValue().length;
            iterator.remove();
        }
    }

    private void ensureReadable() {
        if (closed) {
            throw new ShapefileException(ShapefileErrorCode.CLOSED, "S3 Shapefile source is closed");
        }
        if (terminalFailure != null) {
            throw terminalFailure;
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        blocks.clear();
        snapshots.clear();
        cachedBytes = 0;
    }

    private final class S3RandomAccessObject implements ShapefileRandomAccessObject {
        private final ShapefileComponent component;
        private final S3ObjectSnapshot snapshot;
        private boolean objectClosed;

        private S3RandomAccessObject(ShapefileComponent component, S3ObjectSnapshot snapshot) {
            this.component = component;
            this.snapshot = snapshot;
        }

        @Override
        public long size() {
            ensureObjectOpen();
            return snapshot.size();
        }

        @Override
        public int read(long position, ByteBuffer target) {
            ensureObjectOpen();
            if (position < 0) {
                throw new ShapefileException(
                        ShapefileErrorCode.INVALID_OFFSET,
                        component + " component has a negative read position");
            }
            if (!target.hasRemaining()) {
                return 0;
            }
            if (position >= snapshot.size()) {
                return -1;
            }
            int requested = (int) Math.min(target.remaining(), snapshot.size() - position);
            int copied = 0;
            while (copied < requested) {
                long currentPosition = position + copied;
                long blockIndex = currentPosition / blockSizeBytes;
                byte[] bytes = block(component, snapshot, blockIndex);
                int offsetInBlock = (int) (currentPosition % blockSizeBytes);
                int count = Math.min(requested - copied, bytes.length - offsetInBlock);
                if (count <= 0) {
                    throw new ShapefileException(
                            ShapefileErrorCode.TRUNCATED_INPUT,
                            "S3 block is shorter than expected for Shapefile " + component + " component");
                }
                target.put(bytes, offsetInBlock, count);
                copied += count;
            }
            return copied;
        }

        private void ensureObjectOpen() {
            ensureReadable();
            if (objectClosed) {
                throw new ShapefileException(ShapefileErrorCode.CLOSED, component + " component is closed");
            }
        }

        @Override
        public void close() {
            objectClosed = true;
        }
    }

    private record BlockKey(String bucket, String objectKey, String versionToken, long blockIndex) {
    }
}
