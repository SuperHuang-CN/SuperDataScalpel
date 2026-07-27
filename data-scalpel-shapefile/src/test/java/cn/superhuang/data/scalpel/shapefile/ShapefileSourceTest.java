package cn.superhuang.data.scalpel.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.superhuang.data.scalpel.shapefile.model.ShapefileEnvelope;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileShapeType;
import cn.superhuang.data.scalpel.shapefile.testutil.TestShapefileBuilder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShapefileSourceTest {
    private static final ShapefileEnvelope ENVELOPE =
            new ShapefileEnvelope(0, 0, 2, 2, 0, 0, 0, 0);

    @TempDir
    Path temporaryDirectory;

    @Test
    void customSourceMatchesLocalPathIncludingMetadata() throws Exception {
        Path shp = fixture("memory");
        MemorySource source = MemorySource.from(shp);

        try (ShapefileDataset local = ShapefileDataset.open(shp);
                ShapefileDataset remote = ShapefileDataset.open(source);
                ShapefileFeatureCursor localCursor = local.openCursor(ShapefileReadOptions.limit(1));
                ShapefileFeatureCursor remoteCursor = remote.openCursor(ShapefileReadOptions.limit(1))) {
            assertEquals(ShapefileSourceType.LOCAL, local.sourceInfo().type());
            assertEquals(ShapefileSourceType.CUSTOM, remote.sourceInfo().type());
            assertEquals(local.schema(), remote.schema());
            assertEquals(localCursor.next().attributes(), remoteCursor.next().attributes());
            assertEquals(
                    local.schema().spatialReference().wkt(),
                    remote.schema().spatialReference().wkt());
        }

        assertTrue(source.closed);
    }

    @Test
    void datasetOwnsSourceOnOpenFailureAndNormalClose() throws Exception {
        Path shp = fixture("ownership");
        MemorySource normal = MemorySource.from(shp);
        ShapefileDataset dataset = ShapefileDataset.open(normal);
        assertFalse(normal.closed);
        dataset.close();
        dataset.close();
        assertTrue(normal.closed);

        MemorySource malformed = MemorySource.from(shp);
        malformed.components.get(ShapefileComponent.SHP)[0] = 1;
        assertCode(ShapefileErrorCode.MALFORMED_HEADER, () -> ShapefileDataset.open(malformed));
        assertTrue(malformed.closed);
    }

    @Test
    void sourceComponentContractHasStableMissingShortReadAndClosedFailures() throws Exception {
        MemorySource missing = MemorySource.from(fixture("missing"));
        missing.components.remove(ShapefileComponent.DBF);
        assertCode(ShapefileErrorCode.MISSING_COMPONENT, () -> ShapefileDataset.open(missing));
        assertTrue(missing.closed);

        MemorySource shortRead = MemorySource.from(fixture("short"));
        shortRead.stopEarly = true;
        assertCode(ShapefileErrorCode.TRUNCATED_INPUT, () -> ShapefileDataset.open(shortRead));
        assertTrue(shortRead.closed);

        MemorySource invalidContract = MemorySource.from(fixture("null-object"));
        invalidContract.nullComponent = ShapefileComponent.SHP;
        assertCode(ShapefileErrorCode.INVALID_SOURCE, () -> ShapefileDataset.open(invalidContract));
        assertTrue(invalidContract.closed);

        assertCode(ShapefileErrorCode.CLOSED, () -> shortRead.exists(ShapefileComponent.SHP));
    }

    private Path fixture(String name) throws Exception {
        return new TestShapefileBuilder(temporaryDirectory, name, ShapefileShapeType.POINT, ENVELOPE)
                .field("Name", 'C', 20, 0)
                .record(
                        TestShapefileBuilder.point(ShapefileShapeType.POINT, 1, 2, null, null),
                        false,
                        "道路")
                .dbfCharset(StandardCharsets.UTF_8)
                .cpg("65001")
                .prj("LOCAL_CS[\"fixture\"]")
                .write();
    }

    private static void assertCode(
            ShapefileErrorCode expected,
            org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(ShapefileException.class, action).errorCode());
    }

    private static final class MemorySource implements ShapefileSource {
        private final Map<ShapefileComponent, byte[]> components;
        private ShapefileComponent nullComponent;
        private boolean stopEarly;
        private boolean closed;

        private MemorySource(Map<ShapefileComponent, byte[]> components) {
            this.components = components;
        }

        private static MemorySource from(Path shp) throws IOException {
            String fileName = shp.getFileName().toString();
            String stem = fileName.substring(0, fileName.length() - 4);
            EnumMap<ShapefileComponent, byte[]> components = new EnumMap<>(ShapefileComponent.class);
            for (ShapefileComponent component : ShapefileComponent.values()) {
                Path path = shp.resolveSibling(stem + "." + component.extension());
                if (Files.exists(path)) {
                    components.put(component, Files.readAllBytes(path));
                }
            }
            return new MemorySource(components);
        }

        @Override
        public ShapefileSourceInfo info() {
            ensureOpen();
            return new ShapefileSourceInfo(ShapefileSourceType.CUSTOM, "memory://fixture");
        }

        @Override
        public boolean exists(ShapefileComponent component) {
            ensureOpen();
            return components.containsKey(component);
        }

        @Override
        public ShapefileRandomAccessObject open(ShapefileComponent component) {
            ensureOpen();
            if (component == nullComponent) {
                return null;
            }
            byte[] bytes = components.get(component);
            if (bytes == null) {
                throw new ShapefileException(
                        ShapefileErrorCode.MISSING_COMPONENT,
                        "Missing in-memory component " + component);
            }
            return new ShapefileRandomAccessObject() {
                private boolean objectClosed;

                @Override
                public long size() {
                    ensureObjectOpen();
                    return bytes.length;
                }

                @Override
                public int read(long position, ByteBuffer target) {
                    ensureObjectOpen();
                    if (stopEarly && position >= 16) {
                        return -1;
                    }
                    if (position >= bytes.length) {
                        return -1;
                    }
                    long available = bytes.length - position;
                    if (stopEarly) {
                        available = Math.min(available, 16 - position);
                    }
                    int count = (int) Math.min(target.remaining(), available);
                    target.put(bytes, Math.toIntExact(position), count);
                    return count;
                }

                @Override
                public void close() {
                    objectClosed = true;
                }

                private void ensureObjectOpen() {
                    ensureOpen();
                    if (objectClosed) {
                        throw new ShapefileException(ShapefileErrorCode.CLOSED, "memory object is closed");
                    }
                }
            };
        }

        @Override
        public void close() {
            closed = true;
        }

        private void ensureOpen() {
            if (closed) {
                throw new ShapefileException(ShapefileErrorCode.CLOSED, "memory source is closed");
            }
        }
    }
}
