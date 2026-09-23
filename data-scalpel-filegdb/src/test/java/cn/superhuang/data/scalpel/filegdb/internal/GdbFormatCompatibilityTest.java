package cn.superhuang.data.scalpel.filegdb.internal;

import cn.superhuang.data.scalpel.filegdb.FileGdbErrorCode;
import cn.superhuang.data.scalpel.filegdb.FileGdbException;
import cn.superhuang.data.scalpel.filegdb.FileGdbOpenOptions;
import cn.superhuang.data.scalpel.filegdb.FileGdbReadLimits;
import cn.superhuang.data.scalpel.filegdb.FileGdbReadOptions;
import cn.superhuang.data.scalpel.filegdb.FileGeodatabase;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbEnvelope;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbLayerType;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbSpatialReference;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPoint;
import cn.superhuang.data.scalpel.filegdb.testutil.TestFileGdbBuilder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Format cases checked against the Esri/GDAL field and index layouts. */
class GdbFormatCompatibilityTest {
    @TempDir Path temporaryDirectory;

    @Test
    void acceptsOptionalKnownFieldTerminatorButRejectsUnknownOrTruncatedTails() throws Exception {
        ByteBuffer schema = buffer(128);
        schema.putInt(4).putInt(0x100).putShort((short) 1);
        fieldName(schema, "ID", 6);
        schema.put((byte) 4).put((byte) 2);
        int fieldEnd = schema.position();
        assertEquals(1, readHeader(bytes(schema)).fields().size());
        schema.putInt(0xefbeadde);
        assertEquals(1, readHeader(bytes(schema)).fields().size());
        schema.putInt(fieldEnd, 0x12345678);
        assertEquals(FileGdbErrorCode.MALFORMED_HEADER,
                assertThrows(FileGdbException.class, () -> readHeader(bytes(schema))).code());
        schema.position(fieldEnd + 3);
        assertThrows(FileGdbException.class, () -> readHeader(bytes(schema)));
    }

    @Test
    void readsUtf16AttributesWhileXmlRemainsUtf8() throws Exception {
        ByteBuffer schema = buffer(256);
        schema.putInt(4).putInt(0).putShort((short) 2);
        fieldName(schema, "名称", 4);
        schema.putInt(255).put((byte) 0).put((byte) 0);
        fieldName(schema, "xml", 12);
        schema.put((byte) 0).put((byte) 0).putInt(0xefbeadde);
        var definition = readHeader(bytes(schema));
        assertFalse(definition.stringsUtf8());
        byte[] text = "中文 café 😀".getBytes(StandardCharsets.UTF_16LE);
        byte[] xml = "<值/>".getBytes(StandardCharsets.UTF_8);
        ByteBuffer row = buffer(128).put((byte) text.length).put(text).put((byte) xml.length).put(xml);
        var feature = GdbRecordReader.read(1, ByteBuffer.wrap(bytes(row)), definition, FileGdbReadLimits.defaults());
        assertEquals("中文 café 😀", feature.attribute("名称"));
        assertEquals("<值/>", feature.attribute("xml"));
        assertThrows(FileGdbException.class, () -> GdbRecordReader.read(1,
                ByteBuffer.wrap(new byte[]{1, 65}), definition, FileGdbReadLimits.defaults()));
    }

    @Test
    void acceptsUnknownEmptyLayerExtentAndZeroGridButRejectsPartialNan() throws Exception {
        ByteBuffer schema = buffer(256);
        schema.putInt(4).putInt(0x301).putShort((short) 1);
        fieldName(schema, "Shape", 7);
        schema.put((byte) 0).put((byte) 1).putShort((short) 0).put((byte) 0);
        schema.putDouble(0).putDouble(0).putDouble(1).putDouble(0.001);
        int extentStart = schema.position();
        for (int i = 0; i < 4; i++) schema.putDouble(Double.NaN);
        schema.put((byte) 0).putInt(1).putDouble(0).putInt(0xefbeadde);
        assertTrue(Double.isNaN(readHeader(bytes(schema)).spatialReference().extent().xMin()));
        schema.putDouble(extentStart, 1);
        assertThrows(FileGdbException.class, () -> readHeader(bytes(schema)));
    }

