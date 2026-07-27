package cn.superhuang.data.scalpel.filegdb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.superhuang.data.scalpel.filegdb.model.FileGdbFieldType;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbLayer;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbLayerType;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPoint;
import cn.superhuang.data.scalpel.filegdb.testutil.TestFileGdbBuilder;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileGeodatabaseTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void opensCatalogAndExposesStableSchemasWithoutSystemTables() throws Exception {
        var fixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);

        try (FileGeodatabase database = FileGeodatabase.open(fixture.directory())) {
            assertEquals(FileGdbSourceType.LOCAL, database.sourceInfo().type());
            assertEquals(fixture.directory().toAbsolutePath().normalize().toString(), database.sourceInfo().location());
            assertEquals(
                    List.of("ScalarPointXY", "PointXYZ", "PointXYM", "PointXYZM"),
                    database.layers().stream().map(FileGdbLayer::name).toList());
            assertTrue(database.layers().stream().noneMatch(FileGdbLayer::systemTable));
            assertEquals(
                    List.of(FileGdbLayerType.POINT, FileGdbLayerType.POINT, FileGdbLayerType.POINT, FileGdbLayerType.POINT),
                    database.layers().stream().map(FileGdbLayer::type).toList());

            var schema = database.schema(fixture.layerId("ScalarPointXY"));
            assertEquals("显示名称", schema.field("Name").alias());
            assertEquals(FileGdbFieldType.UUID, schema.field("ExternalId").type());
            assertEquals(FileGdbFieldType.GUID, schema.field("GlobalId").type());
            assertEquals(FileGdbFieldType.XML, schema.field("Metadata").type());
            assertEquals("GEOGCS[\"WGS 84\"]", schema.spatialReference().wkt());
            assertFalse(schema.spatialReference().hasZ());
            assertFalse(schema.spatialReference().hasM());

            assertTrue(database.schema(fixture.layerId("PointXYZ")).spatialReference().hasZ());
            assertFalse(database.schema(fixture.layerId("PointXYZ")).spatialReference().hasM());
            assertFalse(database.schema(fixture.layerId("PointXYM")).spatialReference().hasZ());
            assertTrue(database.schema(fixture.layerId("PointXYM")).spatialReference().hasM());
            assertTrue(database.schema(fixture.layerId("PointXYZM")).spatialReference().hasZ());
            assertTrue(database.schema(fixture.layerId("PointXYZM")).spatialReference().hasM());
            assertThrows(UnsupportedOperationException.class, () -> database.layers().add(database.layers().getFirst()));
            assertThrows(UnsupportedOperationException.class, () -> schema.fields().add(schema.fields().getFirst()));
        }
    }

    @Test
    void readsAllScalarTypesPointsNullsAndDeletedSlots() throws Exception {
        var fixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);

        try (FileGeodatabase database = FileGeodatabase.open(fixture.directory());
                FileGdbFeatureCursor cursor = database.openCursor(
                        fixture.layerId("ScalarPointXY"),
                        FileGdbReadOptions.limit(10))) {
            assertTrue(cursor.hasNext());
            var complete = cursor.next();
            assertEquals(1, complete.oid());
            assertEquals(1, complete.attribute("OBJECTID"));
            assertEquals((short) -12, complete.attribute("Small"));
            assertEquals(42, complete.attribute("Count"));
            assertEquals(1.25f, complete.attribute("Ratio"));
            assertEquals(-99.5d, complete.attribute("Measure"));
            assertEquals("中文 café", complete.attribute("Name"));
            assertEquals(Instant.parse("2024-01-02T03:04:05Z"), complete.attribute("ObservedAt"));
            assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), complete.attribute("ExternalId"));
            assertEquals(UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"), complete.attribute("GlobalId"));
            assertArrayEquals(new byte[] {0, 1, (byte) 0xff}, (byte[]) complete.attribute("Payload"));
            assertThrows(UnsupportedOperationException.class, () -> complete.attributes().put("Count", 99));
            assertEquals("<root>值</root>", complete.attribute("Metadata"));
            assertNull(complete.attribute("OptionalText"));
            assertEquals("x".repeat(64), complete.attribute("LongText"));
            FileGdbPoint point = assertInstanceOf(FileGdbPoint.class, complete.geometry());
            assertEquals(12.25, point.x(), 1e-9);
            assertEquals(-3.5, point.y(), 1e-9);
            assertNull(point.z());
            assertNull(point.m());

            byte[] callerCopy = (byte[]) complete.attribute("Payload");
            callerCopy[0] = 99;
            assertArrayEquals(new byte[] {0, 1, (byte) 0xff}, (byte[]) complete.attribute("Payload"));

            assertTrue(cursor.hasNext());
            var sparse = cursor.next();
            assertEquals(3, sparse.oid(), "zero index slot must be skipped without renumbering OIDs");
            assertEquals(3, sparse.attribute("OBJECTID"));
            assertNull(sparse.attribute("Small"));
            assertEquals(-7, sparse.attribute("Count"));
            assertEquals("", sparse.attribute("Name"));
            assertEquals(Instant.EPOCH, sparse.attribute("ObservedAt"));
            assertArrayEquals(new byte[0], (byte[]) sparse.attribute("Payload"));
            assertNull(sparse.geometry());
            assertFalse(cursor.hasNext());
        }
    }

    @Test
    void readsAllPointDimensionCombinations() throws Exception {
        var fixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);
        try (FileGeodatabase database = FileGeodatabase.open(fixture.directory())) {
            FileGdbPoint xyz = firstPoint(database, fixture.layerId("PointXYZ"));
            assertEquals(10, xyz.x(), 1e-9);
            assertEquals(20, xyz.y(), 1e-9);
            assertEquals(30, xyz.z(), 1e-9);
            assertNull(xyz.m());

            FileGdbPoint xym = firstPoint(database, fixture.layerId("PointXYM"));
            assertNull(xym.z());
            assertEquals(7.5, xym.m(), 1e-9);

            FileGdbPoint xyzm = firstPoint(database, fixture.layerId("PointXYZM"));
            assertEquals(3.5, xyzm.z(), 1e-9);
            assertEquals(4.5, xyzm.m(), 1e-9);
        }
    }

    @Test
    void enforcesCursorLimitsAndCloseLifecycle() throws Exception {
        var fixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);
        FileGdbReadLimits defaults = FileGdbReadLimits.defaults();
        FileGdbReadLimits limits = new FileGdbReadLimits(
                defaults.maxTableFileBytes(),
                defaults.maxFields(),
                defaults.maxRecordBytes(),
                defaults.maxStringBytes(),
                defaults.maxBinaryBytes(),
                defaults.maxGeometryParts(),
                defaults.maxGeometryPoints(),
                defaults.maxIndexSlotsPerCursor(),
                1);
        FileGdbOpenOptions options = new FileGdbOpenOptions(false, false, limits);

        FileGeodatabase database = FileGeodatabase.open(fixture.directory(), options);
        assertEquals(
                FileGdbErrorCode.LIMIT_EXCEEDED,
                assertThrows(
                                FileGdbException.class,
                                () -> database.openCursor(
                                        fixture.layerId("ScalarPointXY"),
                                        FileGdbReadOptions.limit(2)))
                        .code());

        FileGdbFeatureCursor cursor = database.openCursor(
                fixture.layerId("ScalarPointXY"),
                FileGdbReadOptions.limit(1));
        assertEquals(1, cursor.next().oid());
        assertFalse(cursor.hasNext());
        cursor.close();
        assertEquals(
                FileGdbErrorCode.CLOSED,
                assertThrows(FileGdbException.class, cursor::hasNext).code());

        FileGdbFeatureCursor databaseOwnedCursor = database.openCursor(
                fixture.layerId("PointXYZ"),
                FileGdbReadOptions.limit(1));
        database.close();
        database.close();
        assertEquals(
                FileGdbErrorCode.CLOSED,
                assertThrows(FileGdbException.class, databaseOwnedCursor::hasNext).code());
        assertEquals(
                FileGdbErrorCode.CLOSED,
                assertThrows(FileGdbException.class, database::layers).code());
    }

    @Test
    void canExplicitlyIncludeSystemCatalog() throws Exception {
        var fixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);
        FileGdbOpenOptions options = new FileGdbOpenOptions(false, true, FileGdbReadLimits.defaults());

        try (FileGeodatabase database = FileGeodatabase.open(fixture.directory(), options)) {
            assertEquals(5, database.layers().size());
            FileGdbLayer systemCatalog = database.layers().getFirst();
            assertEquals("GDB_SystemCatalog", systemCatalog.name());
            assertTrue(systemCatalog.systemTable());
            assertEquals(FileGdbLayerType.TABLE, systemCatalog.type());
        }
    }

    private static FileGdbPoint firstPoint(FileGeodatabase database, String layerId) {
        try (FileGdbFeatureCursor cursor = database.openCursor(layerId, FileGdbReadOptions.limit(1))) {
            assertTrue(cursor.hasNext());
            return assertInstanceOf(FileGdbPoint.class, cursor.next().geometry());
        }
    }
}
