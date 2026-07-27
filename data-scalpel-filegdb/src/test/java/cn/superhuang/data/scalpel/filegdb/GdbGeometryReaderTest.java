package cn.superhuang.data.scalpel.filegdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.superhuang.data.scalpel.filegdb.model.FileGdbLayerType;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbMultiPoint;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPolygon;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPolyline;
import cn.superhuang.data.scalpel.filegdb.testutil.TestFileGdbBuilder;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GdbGeometryReaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesCursorReadabilityForSupportedLayerTypes() {
        for (FileGdbLayerType layerType : java.util.List.of(
                FileGdbLayerType.TABLE,
                FileGdbLayerType.POINT,
                FileGdbLayerType.MULTIPOINT,
                FileGdbLayerType.POLYLINE,
                FileGdbLayerType.POLYGON)) {
            assertTrue(layerType.isCursorReadable());
        }
        assertFalse(FileGdbLayerType.MULTIPATCH.isCursorReadable());
        assertFalse(FileGdbLayerType.UNKNOWN.isCursorReadable());
    }

    @Test
    void readsPolygonRingsAndAllDimensionCombinations() throws Exception {
        var fixture = TestFileGdbBuilder.polygons(temporaryDirectory);

        try (FileGeodatabase database = FileGeodatabase.open(fixture.directory())) {
            FileGdbPolygon xy = firstPolygon(database, fixture.layerId("PolygonXY"));
            assertFalse(xy.hasZ());
            assertFalse(xy.hasM());
            assertPolygonShape(xy);

            FileGdbPolygon xyz = firstPolygon(database, fixture.layerId("PolygonXYZ"));
            assertTrue(xyz.hasZ());
            assertFalse(xyz.hasM());
            assertEquals(1, xyz.coordinates().z(0), 1e-9);
            assertEquals(8, xyz.coordinates().z(8), 1e-9);

            FileGdbPolygon xym = firstPolygon(database, fixture.layerId("PolygonXYM"));
            assertFalse(xym.hasZ());
            assertTrue(xym.hasM());
            assertTrue(Double.isNaN(xym.coordinates().m(0)));
            assertTrue(Double.isNaN(xym.coordinates().m(9)));

            FileGdbPolygon xyzm = firstPolygon(database, fixture.layerId("PolygonXYZM"));
            assertTrue(xyzm.hasZ());
            assertTrue(xyzm.hasM());
            assertEquals(7, xyzm.coordinates().z(7), 1e-9);
            assertEquals(70, xyzm.coordinates().m(7), 1e-9);
        }
    }

    @Test
    void readsMultiPointsInSourceOrderWithAllDimensionCombinations() throws Exception {
        var fixture = TestFileGdbBuilder.multiPointsAndPolylines(temporaryDirectory);

        try (FileGeodatabase database = FileGeodatabase.open(fixture.directory())) {
            assertEquals(
                    FileGdbLayerType.MULTIPOINT,
                    database.schema(fixture.layerId("MultiPointXY")).layerType());
            FileGdbMultiPoint xy = firstMultiPoint(database, fixture.layerId("MultiPointXY"));
            assertFalse(xy.hasZ());
            assertFalse(xy.hasM());
            assertEquals(4, xy.coordinates().size());
            assertEquals(1, xy.coordinates().x(0), 1e-9);
            assertEquals(2, xy.coordinates().y(0), 1e-9);
            assertEquals(-2, xy.coordinates().x(2), 1e-9);
            assertEquals(4, xy.coordinates().y(2), 1e-9);
            assertEquals(-2, xy.envelope().xMin(), 1e-9);
            assertEquals(9, xy.envelope().xMax(), 1e-9);
            assertEquals(-3, xy.envelope().yMin(), 1e-9);
            assertEquals(4, xy.envelope().yMax(), 1e-9);

            FileGdbMultiPoint xyz = firstMultiPoint(database, fixture.layerId("MultiPointXYZ"));
            assertTrue(xyz.hasZ());
            assertFalse(xyz.hasM());
            assertEquals(40, xyz.coordinates().z(3), 1e-9);

            FileGdbMultiPoint xym = firstMultiPoint(database, fixture.layerId("MultiPointXYM"));
            assertFalse(xym.hasZ());
            assertTrue(xym.hasM());
            assertEquals(200, xym.coordinates().m(1), 1e-9);

            try (FileGdbFeatureCursor cursor = database.openCursor(
                    fixture.layerId("MultiPointXYZM"), FileGdbReadOptions.limit(3))) {
                var complete = cursor.next();
                FileGdbMultiPoint xyzm = assertInstanceOf(FileGdbMultiPoint.class, complete.geometry());
                assertTrue(xyzm.hasZ());
                assertTrue(xyzm.hasM());
                assertEquals(30, xyzm.coordinates().z(2), 1e-9);
                assertEquals(100, xyzm.coordinates().m(0), 1e-9);
                assertTrue(Double.isNaN(xyzm.coordinates().m(1)));
                assertEquals(400, xyzm.coordinates().m(3), 1e-9);
                assertEquals("MultiPointXYZM feature", complete.attribute("Label"));

                var empty = cursor.next();
                assertNull(empty.geometry());
                assertEquals("MultiPointXYZM empty", empty.attribute("Label"));

                FileGdbMultiPoint missingM = assertInstanceOf(FileGdbMultiPoint.class, cursor.next().geometry());
                for (double value : missingM.coordinates().mValues()) {
                    assertTrue(Double.isNaN(value));
                }
            }

            assertEquals(xy, new FileGdbMultiPoint(xy.envelope(), xy.coordinates()));
            assertEquals(xy.hashCode(), new FileGdbMultiPoint(xy.envelope(), xy.coordinates()).hashCode());
        }
    }

    @Test
    void readsPolylinePathsWithAllDimensionCombinations() throws Exception {
        var fixture = TestFileGdbBuilder.multiPointsAndPolylines(temporaryDirectory);

        try (FileGeodatabase database = FileGeodatabase.open(fixture.directory())) {
            assertEquals(
                    FileGdbLayerType.POLYLINE,
                    database.schema(fixture.layerId("PolylineXY")).layerType());
            FileGdbPolyline xy = firstPolyline(database, fixture.layerId("PolylineXY"));
            assertFalse(xy.hasZ());
            assertFalse(xy.hasM());
            assertEquals(2, xy.pathCount());
            assertEquals(3, xy.pathPointCount(0));
            assertEquals(2, xy.pathPointCount(1));
            assertEquals(5, xy.coordinates().size());
            assertEquals(0, xy.coordinates().x(0), 1e-9);
            assertEquals(4, xy.coordinates().x(2), 1e-9);
            assertEquals(10, xy.coordinates().x(3), 1e-9);
            assertEquals(7, xy.coordinates().y(4), 1e-9);
            assertEquals(0, xy.envelope().xMin(), 1e-9);
            assertEquals(12, xy.envelope().xMax(), 1e-9);

            FileGdbPolyline xyz = firstPolyline(database, fixture.layerId("PolylineXYZ"));
            assertTrue(xyz.hasZ());
            assertFalse(xyz.hasM());
            assertEquals(5, xyz.coordinates().z(4), 1e-9);

            FileGdbPolyline xym = firstPolyline(database, fixture.layerId("PolylineXYM"));
            assertFalse(xym.hasZ());
            assertTrue(xym.hasM());
            assertEquals(40, xym.coordinates().m(3), 1e-9);

            try (FileGdbFeatureCursor cursor = database.openCursor(
                    fixture.layerId("PolylineXYZM"), FileGdbReadOptions.limit(3))) {
                var complete = cursor.next();
                FileGdbPolyline xyzm = assertInstanceOf(FileGdbPolyline.class, complete.geometry());
                assertTrue(xyzm.hasZ());
                assertTrue(xyzm.hasM());
                assertEquals(4, xyzm.coordinates().z(3), 1e-9);
                assertEquals(10, xyzm.coordinates().m(0), 1e-9);
                assertTrue(Double.isNaN(xyzm.coordinates().m(1)));
                assertEquals(50, xyzm.coordinates().m(4), 1e-9);
                assertEquals("PolylineXYZM feature", complete.attribute("Label"));

                var empty = cursor.next();
                assertNull(empty.geometry());
                assertEquals("PolylineXYZM empty", empty.attribute("Label"));

                FileGdbPolyline missingM = assertInstanceOf(FileGdbPolyline.class, cursor.next().geometry());
                for (double value : missingM.coordinates().mValues()) {
                    assertTrue(Double.isNaN(value));
                }
            }

            int[] paths = {3, 2};
            FileGdbPolyline equal = new FileGdbPolyline(xy.envelope(), paths, xy.coordinates());
            paths[0] = 99;
            assertEquals(3, equal.pathPointCount(0));
            int[] returnedPaths = equal.pathPointCounts();
            returnedPaths[0] = 99;
            assertEquals(3, equal.pathPointCount(0));
            assertEquals(xy, equal);
            assertEquals(xy.hashCode(), equal.hashCode());
        }
    }

    private static void assertPolygonShape(FileGdbPolygon polygon) {
        assertEquals(2, polygon.ringCount());
        assertEquals(5, polygon.ringPointCount(0));
        assertEquals(5, polygon.ringPointCount(1));
        assertEquals(10, polygon.coordinates().size());
        assertEquals(0, polygon.coordinates().x(0), 1e-9);
        assertEquals(10, polygon.coordinates().x(2), 1e-9);
        assertEquals(10, polygon.coordinates().y(2), 1e-9);
        assertEquals(2, polygon.coordinates().x(9), 1e-9);
        assertEquals(2, polygon.coordinates().y(9), 1e-9);
        assertEquals(0, polygon.envelope().xMin(), 1e-9);
        assertEquals(10, polygon.envelope().xMax(), 1e-9);
        double[] xCopy = polygon.coordinates().xValues();
        xCopy[0] = 999;
        assertEquals(0, polygon.coordinates().x(0), 1e-9);
        int[] ringCopy = polygon.ringPointCounts();
        ringCopy[0] = 999;
        assertEquals(5, polygon.ringPointCount(0));
    }

    private static FileGdbPolygon firstPolygon(FileGeodatabase database, String layerId) {
        try (FileGdbFeatureCursor cursor = database.openCursor(layerId, FileGdbReadOptions.limit(1))) {
            assertTrue(cursor.hasNext());
            return assertInstanceOf(FileGdbPolygon.class, cursor.next().geometry());
        }
    }

    private static FileGdbMultiPoint firstMultiPoint(FileGeodatabase database, String layerId) {
        try (FileGdbFeatureCursor cursor = database.openCursor(layerId, FileGdbReadOptions.limit(1))) {
            assertTrue(cursor.hasNext());
            return assertInstanceOf(FileGdbMultiPoint.class, cursor.next().geometry());
        }
    }

    private static FileGdbPolyline firstPolyline(FileGeodatabase database, String layerId) {
        try (FileGdbFeatureCursor cursor = database.openCursor(layerId, FileGdbReadOptions.limit(1))) {
            assertTrue(cursor.hasNext());
            return assertInstanceOf(FileGdbPolyline.class, cursor.next().geometry());
        }
    }
}