    @Test
    void sparseIndexPreservesLogicalOidsAndCountsOnlyPresentSlots() throws Exception {
        var fixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);
        Path indexPath = fixture.directory().resolve(fixture.layerId("ScalarPointXY") + ".gdbtablx");
        byte[] dense = Files.readAllBytes(indexPath);
        ByteBuffer sparse = buffer(dense.length + 20).put(dense);
        sparse.putInt(8, 1027);
        // One stored page, at logical page 1. Logical page 0 is absent.
        sparse.putInt(1).putInt(2).putInt(1).putInt(1).putInt(2);
        Files.write(indexPath, sparse.array());
        try (var database = FileGeodatabase.open(fixture.directory());
             var cursor = database.openCursor(fixture.layerId("ScalarPointXY"), FileGdbReadOptions.limit(10))) {
            assertEquals(2, database.countFeatures(fixture.layerId("ScalarPointXY")));
            assertEquals(1025, cursor.next().oid());
            assertEquals(1027, cursor.next().oid());
            assertFalse(cursor.hasNext());
        }
        sparse.putInt(dense.length + 16, 3); // Two present bits, but only one stored page.
        Files.write(indexPath, sparse.array());
        try (var database = FileGeodatabase.open(fixture.directory())) {
            assertEquals(FileGdbErrorCode.MALFORMED_HEADER, assertThrows(FileGdbException.class,
                    () -> database.countFeatures(fixture.layerId("ScalarPointXY"))).code());
        }
    }

    @Test
    void readsNullAndEmptyPointEncodingsAndRejectsMismatchedShapeType() {
        var spatial = new FileGdbSpatialReference("", false, false, 0, 0, 1,
                null, null, null, null, 0, null, null,
                new FileGdbEnvelope(0, 0, 0, 0), null, null, null, null);
        var definition = new GdbTableDefinition("a00000009", "a00000009.gdbtable", 1, 100,
                1, 0, true, FileGdbLayerType.POINT, List.of(), spatial);
        for (byte[] encoded : List.of(new byte[0], new byte[]{0}, new byte[]{1, 0}, new byte[]{1, 0, 0})) {
            assertNull(GdbGeometryReader.read(ByteBuffer.wrap(encoded), definition, FileGdbReadLimits.defaults(), 1));
        }
        for (byte[] encoded : List.of(new byte[]{0, 1}, new byte[]{1, 0, 2}, new byte[]{5, 0})) {
            assertThrows(FileGdbException.class, () -> GdbGeometryReader.read(
                    ByteBuffer.wrap(encoded), definition, FileGdbReadLimits.defaults(), 1));
        }
    }

    @Test
    void preservesMissingPointZAndMInsteadOfInventingCoordinates() {
        var spatial = new FileGdbSpatialReference("", true, true, 0, 0, 1,
                -100.0, 1000.0, -100.0, 1000.0, 0, 0.0, 0.0,
                new FileGdbEnvelope(0, 0, 0, 0), null, null, null, null);
        var definition = new GdbTableDefinition("a00000009", "a00000009.gdbtable", 1, 100,
                1, 0xc0, true, FileGdbLayerType.POINT, List.of(), spatial);
        var point = assertInstanceOf(FileGdbPoint.class, GdbGeometryReader.read(
                ByteBuffer.wrap(new byte[]{11, 2, 3, 0, 0}), definition, FileGdbReadLimits.defaults(), 1));
        assertEquals(1, point.x());
        assertEquals(2, point.y());
        assertTrue(Double.isNaN(point.z()));
        assertTrue(Double.isNaN(point.m()));
    }

    @Test
    void rejectsNewerFieldCodesExplicitlyInsteadOfMisreadingTheirValues() {
        for (int code : new int[]{9, 13, 14, 15, 16}) {
            ByteBuffer schema = buffer(128);
            schema.putInt(4).putInt(0x100).putShort((short) 1);
            fieldName(schema, "unsupported", code);
            var error = assertThrows(FileGdbException.class, () -> readHeader(bytes(schema)));
            assertEquals(FileGdbErrorCode.UNSUPPORTED_FORMAT, error.code());
        }
    }

    private GdbTableDefinition readHeader(byte[] schema) throws Exception {
        Path directory = temporaryDirectory.resolve("header.gdb");
        Files.createDirectories(directory);
        ByteBuffer table = buffer(44 + schema.length);
        table.putInt(3).putInt(0).putInt(schema.length);
        table.position(32);
        table.putLong(40).putInt(schema.length).put(schema);
        Files.write(directory.resolve("a00000009.gdbtable"), table.array());
        try (var source = LocalFileGdbSource.open(directory, FileGdbOpenOptions.defaults())) {
            return GdbTableHeaderReader.read(source, "a00000009.gdbtable", "a00000009", FileGdbReadLimits.defaults());
        }
    }

    private static ByteBuffer buffer(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static void fieldName(ByteBuffer buffer, String name, int type) {
        buffer.put((byte) name.length()).put(name.getBytes(StandardCharsets.UTF_16LE));
        buffer.put((byte) 0).put((byte) type);
    }

    private static byte[] bytes(ByteBuffer buffer) {
        return java.util.Arrays.copyOf(buffer.array(), buffer.position());
    }
}
