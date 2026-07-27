package cn.superhuang.data.scalpel.filegdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.superhuang.data.scalpel.filegdb.testutil.TestFileGdbBuilder;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileGdbReadLimitsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsTableFieldAndCatalogSlotCountsBeforeTheirLoopsOrAllocations() throws Exception {
        var fixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);
        FileGdbReadLimits defaults = FileGdbReadLimits.defaults();

        assertLimit(() -> FileGeodatabase.open(fixture.directory(), optionsWithFileLimit(100)));

        FileGdbReadLimits fieldLimit = new FileGdbReadLimits(
                defaults.maxTableFileBytes(),
                2,
                defaults.maxRecordBytes(),
                defaults.maxStringBytes(),
                defaults.maxBinaryBytes(),
                defaults.maxGeometryParts(),
                defaults.maxGeometryPoints(),
                defaults.maxIndexSlotsPerCursor(),
                defaults.maxFeaturesPerCursor());
        assertLimit(() -> FileGeodatabase.open(fixture.directory(), options(fieldLimit)));

        FileGdbReadLimits slotLimit = new FileGdbReadLimits(
                defaults.maxTableFileBytes(),
                defaults.maxFields(),
                defaults.maxRecordBytes(),
                defaults.maxStringBytes(),
                defaults.maxBinaryBytes(),
                defaults.maxGeometryParts(),
                defaults.maxGeometryPoints(),
                100,
                defaults.maxFeaturesPerCursor());
        assertLimit(() -> FileGeodatabase.open(fixture.directory(), options(slotLimit)));
    }

    @Test
    void rejectsStringAndBinaryValuesBeforeAllocatingTheirDeclaredPayloads() throws Exception {
        var fixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);
        FileGdbReadLimits defaults = FileGdbReadLimits.defaults();

        FileGdbReadLimits stringLimit = new FileGdbReadLimits(
                defaults.maxTableFileBytes(),
                defaults.maxFields(),
                defaults.maxRecordBytes(),
                48,
                defaults.maxBinaryBytes(),
                defaults.maxGeometryParts(),
                defaults.maxGeometryPoints(),
                defaults.maxIndexSlotsPerCursor(),
                defaults.maxFeaturesPerCursor());
        assertCursorLimit(fixture, stringLimit, "ScalarPointXY");

        FileGdbReadLimits binaryLimit = new FileGdbReadLimits(
                defaults.maxTableFileBytes(),
                defaults.maxFields(),
                defaults.maxRecordBytes(),
                defaults.maxStringBytes(),
                2,
                defaults.maxGeometryParts(),
                defaults.maxGeometryPoints(),
                defaults.maxIndexSlotsPerCursor(),
                defaults.maxFeaturesPerCursor());
        assertCursorLimit(fixture, binaryLimit, "ScalarPointXY");
    }

    @Test
    void rejectsPolygonPointCountBeforeAllocatingCoordinateArrays() throws Exception {
        var fixture = TestFileGdbBuilder.polygons(temporaryDirectory);
        FileGdbReadLimits defaults = FileGdbReadLimits.defaults();
        FileGdbReadLimits geometryLimit = new FileGdbReadLimits(
                defaults.maxTableFileBytes(),
                defaults.maxFields(),
                defaults.maxRecordBytes(),
                defaults.maxStringBytes(),
                defaults.maxBinaryBytes(),
                defaults.maxGeometryParts(),
                5,
                defaults.maxIndexSlotsPerCursor(),
                defaults.maxFeaturesPerCursor());
        assertCursorLimit(fixture, geometryLimit, "PolygonXY");
    }

    @Test
    void appliesSharedGeometryPointAndPartLimitsToMultiPointAndPolyline() throws Exception {
        var fixture = TestFileGdbBuilder.multiPointsAndPolylines(temporaryDirectory);
        FileGdbReadLimits defaults = FileGdbReadLimits.defaults();
        FileGdbReadLimits pointLimit = new FileGdbReadLimits(
                defaults.maxTableFileBytes(),
                defaults.maxFields(),
                defaults.maxRecordBytes(),
                defaults.maxStringBytes(),
                defaults.maxBinaryBytes(),
                defaults.maxGeometryParts(),
                3,
                defaults.maxIndexSlotsPerCursor(),
                defaults.maxFeaturesPerCursor());
        assertCursorLimit(fixture, pointLimit, "MultiPointXY");
        assertCursorLimit(fixture, pointLimit, "PolylineXY");

        FileGdbReadLimits partLimit = new FileGdbReadLimits(
                defaults.maxTableFileBytes(),
                defaults.maxFields(),
                defaults.maxRecordBytes(),
                defaults.maxStringBytes(),
                defaults.maxBinaryBytes(),
                1,
                defaults.maxGeometryPoints(),
                defaults.maxIndexSlotsPerCursor(),
                defaults.maxFeaturesPerCursor());
        assertCursorLimit(fixture, partLimit, "PolylineXY");
    }

    private static FileGdbOpenOptions optionsWithFileLimit(long bytes) {
        FileGdbReadLimits defaults = FileGdbReadLimits.defaults();
        return options(new FileGdbReadLimits(
                bytes,
                defaults.maxFields(),
                defaults.maxRecordBytes(),
                defaults.maxStringBytes(),
                defaults.maxBinaryBytes(),
                defaults.maxGeometryParts(),
                defaults.maxGeometryPoints(),
                defaults.maxIndexSlotsPerCursor(),
                defaults.maxFeaturesPerCursor()));
    }

    private static FileGdbOpenOptions options(FileGdbReadLimits limits) {
        return new FileGdbOpenOptions(false, false, limits);
    }

    private static void assertCursorLimit(
            TestFileGdbBuilder.Fixture fixture,
            FileGdbReadLimits limits,
            String layerName) {
        try (FileGeodatabase database = FileGeodatabase.open(fixture.directory(), options(limits));
                FileGdbFeatureCursor cursor = database.openCursor(
                        fixture.layerId(layerName),
                        FileGdbReadOptions.limit(1))) {
            assertEquals(
                    FileGdbErrorCode.LIMIT_EXCEEDED,
                    assertThrows(FileGdbException.class, cursor::hasNext).code());
        }
    }

    private static void assertLimit(Runnable action) {
        assertEquals(
                FileGdbErrorCode.LIMIT_EXCEEDED,
                assertThrows(FileGdbException.class, action::run).code());
    }
}
