package cn.superhuang.data.scalpel.filegdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.superhuang.data.scalpel.filegdb.model.FileGdbLayer;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbLayerType;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPoint;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPolygon;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPolyline;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Optional exact checks for external Apache-2.0 reference fixtures that are not copied here. */
class ReferenceFileGdbCompatibilityTest {
    @Test
    void matchesReferenceTestFixtureAndItsPublishedRandomCount() {
        String fixture = System.getProperty("filegdb.referenceTestFixture");
        Assumptions.assumeTrue(fixture != null && !fixture.isBlank());

        try (FileGeodatabase database = FileGeodatabase.open(Path.of(fixture))) {
            assertEquals(List.of("test", "Random"), database.layers().stream().map(FileGdbLayer::name).toList());
            assertEquals(FileGdbLayerType.POINT, database.layers().get(0).type());
            assertEquals(FileGdbLayerType.POINT, database.layers().get(1).type());

            try (FileGdbFeatureCursor cursor = database.openCursor("a00000009", FileGdbReadOptions.limit(1))) {
                var feature = cursor.next();
                assertEquals("Foo", feature.attribute("Classname"));
                assertEquals(1, feature.attribute("Classvalue"));
                FileGdbPoint point = (FileGdbPoint) feature.geometry();
                assertEquals(-118.26767451399996, point.x(), 1e-12);
                assertEquals(37.19043101200003, point.y(), 1e-12);
            }

            int count = 0;
            try (FileGdbFeatureCursor cursor = database.openCursor("a0000000a", FileGdbReadOptions.limit(5_000))) {
                while (cursor.hasNext()) {
                    cursor.next();
                    count++;
                }
            }
            assertEquals(4_710, count, "matches FileGDB-master GDBSuite published expectation");
        }
    }

    @Test
    void matchesReferenceMzCatalogAndReadsPolylineMAndPolygonZ() {
        String fixture = System.getProperty("filegdb.referenceMzFixture");
        Assumptions.assumeTrue(fixture != null && !fixture.isBlank());

        try (FileGeodatabase database = FileGeodatabase.open(Path.of(fixture))) {
            assertEquals(
                    List.of("PolylineM", "PolygonZ"),
                    database.layers().stream().map(FileGdbLayer::name).toList());
            assertEquals("a00000015", database.layers().get(0).id());
            assertEquals(FileGdbLayerType.POLYLINE, database.layers().get(0).type());
            assertEquals("a00000016", database.layers().get(1).id());
            assertEquals(FileGdbLayerType.POLYGON, database.layers().get(1).type());
            assertTrue(database.schema("a00000016").spatialReference().hasZ());

            try (FileGdbFeatureCursor cursor = database.openCursor("a00000015", FileGdbReadOptions.limit(10))) {
                var first = cursor.next();
                assertEquals(1, first.oid());
                FileGdbPolyline firstLine = assertInstanceOf(FileGdbPolyline.class, first.geometry());
                assertEquals(1, firstLine.pathCount());
                assertEquals(3, firstLine.pathPointCount(0));
                assertEquals(-118.610274615, firstLine.coordinates().x(0), 1e-9);
                assertEquals(40.121830087, firstLine.coordinates().y(0), 1e-9);
                assertEquals(100, firstLine.coordinates().m(0), 1e-9);
                assertEquals(-76.718719919, firstLine.coordinates().x(2), 1e-9);
                assertEquals(38.870409023, firstLine.coordinates().y(2), 1e-9);
                assertEquals(300, firstLine.coordinates().m(2), 1e-9);

                var second = cursor.next();
                assertEquals(2, second.oid());
                FileGdbPolyline secondLine = assertInstanceOf(FileGdbPolyline.class, second.geometry());
                assertEquals(4, secondLine.coordinates().size());
                for (double value : secondLine.coordinates().mValues()) {
                    assertTrue(Double.isNaN(value));
                }

                var third = cursor.next();
                assertEquals(3, third.oid());
                FileGdbPolyline thirdLine = assertInstanceOf(FileGdbPolyline.class, third.geometry());
                assertEquals(100, thirdLine.coordinates().m(0), 1e-9);
                assertTrue(Double.isNaN(thirdLine.coordinates().m(1)));
                assertTrue(Double.isNaN(thirdLine.coordinates().m(2)));
                assertEquals(400, thirdLine.coordinates().m(3), 1e-9);
                assertFalse(cursor.hasNext());
            }

            int count = 0;
            try (FileGdbFeatureCursor cursor = database.openCursor("a00000016", FileGdbReadOptions.limit(10))) {
                while (cursor.hasNext()) {
                    var feature = cursor.next();
                    assertTrue(feature.geometry() instanceof FileGdbPolygon);
                    count++;
                }
            }
            assertEquals(2, count);
        }
    }

    @Test
    void matchesReferenceMiamiTablesTimestampsAndDeletedSlots() {
        String fixture = System.getProperty("filegdb.referenceMiamiFixture");
        Assumptions.assumeTrue(fixture != null && !fixture.isBlank());

        try (FileGeodatabase database = FileGeodatabase.open(Path.of(fixture))) {
            assertEquals(
                    List.of("MiamiExtent", "Voyage", "Broadcast", "Vessel", "BaseStations", "AttributeUnits", "Extent"),
                    database.layers().stream().map(FileGdbLayer::name).toList());
            try (FileGdbFeatureCursor voyage = database.openCursor("a0000000a", FileGdbReadOptions.limit(1))) {
                var first = voyage.next();
                assertEquals(Instant.parse("2008-12-31T09:00:00Z"), first.attribute("ETA"));
                assertEquals(Instant.parse("2008-12-31T23:59:58Z"), first.attribute("StartTime"));
            }
            try (FileGdbFeatureCursor broadcast = database.openCursor("a0000000b", FileGdbReadOptions.limit(1))) {
                var first = broadcast.next();
                assertEquals(11, first.oid());
                assertEquals(Instant.parse("2008-12-31T23:59:00Z"), first.attribute("BaseDateTime"));
                FileGdbPoint point = (FileGdbPoint) first.geometry();
                assertEquals(-80.161767, point.x(), 1e-12);
                assertEquals(25.773433, point.y(), 1e-12);
            }
        }
    }

    @Test
    void identifiesReferenceWorldCompressedTable() {
        String fixture = System.getProperty("filegdb.referenceCompressedFixture");
        Assumptions.assumeTrue(fixture != null && !fixture.isBlank());

        FileGdbException exception = org.junit.jupiter.api.Assertions.assertThrows(
                FileGdbException.class,
                () -> FileGeodatabase.open(Path.of(fixture)));
        assertEquals(FileGdbErrorCode.UNSUPPORTED_FORMAT, exception.code());
        assertTrue(exception.getMessage().contains("table compression"));
    }
}
