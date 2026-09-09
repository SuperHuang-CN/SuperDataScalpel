package cn.superhuang.datascalpel.taskengine.canvas;

import net.sf.geographiclib.Geodesic;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;
import static org.junit.jupiter.api.Assertions.*;

class TrackGeodesicPathTest {
    private Geometry read(String wkt) throws Exception { return new WKTReader().read(wkt); }
    private void assertSegments(Geometry result, double step) {
        assertInstanceOf(MultiLineString.class, result);
        assertEquals(4326, result.getSRID());
        for (int part = 0; part < result.getNumGeometries(); part++) {
            var points = result.getGeometryN(part).getCoordinates();
            assertTrue(points.length >= 2);
            for (int i = 0; i < points.length; i++) {
                assertTrue(Double.isFinite(points[i].x) && Double.isFinite(points[i].y));
                assertTrue(Math.abs(points[i].x) <= 180 && Math.abs(points[i].y) <= 90, points[i].toString());
                assertTrue(Double.isNaN(points[i].getZ()));
                if (i > 0) {
                    assertTrue(Math.abs(points[i].x - points[i - 1].x) <= 180 + 1e-8);
                    assertTrue(Geodesic.WGS84.Inverse(points[i - 1].y, points[i - 1].x, points[i].y, points[i].x).s12 <= step + 1e-5);
                }
            }
        }
    }
    @Test void samplesEquatorAndHighLatitudeWithoutChangingSource() throws Exception {
        var input = read("LINESTRING (-45 60, 45 60)");
        var copy = input.copy();
        var result = TrackGeodesicPath.build(input, 100_000);
        assertSegments(result, 100_000);
        assertTrue(result.getEnvelopeInternal().getMaxY() > 67);
        assertTrue(input.equalsExact(copy));
        assertEquals(1, result.getNumGeometries());
        var equator = TrackGeodesicPath.build(read("LINESTRING (0 0, 2 0)"), 50_000);
        assertSegments(equator, 50_000);
        assertEquals(6, equator.getNumPoints());
    }
    @Test void cutsBothDirectionsAtTheGeodesicLatitude() throws Exception {
        for (String wkt : new String[]{"LINESTRING (170 60, -170 60)", "LINESTRING (-170 60, 170 60)"}) {
            var result = TrackGeodesicPath.build(read(wkt), 2_000_000);
            assertSegments(result, 2_000_000);
            assertEquals(2, result.getNumGeometries());
            var end = result.getGeometryN(0).getCoordinates()[1];
            var start = result.getGeometryN(1).getCoordinate();
            assertEquals(180, Math.abs(end.x));
            assertEquals(-end.x, start.x);
            assertEquals(end.y, start.y, 1e-9);
            assertTrue(end.y > 60.3, "cut must follow the ellipsoidal arc, not linear latitude");
        }
    }
    @Test void exactDatelineVerticesAndReversalsDoNotCreateGlobalChords() throws Exception {
        for (String wkt : new String[]{"LINESTRING (180 0, 179 0)", "LINESTRING (180 0, -179 0)",
                "LINESTRING (179 0, 180 0, -179 0)", "LINESTRING (-179 0, -180 0, 179 0)",
                "LINESTRING (179 0, -179 1, 179 2, -179 3)", "LINESTRING (179 0, 180 0, 179 1)"}) {
            assertSegments(TrackGeodesicPath.build(read(wkt), 50_000), 50_000);
        }
    }
    @Test void polarAndAntipodalPathsRemainFinite() throws Exception {
        for (String wkt : new String[]{"LINESTRING (0 89, 180 89)", "LINESTRING (0 0, 180 0)",
                "LINESTRING (0 90, 45 80)", "LINESTRING (-90 -89, 90 -89)"}) {
            assertSegments(TrackGeodesicPath.build(read(wkt), 100_000), 100_000);
        }
    }
    @Test void poleEndpointsUseAdjacentMeridiansRegardlessOfTheirStoredLongitude() throws Exception {
        for (String wkt : new String[]{"LINESTRING (40 85, 0 90)", "LINESTRING (0 90, -40 85)",
                "LINESTRING (130 -85, 0 -90)", "LINESTRING (-170 -90, 130 -85)"}) {
            Geometry input = read(wkt); Geometry original = input.copy();
            Geometry result = TrackGeodesicPath.build(input,25_000); assertSegments(result,25_000);
            for (int part = 0; part < result.getNumGeometries(); part++) {
                Coordinate[] vertices = result.getGeometryN(part).getCoordinates();
                for (int i = 1; i < vertices.length; i++)
                    assertEquals(vertices[i - 1].x,vertices[i].x,1e-10);
            }
            assertTrue(input.equalsExact(original));
        }
    }

    @Test void retainsZeroLengthObservationsAndEmptyGeometry() throws Exception {
        var result = TrackGeodesicPath.build(read("LINESTRING (180 10, -180 10)"), 1000);
        assertSegments(result, 1000);
        assertEquals(2, result.getNumPoints());
        assertEquals(0, result.getLength());
        assertTrue(TrackGeodesicPath.build(read("LINESTRING EMPTY"), 1000).isEmpty());
        assertNull(TrackGeodesicPath.build(null, 1000));
    }
    @Test void failsSafelyForInvalidCoordinatesAndUnboundedDensification() throws Exception {
        for (String wkt : new String[]{"LINESTRING (181 0, 0 0)", "LINESTRING (0 91, 0 0)", "POINT (0 0)"}) {
            var input = read(wkt);
            assertEquals("TRACK_GEODESIC_COORDINATE_INVALID", assertThrows(IllegalArgumentException.class,
                    () -> TrackGeodesicPath.build(input, 1000)).getMessage());
        }
        var input = read("LINESTRING (0 0, 1 0)");
        assertEquals("TRACK_GEODESIC_VERTEX_LIMIT_EXCEEDED", assertThrows(IllegalArgumentException.class,
                () -> TrackGeodesicPath.build(input, 0.001)).getMessage());
        for (double length : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertEquals("INVALID_TRACK_GEODESIC_SEGMENT_LENGTH", assertThrows(IllegalArgumentException.class,
                    () -> TrackGeodesicPath.build(input, length)).getMessage());
    }
}
