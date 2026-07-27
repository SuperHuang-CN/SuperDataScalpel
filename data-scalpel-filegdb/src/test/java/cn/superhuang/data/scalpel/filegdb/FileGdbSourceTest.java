package cn.superhuang.data.scalpel.filegdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPoint;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPolygon;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbMultiPoint;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPolyline;
import cn.superhuang.data.scalpel.filegdb.testutil.TestFileGdbBuilder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileGdbSourceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsAllSupportedGeometryFixturesThroughStorageNeutralSource() throws Exception {
        var scalarFixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);
        MemorySource scalarSource = MemorySource.from(scalarFixture.directory());

        try (FileGeodatabase database = FileGeodatabase.open(scalarSource)) {
            assertEquals(FileGdbSourceType.S3, database.sourceInfo().type());
            assertEquals("memory://scalar-and-point.gdb", database.sourceInfo().location());
            assertEquals(4, database.layers().size());
            try (FileGdbFeatureCursor cursor = database.openCursor(
                    scalarFixture.layerId("ScalarPointXY"),
                    FileGdbReadOptions.limit(10))) {
                var first = cursor.next();
                assertEquals(1, first.oid());
                assertEquals("中文 café", first.attribute("Name"));
                FileGdbPoint point = assertInstanceOf(FileGdbPoint.class, first.geometry());
                assertEquals(12.25, point.x(), 1e-9);
                assertEquals(-3.5, point.y(), 1e-9);
                assertEquals(3, cursor.next().oid());
                assertFalse(cursor.hasNext());
            }
        }
        assertTrue(scalarSource.closed);

        Path polygonParent = Files.createDirectory(temporaryDirectory.resolve("polygon-source"));
        var polygonFixture = TestFileGdbBuilder.polygons(polygonParent);
        try (FileGeodatabase database = FileGeodatabase.open(MemorySource.from(polygonFixture.directory()));
                FileGdbFeatureCursor cursor = database.openCursor(
                        polygonFixture.layerId("PolygonXYZM"),
                        FileGdbReadOptions.limit(1))) {
            FileGdbPolygon polygon = assertInstanceOf(FileGdbPolygon.class, cursor.next().geometry());
            assertEquals(2, polygon.ringCount());
            assertTrue(polygon.coordinates().hasZ());
            assertTrue(polygon.coordinates().hasM());
        }

        Path geometryParent = Files.createDirectory(temporaryDirectory.resolve("geometry-source"));
        var geometryFixture = TestFileGdbBuilder.multiPointsAndPolylines(geometryParent);
        try (FileGeodatabase local = FileGeodatabase.open(geometryFixture.directory());
                FileGeodatabase memory = FileGeodatabase.open(MemorySource.from(geometryFixture.directory()))) {
            assertEquals(local.layers(), memory.layers());
            for (var layer : local.layers()) {
                assertEquals(local.schema(layer.id()), memory.schema(layer.id()));
                try (FileGdbFeatureCursor localCursor = local.openCursor(layer.id(), FileGdbReadOptions.limit(10));
                        FileGdbFeatureCursor memoryCursor = memory.openCursor(
                                layer.id(), FileGdbReadOptions.limit(10))) {
                    while (localCursor.hasNext()) {
                        assertTrue(memoryCursor.hasNext());
                        var localFeature = localCursor.next();
                        var memoryFeature = memoryCursor.next();
                        assertEquals(localFeature.oid(), memoryFeature.oid());
                        assertEquals(localFeature.attributes(), memoryFeature.attributes());
                        assertEquals(localFeature.geometry(), memoryFeature.geometry());
                    }
                    assertFalse(memoryCursor.hasNext());
                }
            }
            try (FileGdbFeatureCursor cursor = memory.openCursor(
                    geometryFixture.layerId("MultiPointXYZM"), FileGdbReadOptions.limit(1))) {
                assertInstanceOf(FileGdbMultiPoint.class, cursor.next().geometry());
            }
            try (FileGdbFeatureCursor cursor = memory.openCursor(
                    geometryFixture.layerId("PolylineXYZM"), FileGdbReadOptions.limit(1))) {
                assertInstanceOf(FileGdbPolyline.class, cursor.next().geometry());
            }
        }
    }

    @Test
    void closesOwnedSourceWhenOpenFailsAndRejectsZeroProgress() throws Exception {
        var fixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);
        MemorySource missingCatalog = MemorySource.from(fixture.directory());
        missingCatalog.objects.remove("a00000001.gdbtable");

        FileGdbException missing = assertThrows(
                FileGdbException.class,
                () -> FileGeodatabase.open(missingCatalog));
        assertEquals(FileGdbErrorCode.MISSING_FILE, missing.code());
        assertTrue(missingCatalog.closed);

        MemorySource zeroProgress = MemorySource.from(fixture.directory());
        zeroProgress.zeroProgressFile = "a00000001.gdbtable";
        FileGdbException stalled = assertThrows(
                FileGdbException.class,
                () -> FileGeodatabase.open(zeroProgress));
        assertEquals(FileGdbErrorCode.IO_ERROR, stalled.code());
        assertTrue(zeroProgress.closed);
    }

    @Test
    void classifiesPrematureSourceEndAsTruncatedInput() throws Exception {
        var fixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);
        MemorySource truncated = MemorySource.from(fixture.directory());
        truncated.prematureEndFile = "a00000001.gdbtable";

        FileGdbException exception = assertThrows(
                FileGdbException.class,
                () -> FileGeodatabase.open(truncated));
        assertEquals(FileGdbErrorCode.TRUNCATED_INPUT, exception.code());
        assertTrue(truncated.closed);
    }

    @Test
    void closesCursorsBeforeSourceAndPreservesSuppressedCloseFailures() throws Exception {
        var fixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);
        String layerId = fixture.layerId("ScalarPointXY");
        MemorySource source = MemorySource.from(fixture.directory());
        FileGeodatabase database = FileGeodatabase.open(source);
        database.openCursor(layerId, FileGdbReadOptions.limit(1));
        source.closeFailureFile = layerId + ".gdbtable";
        source.failSourceClose = true;
        source.closeEvents.clear();

        FileGdbException failure = assertThrows(FileGdbException.class, database::close);

        assertEquals("object:" + layerId + ".gdbtable", source.closeEvents.get(0));
        assertEquals("object:" + layerId + ".gdbtablx", source.closeEvents.get(1));
        assertEquals("source", source.closeEvents.get(2));
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("source close failure", failure.getSuppressed()[0].getMessage());
        assertTrue(source.closed);
        database.close();
    }

    private static final class MemorySource implements FileGdbSource {
        private final Map<String, byte[]> objects;
        private boolean closed;
        private String zeroProgressFile;
        private String prematureEndFile;
        private String closeFailureFile;
        private boolean failSourceClose;
        private final List<String> closeEvents = new ArrayList<>();

        private MemorySource(Map<String, byte[]> objects) {
            this.objects = objects;
        }

        static MemorySource from(Path directory) throws IOException {
            Map<String, byte[]> objects = new LinkedHashMap<>();
            try (var files = Files.list(directory)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    objects.put(file.getFileName().toString(), Files.readAllBytes(file));
                }
            }
            return new MemorySource(objects);
        }

        @Override
        public FileGdbSourceInfo info() {
            ensureOpen();
            return new FileGdbSourceInfo(FileGdbSourceType.S3, "memory://scalar-and-point.gdb");
        }

        @Override
        public boolean exists(String fileName) {
            ensureOpen();
            return objects.containsKey(fileName);
        }

        @Override
        public FileGdbRandomAccessObject open(String fileName) {
            ensureOpen();
            byte[] bytes = objects.get(fileName);
            if (bytes == null) {
                throw new FileGdbException(FileGdbErrorCode.MISSING_FILE, "Missing " + fileName);
            }
            return new FileGdbRandomAccessObject() {
                private boolean objectClosed;

                @Override
                public long size() {
                    ensureObjectOpen();
                    return bytes.length;
                }

                @Override
                public int read(long position, ByteBuffer target) {
                    ensureObjectOpen();
                    if (fileName.equals(zeroProgressFile)) {
                        return 0;
                    }
                    if (fileName.equals(prematureEndFile) && position >= 4) {
                        return -1;
                    }
                    if (position >= bytes.length) {
                        return -1;
                    }
                    int length = Math.min(target.remaining(), bytes.length - Math.toIntExact(position));
                    if (fileName.equals(prematureEndFile)) {
                        length = Math.min(length, Math.max(0, 4 - Math.toIntExact(position)));
                    }
                    if (length == 0) {
                        return -1;
                    }
                    target.put(bytes, Math.toIntExact(position), length);
                    return length;
                }

                private void ensureObjectOpen() {
                    if (objectClosed || closed) {
                        throw new FileGdbException(FileGdbErrorCode.CLOSED, fileName + " is closed");
                    }
                }

                @Override
                public void close() {
                    if (objectClosed) {
                        return;
                    }
                    objectClosed = true;
                    closeEvents.add("object:" + fileName);
                    if (fileName.equals(closeFailureFile)) {
                        throw new FileGdbException(FileGdbErrorCode.IO_ERROR, "object close failure");
                    }
                }
            };
        }

        private void ensureOpen() {
            if (closed) {
                throw new FileGdbException(FileGdbErrorCode.CLOSED, "Memory source is closed");
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            closeEvents.add("source");
            if (failSourceClose) {
                throw new FileGdbException(FileGdbErrorCode.IO_ERROR, "source close failure");
            }
        }
    }
}
