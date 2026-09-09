package cn.superhuang.datascalpel.taskengine.canvas;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;
import static org.junit.jupiter.api.Assertions.*;

class NearestGeometrySupportTest {
    private final WKTReader reader = new WKTReader();

    @Test void closestPositionsAreNotCentroidsAndCoincidentPointsRetainZeroLengthLines() throws Exception {
        var a = reader.read("POINT (0 20)");
        var b = reader.read("LINESTRING (2 -100, 2 100)");
        var line = NearestGeometrySupport.connection(a, b, false, Double.NaN);
        assertEquals(2, line.getLength()); assertEquals(20, line.getCoordinates()[1].y);
        var zero = NearestGeometrySupport.connection(a, a, true, 1000);
        assertEquals("MultiLineString", zero.getGeometryType()); assertEquals(2, zero.getNumPoints());
        assertEquals(0, zero.getLength()); assertEquals(4326, zero.getSRID());
    }

    @Test void nullEmptyInvalidAndNonpointInputsHaveExplicitSemantics() throws Exception {
        assertNull(NearestGeometrySupport.checked(null, true));
        assertNull(NearestGeometrySupport.checked(reader.read("POINT EMPTY"), true));
        assertEquals("GEODESIC_NEAREST_REQUIRES_POINTS", assertThrows(IllegalArgumentException.class,
                () -> NearestGeometrySupport.checked(reader.read("LINESTRING (0 0, 1 1)"), true)).getMessage());
        for (Geometry geometry : new Geometry[]{reader.read("POLYGON ((0 0, 2 2, 0 2, 2 0, 0 0))"),
                new GeometryFactory().createPoint(new CoordinateXY(Double.POSITIVE_INFINITY, 0))}) {
            assertEquals("SPATIAL_NEAREST_GEOMETRY_INVALID", assertThrows(IllegalArgumentException.class,
                    () -> NearestGeometrySupport.checked(geometry, false)).getMessage());
        }
        assertEquals("SPATIAL_NEAREST_GEOMETRY_INVALID", assertThrows(IllegalArgumentException.class,
                () -> NearestGeometrySupport.checked(reader.read("POINT (181 0)"), true)).getMessage());
    }

    @Test void tinyGeodesicStepsFailSafelyBeforeAllocatingUnboundedLines() throws Exception {
        var a = reader.read("POINT (0 0)"); var b = reader.read("POINT (100 0)");
        assertEquals("SPATIAL_NEAREST_CONNECTION_VERTEX_LIMIT_EXCEEDED", assertThrows(IllegalArgumentException.class,
                () -> NearestGeometrySupport.connection(a, b, true, 0.001)).getMessage());
    }
}
