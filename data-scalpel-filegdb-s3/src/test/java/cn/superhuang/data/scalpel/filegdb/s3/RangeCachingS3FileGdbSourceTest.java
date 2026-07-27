package cn.superhuang.data.scalpel.filegdb.s3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.superhuang.data.scalpel.filegdb.FileGdbErrorCode;
import cn.superhuang.data.scalpel.filegdb.FileGdbException;
import cn.superhuang.data.scalpel.filegdb.FileGdbFeatureCursor;
import cn.superhuang.data.scalpel.filegdb.FileGdbRandomAccessObject;
import cn.superhuang.data.scalpel.filegdb.FileGdbReadLimits;
import cn.superhuang.data.scalpel.filegdb.FileGdbReadOptions;
import cn.superhuang.data.scalpel.filegdb.FileGdbSource;
import cn.superhuang.data.scalpel.filegdb.FileGdbSourceType;
import cn.superhuang.data.scalpel.filegdb.FileGeodatabase;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbFeature;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbMultiPoint;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPoint;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPolygon;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPolyline;
import cn.superhuang.data.scalpel.filegdb.testutil.TestFileGdbBuilder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RangeCachingS3FileGdbSourceTest {
    private static final int BLOCK_SIZE = 64 * 1024;
    private static final S3FileGdbLocation LOCATION =
            new S3FileGdbLocation("bucket", "datasets/sample.gdb");

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsAcrossBlocksAndMaintainsBoundedSharedLru() {
        byte[] data = new byte[2 * BLOCK_SIZE + 17];
        for (int index = 0; index < data.length; index++) {
            data[index] = (byte) index;
        }
        FakeS3ObjectAccess access = new FakeS3ObjectAccess(Map.of(LOCATION.objectKey("table.gdbtable"), data));
        RangeCachingS3FileGdbSource source = source(access, 2L * BLOCK_SIZE);

        try (FileGdbRandomAccessObject first = source.open("table.gdbtable");
                FileGdbRandomAccessObject second = source.open("table.gdbtable")) {
            assertArrayEquals(Arrays.copyOfRange(data, 10, 42), read(first, 10, 32));
            assertArrayEquals(Arrays.copyOfRange(data, 20, 30), read(second, 20, 10));
            assertEquals(1, access.ranges.size(), "open handles must share the same cached block");

            assertArrayEquals(
                    Arrays.copyOfRange(data, BLOCK_SIZE - 8, BLOCK_SIZE + 12),
                    read(first, BLOCK_SIZE - 8L, 20));
            assertEquals(2, access.ranges.size());

            read(first, 0, 1); // Make block zero most recently used.
            assertArrayEquals(
                    Arrays.copyOfRange(data, 2 * BLOCK_SIZE, data.length),
                    read(first, 2L * BLOCK_SIZE, 17));
            assertTrue(source.cachedBytes() < 2L * BLOCK_SIZE);
            assertEquals(2, source.cachedBlockCount());
            assertTrue(source.cachedBytes() <= 2L * BLOCK_SIZE);

            read(first, BLOCK_SIZE, 1); // Block one was the LRU victim and must be fetched again.
            assertEquals(4, access.ranges.size());
        }

        assertEquals(
                List.of(
                        new RangeRequest(LOCATION.objectKey("table.gdbtable"), 0, BLOCK_SIZE - 1L),
                        new RangeRequest(LOCATION.objectKey("table.gdbtable"), BLOCK_SIZE, 2L * BLOCK_SIZE - 1),
                        new RangeRequest(LOCATION.objectKey("table.gdbtable"), 2L * BLOCK_SIZE, data.length - 1L),
                        new RangeRequest(LOCATION.objectKey("table.gdbtable"), BLOCK_SIZE, 2L * BLOCK_SIZE - 1)),
                access.ranges);

        source.close();
        source.close();
        assertEquals(0, source.cachedBytes());
        assertEquals(0, source.cachedBlockCount());
        FileGdbException closed = assertThrows(FileGdbException.class, () -> source.exists("gdb"));
        assertEquals(FileGdbErrorCode.CLOSED, closed.code());
    }

    @Test
    void rejectsUnsafePhysicalFileNames() {
        FakeS3ObjectAccess access = new FakeS3ObjectAccess(Map.of());
        RangeCachingS3FileGdbSource source = source(access, BLOCK_SIZE);

        for (String fileName : List.of("../gdb", "nested/gdb", "nested\\gdb", ".", "..", "bad\u0000name")) {
            FileGdbException exception = assertThrows(FileGdbException.class, () -> source.exists(fileName));
            assertEquals(FileGdbErrorCode.INVALID_DIRECTORY, exception.code());
        }
        assertThrows(FileGdbException.class, () -> source.open(null));
        source.close();
    }

    @Test
    void mapsShortRangesAndObjectChangesWithoutCachingFailedBlocks() {
        byte[] data = new byte[BLOCK_SIZE + 1];
        FakeS3ObjectAccess access = new FakeS3ObjectAccess(Map.of(LOCATION.objectKey("table.gdbtable"), data));
        RangeCachingS3FileGdbSource source = source(access, BLOCK_SIZE);

        access.responseTransform = response -> new S3RangeResponse(
                Arrays.copyOf(response.bytes(), response.bytes().length - 1),
                response.contentLength() - 1,
                response.contentRange(),
                response.eTag(),
                response.versionId());
        try (FileGdbRandomAccessObject object = source.open("table.gdbtable")) {
            FileGdbException shortRange = assertThrows(
                    FileGdbException.class,
                    () -> read(object, 0, 1));
            assertEquals(FileGdbErrorCode.TRUNCATED_INPUT, shortRange.code());
            assertEquals(0, source.cachedBlockCount());

            access.responseTransform = response -> new S3RangeResponse(
                    response.bytes(),
                    response.contentLength(),
                    response.contentRange(),
                    "\"changed\"",
                    response.versionId());
            FileGdbException changed = assertThrows(
                    FileGdbException.class,
                    () -> read(object, 0, 1));
            assertEquals(FileGdbErrorCode.SOURCE_CHANGED, changed.code());
            assertEquals(0, source.cachedBlockCount());
        }
    }

    @Test
    void readsAllGeneratedGeometryFixturesIdenticallyToLocalSource() throws Exception {
        var scalarFixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);
        FakeS3ObjectAccess scalarAccess = fromDirectory(scalarFixture.directory(), LOCATION);
        RangeCachingS3FileGdbSource scalarSource = (RangeCachingS3FileGdbSource) S3FileGdbSource.create(
                scalarAccess,
                LOCATION,
                new S3FileGdbOptions(BLOCK_SIZE, 4L * BLOCK_SIZE));

        try (FileGeodatabase local = FileGeodatabase.open(scalarFixture.directory());
                FileGeodatabase remote = FileGeodatabase.open(scalarSource)) {
            assertEquals(FileGdbSourceType.S3, remote.sourceInfo().type());
            assertEquals(LOCATION.safeLocation(), remote.sourceInfo().location());
            assertEquals(local.layers(), remote.layers());
            for (var layer : local.layers()) {
                assertEquals(local.schema(layer.id()), remote.schema(layer.id()));
            }
            try (FileGdbFeatureCursor localCursor = local.openCursor(
                            scalarFixture.layerId("ScalarPointXY"), FileGdbReadOptions.limit(10));
                    FileGdbFeatureCursor remoteCursor = remote.openCursor(
                            scalarFixture.layerId("ScalarPointXY"), FileGdbReadOptions.limit(10))) {
                while (localCursor.hasNext()) {
                    assertTrue(remoteCursor.hasNext());
                    assertFeatureEquals(localCursor.next(), remoteCursor.next());
                }
                assertFalse(remoteCursor.hasNext());
            }
        }
        assertEquals(0, scalarSource.cachedBytes(), "database close releases the source cache");
        assertTrue(scalarAccess.ranges.stream().allMatch(request -> request.end() >= request.start()));

        Path polygonParent = Files.createDirectory(temporaryDirectory.resolve("polygon-parent"));
        var polygonFixture = TestFileGdbBuilder.polygons(polygonParent);
        S3FileGdbLocation polygonLocation = new S3FileGdbLocation("bucket", "datasets/polygon.gdb");
        FakeS3ObjectAccess polygonAccess = fromDirectory(polygonFixture.directory(), polygonLocation);
        try (FileGeodatabase local = FileGeodatabase.open(polygonFixture.directory());
                FileGeodatabase remote = FileGeodatabase.open(S3FileGdbSource.create(
                        polygonAccess,
                        polygonLocation,
                        new S3FileGdbOptions(BLOCK_SIZE, 4L * BLOCK_SIZE)));
                FileGdbFeatureCursor localCursor = local.openCursor(
                        polygonFixture.layerId("PolygonXYZM"), FileGdbReadOptions.limit(1));
                FileGdbFeatureCursor remoteCursor = remote.openCursor(
                        polygonFixture.layerId("PolygonXYZM"), FileGdbReadOptions.limit(1))) {
            FileGdbPolygon localPolygon = assertInstanceOf(FileGdbPolygon.class, localCursor.next().geometry());
            FileGdbPolygon remotePolygon = assertInstanceOf(FileGdbPolygon.class, remoteCursor.next().geometry());
            assertArrayEquals(localPolygon.ringPointCounts(), remotePolygon.ringPointCounts());
            assertArrayEquals(localPolygon.coordinates().xValues(), remotePolygon.coordinates().xValues());
            assertArrayEquals(localPolygon.coordinates().yValues(), remotePolygon.coordinates().yValues());
            assertArrayEquals(localPolygon.coordinates().zValues(), remotePolygon.coordinates().zValues());
            assertArrayEquals(localPolygon.coordinates().mValues(), remotePolygon.coordinates().mValues());
        }

        Path geometryParent = Files.createDirectory(temporaryDirectory.resolve("geometry-parent"));
        var geometryFixture = TestFileGdbBuilder.multiPointsAndPolylines(geometryParent);
        S3FileGdbLocation geometryLocation = new S3FileGdbLocation(
                "bucket", "datasets/multipoint-and-polyline.gdb");
        FakeS3ObjectAccess geometryAccess = fromDirectory(geometryFixture.directory(), geometryLocation);
        RangeCachingS3FileGdbSource geometrySource = (RangeCachingS3FileGdbSource) S3FileGdbSource.create(
                geometryAccess,
                geometryLocation,
                new S3FileGdbOptions(BLOCK_SIZE, 4L * BLOCK_SIZE));
        try (FileGeodatabase local = FileGeodatabase.open(geometryFixture.directory());
                FileGeodatabase remote = FileGeodatabase.open(geometrySource)) {
            for (String layerName : List.of("MultiPointXYZM", "PolylineXYZM")) {
                String layerId = geometryFixture.layerId(layerName);
                try (FileGdbFeatureCursor localCursor = local.openCursor(layerId, FileGdbReadOptions.limit(10));
                        FileGdbFeatureCursor remoteCursor = remote.openCursor(
                                layerId, FileGdbReadOptions.limit(10))) {
                    while (localCursor.hasNext()) {
                        assertTrue(remoteCursor.hasNext());
                        FileGdbFeature localFeature = localCursor.next();
                        FileGdbFeature remoteFeature = remoteCursor.next();
                        assertFeatureEquals(localFeature, remoteFeature);
                        if (localFeature.geometry() != null) {
                            if (layerName.startsWith("MultiPoint")) {
                                assertInstanceOf(FileGdbMultiPoint.class, remoteFeature.geometry());
                            } else {
                                assertInstanceOf(FileGdbPolyline.class, remoteFeature.geometry());
                            }
                        }
                    }
                    assertFalse(remoteCursor.hasNext());
                }
            }
        }
        assertEquals(0, geometrySource.cachedBytes());
        assertFalse(geometryAccess.ranges.isEmpty());
        assertTrue(geometryAccess.ranges.stream().allMatch(request -> request.end() >= request.start()));
    }

    @Test
    void retainsMissingAndCompressedTableBehaviorAndAppliesCoreFileLimit() throws Exception {
        var fixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);
        FakeS3ObjectAccess missing = fromDirectory(fixture.directory(), LOCATION);
        missing.objects.remove(LOCATION.objectKey("a00000001.gdbtable"));
        FileGdbException missingException = assertThrows(
                FileGdbException.class,
                () -> FileGeodatabase.open(S3FileGdbSource.create(
                        missing, LOCATION, new S3FileGdbOptions(BLOCK_SIZE, BLOCK_SIZE))));
        assertEquals(FileGdbErrorCode.MISSING_FILE, missingException.code());

        FakeS3ObjectAccess compressed = fromDirectory(fixture.directory(), LOCATION);
        byte[] compressedTable = compressed.objects.get(LOCATION.objectKey("a00000009.gdbtable")).clone();
        Arrays.fill(compressedTable, 0, Integer.BYTES, (byte) 0);
        compressed.objects.put(LOCATION.objectKey("a00000009.gdbtable"), compressedTable);
        compressed.objects.put(LOCATION.objectKey("a00000009.gdbtable.cdf"), new byte[] {1});
        compressed.objects.remove(LOCATION.objectKey("a00000009.gdbtablx"));
        FileGdbException compressedException = assertThrows(
                FileGdbException.class,
                () -> FileGeodatabase.open(S3FileGdbSource.create(
                        compressed, LOCATION, new S3FileGdbOptions(BLOCK_SIZE, BLOCK_SIZE))));
        assertEquals(FileGdbErrorCode.UNSUPPORTED_FORMAT, compressedException.code());

        FakeS3ObjectAccess oversized = fromDirectory(fixture.directory(), LOCATION);
        oversized.sizeOverrides.put(
                LOCATION.objectKey("a00000001.gdbtable"),
                FileGdbReadLimits.defaults().maxTableFileBytes() + 1);
        FileGdbException limit = assertThrows(
                FileGdbException.class,
                () -> FileGeodatabase.open(S3FileGdbSource.create(
                        oversized, LOCATION, new S3FileGdbOptions(BLOCK_SIZE, BLOCK_SIZE))));
        assertEquals(FileGdbErrorCode.LIMIT_EXCEEDED, limit.code());
        assertTrue(
                oversized.ranges.stream().noneMatch(request -> request.key().endsWith("a00000001.gdbtable")),
                "oversized objects must be rejected before their data GET");
    }

    private static RangeCachingS3FileGdbSource source(FakeS3ObjectAccess access, long maxCacheBytes) {
        return (RangeCachingS3FileGdbSource) S3FileGdbSource.create(
                access,
                LOCATION,
                new S3FileGdbOptions(BLOCK_SIZE, maxCacheBytes));
    }

    private static byte[] read(FileGdbRandomAccessObject object, long position, int length) {
        ByteBuffer target = ByteBuffer.allocate(length);
        assertEquals(length, object.read(position, target));
        return target.array();
    }

    private static FakeS3ObjectAccess fromDirectory(Path directory, S3FileGdbLocation location) throws IOException {
        Map<String, byte[]> objects = new LinkedHashMap<>();
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                objects.put(location.objectKey(file.getFileName().toString()), Files.readAllBytes(file));
            }
        }
        return new FakeS3ObjectAccess(objects);
    }

    private static void assertFeatureEquals(FileGdbFeature expected, FileGdbFeature actual) {
        assertEquals(expected.oid(), actual.oid());
        assertEquals(expected.geometry(), actual.geometry());
        assertEquals(expected.attributes().keySet(), actual.attributes().keySet());
        for (String name : expected.attributes().keySet()) {
            Object expectedValue = expected.attribute(name);
            Object actualValue = actual.attribute(name);
            if (expectedValue instanceof byte[] expectedBytes) {
                assertArrayEquals(expectedBytes, assertInstanceOf(byte[].class, actualValue));
            } else {
                assertEquals(expectedValue, actualValue, "attribute " + name);
            }
        }
        if (expected.geometry() instanceof FileGdbPoint expectedPoint) {
            assertEquals(expectedPoint, assertInstanceOf(FileGdbPoint.class, actual.geometry()));
        }
    }

    private static final class FakeS3ObjectAccess implements S3ObjectAccess {
        private final Map<String, byte[]> objects;
        private final Map<String, Long> sizeOverrides = new HashMap<>();
        private final List<RangeRequest> ranges = new ArrayList<>();
        private UnaryOperator<S3RangeResponse> responseTransform = UnaryOperator.identity();

        private FakeS3ObjectAccess(Map<String, byte[]> objects) {
            this.objects = new LinkedHashMap<>(objects);
        }

        @Override
        public S3ObjectMetadata head(String objectKey, String fileName, boolean allowMissing) {
            byte[] bytes = objects.get(objectKey);
            if (bytes == null) {
                if (allowMissing) {
                    return null;
                }
                throw new FileGdbException(FileGdbErrorCode.MISSING_FILE, "Missing FileGDB file " + fileName);
            }
            long size = sizeOverrides.getOrDefault(objectKey, (long) bytes.length);
            return new S3ObjectMetadata(objectKey, size, "\"etag-" + objectKey + "\"", null);
        }

        @Override
        public S3RangeResponse readRange(
                S3ObjectMetadata metadata,
                String fileName,
                long startInclusive,
                long endInclusive) {
            ranges.add(new RangeRequest(metadata.objectKey(), startInclusive, endInclusive));
            byte[] object = objects.get(metadata.objectKey());
            byte[] bytes = Arrays.copyOfRange(
                    object,
                    Math.toIntExact(startInclusive),
                    Math.toIntExact(endInclusive + 1));
            S3RangeResponse response = new S3RangeResponse(
                    bytes,
                    (long) bytes.length,
                    "bytes " + startInclusive + "-" + endInclusive + "/" + metadata.size(),
                    metadata.eTag(),
                    metadata.versionId());
            return responseTransform.apply(response);
        }
    }

    private record RangeRequest(String key, long start, long end) {
    }
}
