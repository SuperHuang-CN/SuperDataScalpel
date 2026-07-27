package cn.superhuang.data.scalpel.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.superhuang.data.scalpel.shapefile.model.ShapefileEnvelope;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileShapeType;
import cn.superhuang.data.scalpel.shapefile.testutil.TestShapefileBuilder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

class ShapefileMalformedInputTest {
    private static final ShapefileEnvelope ENVELOPE =
            new ShapefileEnvelope(0, 0, 5, 5, 0, 0, 0, 0);

    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsMissingAndCaseConflictingComponents() throws Exception {
        Path missingShp = pointFixture("missing");
        Files.delete(temporaryDirectory.resolve("missing.shx"));
        assertCode(ShapefileErrorCode.MISSING_COMPONENT, () -> ShapefileDataset.open(missingShp));

        Path shp = pointFixture("conflict");
        Files.copy(temporaryDirectory.resolve("conflict.shx"), temporaryDirectory.resolve("conflict.SHX"));
        try (var paths = Files.list(temporaryDirectory)) {
            long shxComponents = paths
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase("conflict.shx"))
                    .count();
            Assumptions.assumeTrue(
                    shxComponents == 2,
                    "Filesystem is case-insensitive and cannot represent a case-conflicting component set");
        }
        Path conflictingShp = shp;
        assertCode(ShapefileErrorCode.INVALID_SOURCE, () -> ShapefileDataset.open(conflictingShp));
    }

    @Test
    void rejectsInvalidHeaderIndexAndRecordCounts() throws Exception {
        Path headerShp = pointFixture("header");
        mutateInt(headerShp, 0, 1234, ByteOrder.BIG_ENDIAN);
        assertCode(ShapefileErrorCode.MALFORMED_HEADER, () -> ShapefileDataset.open(headerShp));

        Path shp = pointFixture("offset");
        mutateInt(temporaryDirectory.resolve("offset.shx"), 100, 49, ByteOrder.BIG_ENDIAN);
        Path invalidOffset = shp;
        assertCode(ShapefileErrorCode.INVALID_OFFSET, () -> ShapefileDataset.open(invalidOffset));

        shp = pointFixture("count");
        mutateInt(temporaryDirectory.resolve("count.dbf"), 4, 2, ByteOrder.LITTLE_ENDIAN);
        Path mismatchedCount = shp;
        assertCode(ShapefileErrorCode.TRUNCATED_INPUT, () -> ShapefileDataset.open(mismatchedCount));
    }

    @Test
    void rejectsUnsupportedDbfFieldsAndInvalidCpg() throws Exception {
        Path memoShp = new TestShapefileBuilder(
                temporaryDirectory, "memo", ShapefileShapeType.POINT, ENVELOPE)
                .field("Memo", 'M', 10, 0)
                .record(TestShapefileBuilder.point(ShapefileShapeType.POINT, 1, 2, null, null), false, "x")
                .write();
        assertCode(ShapefileErrorCode.UNSUPPORTED_FORMAT, () -> ShapefileDataset.open(memoShp));

        Path shp = new TestShapefileBuilder(
                temporaryDirectory, "encoding", ShapefileShapeType.POINT, ENVELOPE)
                .field("Name", 'C', 10, 0)
                .record(TestShapefileBuilder.point(ShapefileShapeType.POINT, 1, 2, null, null), false, "x")
                .cpg("definitely-not-a-charset")
                .write();
        Path invalidEncoding = shp;
        assertCode(ShapefileErrorCode.INVALID_ENCODING, () -> ShapefileDataset.open(invalidEncoding));
    }

    @Test
    void recordFailureTerminatesCursorAndDoesNotInventNumericValues() throws Exception {
        Path shp = new TestShapefileBuilder(
                temporaryDirectory, "bad-number", ShapefileShapeType.POINT, ENVELOPE)
                .field("Count", 'N', 10, 0)
                .record(TestShapefileBuilder.point(ShapefileShapeType.POINT, 1, 2, null, null), false, "oops")
                .write();
        try (ShapefileDataset dataset = ShapefileDataset.open(shp);
                ShapefileFeatureCursor cursor = dataset.openCursor(ShapefileReadOptions.limit(1))) {
            ShapefileException first = assertThrows(ShapefileException.class, cursor::hasNext);
            assertEquals(ShapefileErrorCode.RECORD_MISMATCH, first.errorCode());
            assertSame(first, assertThrows(ShapefileException.class, cursor::hasNext));
        }
    }

    @Test
    void rejectsUnclosedPolygonAndRecordByteLimit() throws Exception {
        Path polygon = new TestShapefileBuilder(
                temporaryDirectory, "polygon", ShapefileShapeType.POLYGON, ENVELOPE)
                .field("Id", 'N', 5, 0)
                .record(TestShapefileBuilder.multipart(
                        ShapefileShapeType.POLYGON,
                        new int[] {4}, new double[] {0, 1, 1, 2}, new double[] {0, 0, 1, 2}, null, null),
                        false, "1")
                .write();
        try (ShapefileDataset dataset = ShapefileDataset.open(polygon);
                ShapefileFeatureCursor cursor = dataset.openCursor(ShapefileReadOptions.limit(1))) {
            assertCode(ShapefileErrorCode.RECORD_MISMATCH, cursor::hasNext);
        }

        Path point = pointFixture("limited");
        ShapefileReadLimits defaults = ShapefileReadLimits.defaults();
        ShapefileReadLimits limits = new ShapefileReadLimits(
                defaults.maxComponentFileBytes(), defaults.maxFields(), 8,
                defaults.maxMetadataBytes(), defaults.maxParts(), defaults.maxGeometryPoints(),
                defaults.maxIndexRecords(), defaults.maxFeaturesPerCursor());
        ShapefileOpenOptions options = new ShapefileOpenOptions(
                limits, null, java.nio.charset.Charset.forName("GB18030"), false);
        assertCode(ShapefileErrorCode.LIMIT_EXCEEDED, () -> ShapefileDataset.open(point, options));
    }

    private Path pointFixture(String name) throws Exception {
        return new TestShapefileBuilder(temporaryDirectory, name, ShapefileShapeType.POINT, ENVELOPE)
                .field("Id", 'N', 5, 0)
                .record(TestShapefileBuilder.point(ShapefileShapeType.POINT, 1, 2, null, null), false, "1")
                .write();
    }

    private static void mutateInt(Path path, int offset, int value, ByteOrder order) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        ByteBuffer.wrap(bytes).order(order).putInt(offset, value);
        Files.write(path, bytes);
    }

    private static void assertCode(ShapefileErrorCode expected, org.junit.jupiter.api.function.Executable operation) {
        assertEquals(expected, assertThrows(ShapefileException.class, operation).errorCode());
    }
}
