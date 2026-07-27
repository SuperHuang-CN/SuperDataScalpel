package cn.superhuang.data.scalpel.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.superhuang.data.scalpel.shapefile.model.ShapefileEnvelope;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileShapeType;
import cn.superhuang.data.scalpel.shapefile.testutil.TestShapefileBuilder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShapefileStructuralValidationTest {
    private static final ShapefileEnvelope ENVELOPE =
            new ShapefileEnvelope(0, 0, 5, 5, 0, 10, 0, 10);

    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsHeaderSignatureReservedVersionLengthTypeAndRangeMismatches() throws Exception {
        Path signature = pointFixture("signature");
        mutateInt(signature, 0, 1234, ByteOrder.BIG_ENDIAN);
        assertOpenCode(ShapefileErrorCode.MALFORMED_HEADER, signature);

        Path reserved = pointFixture("reserved");
        mutateInt(reserved, 4, 1, ByteOrder.BIG_ENDIAN);
        assertOpenCode(ShapefileErrorCode.MALFORMED_HEADER, reserved);

        Path version = pointFixture("version");
        mutateInt(version, 28, 999, ByteOrder.LITTLE_ENDIAN);
        assertOpenCode(ShapefileErrorCode.MALFORMED_HEADER, version);

        Path length = pointFixture("length");
        mutateInt(length, 24, 50, ByteOrder.BIG_ENDIAN);
        assertOpenCode(ShapefileErrorCode.MALFORMED_HEADER, length);

        Path type = pointFixture("type");
        mutateInt(temporaryDirectory.resolve("type.shx"), 32, ShapefileShapeType.POINT_M.code(), ByteOrder.LITTLE_ENDIAN);
        assertOpenCode(ShapefileErrorCode.MALFORMED_HEADER, type);

        Path range = pointFixture("range");
        mutateDouble(temporaryDirectory.resolve("range.shx"), 36, 99, ByteOrder.LITTLE_ENDIAN);
        assertOpenCode(ShapefileErrorCode.MALFORMED_HEADER, range);
    }

    @Test
    void rejectsUnsupportedShapeTypesAndIncompleteShxEntries() throws Exception {
        Path multiPatch = pointFixture("multipatch");
        mutateInt(multiPatch, 32, ShapefileShapeType.MULTIPATCH.code(), ByteOrder.LITTLE_ENDIAN);
        mutateInt(temporaryDirectory.resolve("multipatch.shx"), 32, ShapefileShapeType.MULTIPATCH.code(), ByteOrder.LITTLE_ENDIAN);
        assertOpenCode(ShapefileErrorCode.UNSUPPORTED_FORMAT, multiPatch);

        Path unknown = pointFixture("unknown");
        mutateInt(unknown, 32, 99, ByteOrder.LITTLE_ENDIAN);
        mutateInt(temporaryDirectory.resolve("unknown.shx"), 32, 99, ByteOrder.LITTLE_ENDIAN);
        assertOpenCode(ShapefileErrorCode.UNSUPPORTED_FORMAT, unknown);

        Path incomplete = pointFixture("incomplete-index");
        Path shx = temporaryDirectory.resolve("incomplete-index.shx");
        byte[] bytes = Files.readAllBytes(shx);
        bytes = Arrays.copyOf(bytes, bytes.length - 1);
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(24, bytes.length / 2);
        Files.write(shx, bytes);
        assertOpenCode(ShapefileErrorCode.MALFORMED_HEADER, incomplete);
    }

    @Test
    void validatesIndexOffsetsAndShpRecordLengthsAtOpen() throws Exception {
        Path headerOffset = pointFixture("header-offset");
        mutateInt(temporaryDirectory.resolve("header-offset.shx"), 100, 49, ByteOrder.BIG_ENDIAN);
        assertOpenCode(ShapefileErrorCode.INVALID_OFFSET, headerOffset);

        Path beyondEnd = pointFixture("beyond-end");
        mutateInt(temporaryDirectory.resolve("beyond-end.shx"), 100, 10_000, ByteOrder.BIG_ENDIAN);
        assertOpenCode(ShapefileErrorCode.INVALID_OFFSET, beyondEnd);

        Path lengthMismatch = pointFixture("record-length");
        mutateInt(lengthMismatch, 104, 9, ByteOrder.BIG_ENDIAN);
        assertOpenCode(ShapefileErrorCode.RECORD_MISMATCH, lengthMismatch);

        Path overlap = twoPointFixture("overlap");
        mutateInt(temporaryDirectory.resolve("overlap.shx"), 108, 50, ByteOrder.BIG_ENDIAN);
        assertOpenCode(ShapefileErrorCode.INVALID_OFFSET, overlap);
    }

    @Test
    void validatesRecordNumberTypeTrailingBytesAndArrayBoundsLazily() throws Exception {
        Path recordNumber = pointFixture("record-number");
        mutateInt(recordNumber, 100, 2, ByteOrder.BIG_ENDIAN);
        assertCursorCode(ShapefileErrorCode.RECORD_MISMATCH, recordNumber);

        Path recordType = pointFixture("record-type");
        mutateInt(recordType, 108, ShapefileShapeType.POINT_M.code(), ByteOrder.LITTLE_ENDIAN);
        assertCursorCode(ShapefileErrorCode.RECORD_MISMATCH, recordType);

        byte[] pointWithTrailingBytes = Arrays.copyOf(
                TestShapefileBuilder.point(ShapefileShapeType.POINT, 1, 2, null, null), 22);
        Path trailing = fixture("trailing", ShapefileShapeType.POINT, pointWithTrailingBytes);
        assertCursorCode(ShapefileErrorCode.RECORD_MISMATCH, trailing);

        byte[] multiPoint = TestShapefileBuilder.multiPoint(
                ShapefileShapeType.MULTIPOINT,
                new double[] {1}, new double[] {2}, null, null);
        ByteBuffer.wrap(multiPoint).order(ByteOrder.LITTLE_ENDIAN).putInt(36, 10_000_000);
        Path hugeCount = fixture("huge-count", ShapefileShapeType.MULTIPOINT, multiPoint);
        assertCursorCode(ShapefileErrorCode.TRUNCATED_INPUT, hugeCount);

        byte[] line = TestShapefileBuilder.multipart(
                ShapefileShapeType.POLYLINE,
                new int[] {2}, new double[] {0, 1}, new double[] {0, 1}, null, null);
        ByteBuffer.wrap(line).order(ByteOrder.LITTLE_ENDIAN).putInt(44, 1);
        Path invalidStart = fixture("invalid-start", ShapefileShapeType.POLYLINE, line);
        assertCursorCode(ShapefileErrorCode.RECORD_MISMATCH, invalidStart);

        byte[] pointZ = Arrays.copyOf(
                TestShapefileBuilder.point(ShapefileShapeType.POINT_Z, 1, 2, 3.0, null), 32);
        Path incompleteM = fixture("incomplete-point-m", ShapefileShapeType.POINT_Z, pointZ);
        assertCursorCode(ShapefileErrorCode.RECORD_MISMATCH, incompleteM);
    }

    @Test
    void rejectsMalformedDbfSchemaCountsMarkersAndValuesWithContext() throws Exception {
        Path dbaseIv = pointFixture("dbase-iv");
        mutateByte(temporaryDirectory.resolve("dbase-iv.dbf"), 0, 0x04);
        try (ShapefileDataset dataset = ShapefileDataset.open(dbaseIv)) {
            assertEquals(1, dataset.schema().recordCount());
        }

        Path memoVersion = pointFixture("memo-version");
        mutateByte(temporaryDirectory.resolve("memo-version.dbf"), 0, 0x83);
        assertOpenCode(ShapefileErrorCode.UNSUPPORTED_FORMAT, memoVersion);

        Path duplicate = new TestShapefileBuilder(
                temporaryDirectory, "duplicate", ShapefileShapeType.POINT, ENVELOPE)
                .field("Same", 'C', 4, 0)
                .field("Same", 'C', 4, 0)
                .record(TestShapefileBuilder.point(ShapefileShapeType.POINT, 1, 2, null, null), false, "a", "b")
                .write();
        assertOpenCode(ShapefileErrorCode.MALFORMED_HEADER, duplicate);

        Path terminator = pointFixture("terminator");
        mutateByte(temporaryDirectory.resolve("terminator.dbf"), 64, 0);
        assertOpenCode(ShapefileErrorCode.MALFORMED_HEADER, terminator);

        Path countMismatch = twoPointFixture("dbf-count");
        mutateInt(temporaryDirectory.resolve("dbf-count.dbf"), 4, 1, ByteOrder.LITTLE_ENDIAN);
        assertOpenCode(ShapefileErrorCode.RECORD_MISMATCH, countMismatch);

        Path marker = pointFixture("marker");
        mutateByte(temporaryDirectory.resolve("marker.dbf"), 65, 0x7F);
        assertCursorCode(ShapefileErrorCode.RECORD_MISMATCH, marker);

        assertInvalidFieldValue("bad-number", 'N', 10, 0, "oops", "Value");
        assertInvalidFieldValue("bad-decimal", 'F', 10, 2, "1.2x", "Value");
        assertInvalidFieldValue("bad-boolean", 'L', 1, 0, "X", "Value");
        assertInvalidFieldValue("bad-date", 'D', 8, 0, "20230229", "Value");
    }

    @Test
    void enforcesEachConfigurableAllocationOrIterationLimit() throws Exception {
        Path fields = new TestShapefileBuilder(
                temporaryDirectory, "field-limit", ShapefileShapeType.POINT, ENVELOPE)
                .field("A", 'C', 2, 0)
                .field("B", 'C', 2, 0)
                .record(TestShapefileBuilder.point(ShapefileShapeType.POINT, 1, 2, null, null), false, "a", "b")
                .write();
        assertOpenCode(ShapefileErrorCode.LIMIT_EXCEEDED, fields, limits(1, 64 * 1024 * 1024, 1024 * 1024, 100_000, 10_000_000, 100_000_000));

        Path records = twoPointFixture("index-limit");
        assertOpenCode(ShapefileErrorCode.LIMIT_EXCEEDED, records, limits(1024, 64 * 1024 * 1024, 1024 * 1024, 100_000, 10_000_000, 1));

        Path metadata = pointFixture("metadata-limit");
        Files.writeString(temporaryDirectory.resolve("metadata-limit.prj"), "12345");
        assertOpenCode(ShapefileErrorCode.LIMIT_EXCEEDED, metadata, limits(1024, 64 * 1024 * 1024, 4, 100_000, 10_000_000, 100_000_000));

        byte[] twoPartLine = TestShapefileBuilder.multipart(
                ShapefileShapeType.POLYLINE,
                new int[] {2, 2},
                new double[] {0, 1, 2, 3}, new double[] {0, 1, 2, 3}, null, null);
        Path parts = fixture("part-limit", ShapefileShapeType.POLYLINE, twoPartLine);
        assertCursorCode(ShapefileErrorCode.LIMIT_EXCEEDED, parts, limits(1024, 64 * 1024 * 1024, 1024 * 1024, 1, 10_000_000, 100_000_000));

        byte[] twoPoints = TestShapefileBuilder.multiPoint(
                ShapefileShapeType.MULTIPOINT,
                new double[] {0, 1}, new double[] {0, 1}, null, null);
        Path points = fixture("point-limit", ShapefileShapeType.MULTIPOINT, twoPoints);
        assertCursorCode(ShapefileErrorCode.LIMIT_EXCEEDED, points, limits(1024, 64 * 1024 * 1024, 1024 * 1024, 100_000, 1, 100_000_000));
    }

    @Test
    void nextAfterEndAndFailureStateFollowIteratorContract() throws Exception {
        Path empty = new TestShapefileBuilder(
                temporaryDirectory, "empty", ShapefileShapeType.POINT, ENVELOPE)
                .field("Id", 'N', 5, 0)
                .write();
        try (ShapefileDataset dataset = ShapefileDataset.open(empty);
                ShapefileFeatureCursor cursor = dataset.openCursor(ShapefileReadOptions.limit(1))) {
            assertThrows(NoSuchElementException.class, cursor::next);
            assertThrows(NoSuchElementException.class, cursor::next);
        }

        Path failed = pointFixture("stable-failure");
        mutateInt(failed, 100, 2, ByteOrder.BIG_ENDIAN);
        try (ShapefileDataset dataset = ShapefileDataset.open(failed);
                ShapefileFeatureCursor cursor = dataset.openCursor(ShapefileReadOptions.limit(1))) {
            ShapefileException first = assertThrows(ShapefileException.class, cursor::hasNext);
            assertSame(first, assertThrows(ShapefileException.class, cursor::next));
            cursor.close();
            assertEquals(
                    ShapefileErrorCode.CLOSED,
                    assertThrows(ShapefileException.class, cursor::hasNext).errorCode());
        }
    }

    private void assertInvalidFieldValue(
            String name,
            char type,
            int length,
            int decimals,
            String value,
            String fieldName) throws Exception {
        Path shp = new TestShapefileBuilder(
                temporaryDirectory, name, ShapefileShapeType.POINT, ENVELOPE)
                .field(fieldName, type, length, decimals)
                .record(TestShapefileBuilder.point(ShapefileShapeType.POINT, 1, 2, null, null), false, value)
                .write();
        try (ShapefileDataset dataset = ShapefileDataset.open(shp);
                ShapefileFeatureCursor cursor = dataset.openCursor(ShapefileReadOptions.limit(1))) {
            ShapefileException failure = assertThrows(ShapefileException.class, cursor::hasNext);
            assertEquals(ShapefileErrorCode.RECORD_MISMATCH, failure.errorCode());
            assertTrue(failure.getMessage().contains(fieldName));
            assertTrue(failure.getMessage().contains("record 1"));
        }
    }

    private Path pointFixture(String name) throws Exception {
        return fixture(
                name,
                ShapefileShapeType.POINT,
                TestShapefileBuilder.point(ShapefileShapeType.POINT, 1, 2, null, null));
    }

    private Path twoPointFixture(String name) throws Exception {
        return new TestShapefileBuilder(temporaryDirectory, name, ShapefileShapeType.POINT, ENVELOPE)
                .field("Id", 'N', 5, 0)
                .record(TestShapefileBuilder.point(ShapefileShapeType.POINT, 1, 2, null, null), false, "1")
                .record(TestShapefileBuilder.point(ShapefileShapeType.POINT, 2, 3, null, null), false, "2")
                .write();
    }

    private Path fixture(String name, ShapefileShapeType type, byte[] geometry) throws Exception {
        return new TestShapefileBuilder(temporaryDirectory, name, type, ENVELOPE)
                .field("Id", 'N', 5, 0)
                .record(geometry, false, "1")
                .write();
    }

    private static ShapefileReadLimits limits(
            int maxFields,
            int maxRecordBytes,
            int maxMetadataBytes,
            int maxParts,
            int maxPoints,
            long maxIndexRecords) {
        ShapefileReadLimits defaults = ShapefileReadLimits.defaults();
        return new ShapefileReadLimits(
                defaults.maxComponentFileBytes(),
                maxFields,
                maxRecordBytes,
                maxMetadataBytes,
                maxParts,
                maxPoints,
                maxIndexRecords,
                defaults.maxFeaturesPerCursor());
    }

    private static void assertOpenCode(ShapefileErrorCode expected, Path shp) {
        assertOpenCode(expected, shp, ShapefileReadLimits.defaults());
    }

    private static void assertOpenCode(ShapefileErrorCode expected, Path shp, ShapefileReadLimits limits) {
        ShapefileOpenOptions options = new ShapefileOpenOptions(
                limits, null, Charset.forName("GB18030"), false);
        assertEquals(
                expected,
                assertThrows(ShapefileException.class, () -> ShapefileDataset.open(shp, options)).errorCode());
    }

    private static void assertCursorCode(ShapefileErrorCode expected, Path shp) {
        assertCursorCode(expected, shp, ShapefileReadLimits.defaults());
    }

    private static void assertCursorCode(ShapefileErrorCode expected, Path shp, ShapefileReadLimits limits) {
        ShapefileOpenOptions options = new ShapefileOpenOptions(
                limits, null, Charset.forName("GB18030"), false);
        try (ShapefileDataset dataset = ShapefileDataset.open(shp, options);
                ShapefileFeatureCursor cursor = dataset.openCursor(ShapefileReadOptions.limit(1))) {
            assertEquals(expected, assertThrows(ShapefileException.class, cursor::hasNext).errorCode());
        }
    }

    private static void mutateByte(Path path, int offset, int value) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        bytes[offset] = (byte) value;
        Files.write(path, bytes);
    }

    private static void mutateInt(Path path, int offset, int value, ByteOrder order) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        ByteBuffer.wrap(bytes).order(order).putInt(offset, value);
        Files.write(path, bytes);
    }

    private static void mutateDouble(Path path, int offset, double value, ByteOrder order) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        ByteBuffer.wrap(bytes).order(order).putDouble(offset, value);
        Files.write(path, bytes);
    }
}
