package cn.superhuang.data.scalpel.filegdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPolyline;
import cn.superhuang.data.scalpel.filegdb.testutil.TestFileGdbBuilder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileGdbMalformedInputTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsNonGdbDirectoriesAndMissingCatalogFiles() throws Exception {
        Path plain = Files.createDirectory(temporaryDirectory.resolve("plain"));
        assertCode(FileGdbErrorCode.INVALID_DIRECTORY, () -> FileGeodatabase.open(plain));

        Path incomplete = Files.createDirectory(temporaryDirectory.resolve("incomplete.gdb"));
        assertCode(FileGdbErrorCode.MISSING_FILE, () -> FileGeodatabase.open(incomplete));
    }

    @Test
    void rejectsCompressedTableSignatureWithStableUnsupportedCode() throws Exception {
        var fixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);
        String physicalName = fixture.layerId("PointXYZ");
        Path table = fixture.directory().resolve(physicalName + ".gdbtable");
        write(table, 0, littleEndianInt(0));
        Files.write(table.resolveSibling(table.getFileName() + ".cdf"), new byte[] {1});
        Files.delete(fixture.directory().resolve(physicalName + ".gdbtablx"));

        assertCode(FileGdbErrorCode.UNSUPPORTED_FORMAT, () -> FileGeodatabase.open(fixture.directory()));
    }

    @Test
    void rejectsRecordOffsetsOutsideTheTableInsteadOfReturningAnEmptyFeature() throws Exception {
        var fixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);
        String physicalName = fixture.layerId("PointXYZ");
        Path index = fixture.directory().resolve(physicalName + ".gdbtablx");
        write(index, 16, new byte[] {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x7f});

        try (FileGeodatabase database = FileGeodatabase.open(fixture.directory());
                FileGdbFeatureCursor cursor = database.openCursor(physicalName, FileGdbReadOptions.limit(1))) {
            assertCode(FileGdbErrorCode.INVALID_OFFSET, cursor::hasNext);
        }
    }

    @Test
    void rejectsOversizedRecordBeforeAllocatingItsPayload() throws Exception {
        var fixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);
        String physicalName = fixture.layerId("PointXYZ");
        Path index = fixture.directory().resolve(physicalName + ".gdbtablx");
        long recordOffset = readUnsignedOffset(index, 5);
        Path table = fixture.directory().resolve(physicalName + ".gdbtable");
        write(table, recordOffset, littleEndianInt(Integer.MAX_VALUE));

        try (FileGeodatabase database = FileGeodatabase.open(fixture.directory());
                FileGdbFeatureCursor cursor = database.openCursor(physicalName, FileGdbReadOptions.limit(1))) {
            assertCode(FileGdbErrorCode.LIMIT_EXCEEDED, cursor::hasNext);
        }
    }

    @Test
    void readsPolylineGeometryAfterDiscoveringItsSchemaType() throws Exception {
        var fixture = TestFileGdbBuilder.polygons(temporaryDirectory);
        String physicalName = fixture.layerId("PolygonXY");
        Path table = fixture.directory().resolve(physicalName + ".gdbtable");
        write(table, 48, new byte[] {3});

        try (FileGeodatabase database = FileGeodatabase.open(fixture.directory())) {
            assertEquals("POLYLINE", database.schema(physicalName).layerType().name());
            try (FileGdbFeatureCursor cursor = database.openCursor(
                    physicalName, FileGdbReadOptions.limit(1))) {
                assertInstanceOf(FileGdbPolyline.class, cursor.next().geometry());
            }
        }
    }

    @Test
    void rejectsMissingPhysicalIndexNamedByCatalog() throws Exception {
        var fixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);
        String physicalName = fixture.layerId("PointXYM");
        Files.delete(fixture.directory().resolve(physicalName + ".gdbtablx"));

        assertCode(FileGdbErrorCode.MISSING_FILE, () -> FileGeodatabase.open(fixture.directory()));
    }

    @Test
    void rejectsTruncatedTableHeader() throws Exception {
        var fixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);
        String physicalName = fixture.layerId("PointXYZM");
        Files.write(
                fixture.directory().resolve(physicalName + ".gdbtable"),
                new byte[12]);

        assertCode(FileGdbErrorCode.TRUNCATED_INPUT, () -> FileGeodatabase.open(fixture.directory()));
    }

    @Test
    void listsButRefusesUnsupportedMultipatchAndRejectsRasterField() throws Exception {
        var multipatchFixture = TestFileGdbBuilder.polygons(temporaryDirectory);
        String polygon = multipatchFixture.layerId("PolygonXYZM");
        write(multipatchFixture.directory().resolve(polygon + ".gdbtable"), 48, new byte[] {9});
        try (FileGeodatabase database = FileGeodatabase.open(multipatchFixture.directory())) {
            assertEquals("MULTIPATCH", database.schema(polygon).layerType().name());
            assertCode(
                    FileGdbErrorCode.UNSUPPORTED_FORMAT,
                    () -> database.openCursor(polygon, FileGdbReadOptions.limit(1)));
        }

        Path secondParent = Files.createDirectory(temporaryDirectory.resolve("second"));
        var rasterFixture = TestFileGdbBuilder.scalarAndPoints(secondParent);
        String point = rasterFixture.layerId("PointXYZ");
        // The first field type follows schema prefix + OBJECTID name/alias at this deterministic offset.
        write(rasterFixture.directory().resolve(point + ".gdbtable"), 88, new byte[] {9});
        assertCode(FileGdbErrorCode.UNSUPPORTED_FORMAT, () -> FileGeodatabase.open(rasterFixture.directory()));
    }

    @Test
    void rejectsSymlinksByDefaultAndAllowsAnExplicitInDirectoryRootLink() throws Exception {
        var fixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);
        Path link = temporaryDirectory.resolve("linked.gdb");
        try {
            Files.createSymbolicLink(link, fixture.directory());
        } catch (UnsupportedOperationException | IOException exception) {
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    false,
                    "Symbolic links are unavailable: " + exception.getMessage());
            return;
        }

        assertCode(FileGdbErrorCode.INVALID_DIRECTORY, () -> FileGeodatabase.open(link));
        FileGdbOpenOptions options = new FileGdbOpenOptions(true, false, FileGdbReadLimits.defaults());
        try (FileGeodatabase database = FileGeodatabase.open(link, options)) {
            assertEquals(4, database.layers().size());
        }
    }

    private static long readUnsignedOffset(Path index, int width) throws IOException {
        byte[] bytes = Files.readAllBytes(index);
        long value = 0;
        for (int byteIndex = 0; byteIndex < width; byteIndex++) {
            value |= (long) (bytes[16 + byteIndex] & 0xff) << (byteIndex * 8);
        }
        return value;
    }

    private static byte[] littleEndianInt(int value) {
        return ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
    }

    private static void write(Path path, long position, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            ByteBuffer source = ByteBuffer.wrap(bytes);
            while (source.hasRemaining()) {
                position += channel.write(source, position);
            }
        }
    }

    private static void assertCode(FileGdbErrorCode expected, Runnable action) {
        assertEquals(expected, assertThrows(FileGdbException.class, action::run).code());
    }
}
