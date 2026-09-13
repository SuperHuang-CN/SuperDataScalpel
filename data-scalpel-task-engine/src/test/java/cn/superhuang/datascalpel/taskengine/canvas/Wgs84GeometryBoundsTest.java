package cn.superhuang.datascalpel.taskengine.canvas;

import net.sf.geographiclib.Constants;
import net.sf.geographiclib.Geodesic;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.WKTReader;

import static org.junit.jupiter.api.Assertions.*;

class Wgs84GeometryBoundsTest {
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final WKTReader READER = new WKTReader(FACTORY);

    @Test void boundsContainWholeHighLatitudeDatelineAndAntipodalArcs() throws Exception {
        for (String wkt : new String[]{
                "LINESTRING (-45 60,45 60)",
                "LINESTRING (179.9 10,-179.8 11)",
                "LINESTRING (-90 80,90 80)",
                "LINESTRING (0 0,180 0)",
                "MULTILINESTRING ((-45 60,45 60),(179.9 10,-179.8 11))"
        }) {
            Geometry geometry = READER.read(wkt);
            var bounds = Wgs84GeometryBounds.of(geometry);
            for (int part = 0; part < geometry.getNumGeometries(); part++) {
                var coordinates = geometry.getGeometryN(part).getCoordinates();
                for (int edge = 1; edge < coordinates.length; edge++) {
                    var line = Geodesic.WGS84.InverseLine(coordinates[edge - 1].y, coordinates[edge - 1].x,
                            coordinates[edge].y, coordinates[edge].x);
                    for (int sample = 0; sample <= 100; sample++) {
                        var point = line.Position(line.Distance() * sample / 100);
                        contains(bounds, ecef(point.lon2, point.lat2));
                    }
                }
            }
        }
    }

    @Test void polygonBoundsIncludeInteriorCartesianCriticalPointButRespectAHole() throws Exception {
        var shell = Wgs84GeometryBounds.of(READER.read("POLYGON ((-1 -1,1 -1,1 1,-1 1,-1 -1))"));
        double[] origin = ecef(0, 0);
        contains(shell, origin);
        assertEquals(Constants.WGS84_a, shell.xyEnvelope().getEnvelopeInternal().getMaxX(), 1e-8);

        var hole = Wgs84GeometryBounds.of(READER.read(
                "POLYGON ((-1 -1,1 -1,1 1,-1 1,-1 -1),(-0.1 -0.1,-0.1 0.1,0.1 0.1,0.1 -0.1,-0.1 -0.1))"));
        assertTrue(hole.xyEnvelope().getEnvelopeInternal().getMaxX() < Constants.WGS84_a);
        assertFalse(hole.xyEnvelope().getEnvelopeInternal().contains(origin[0], origin[1]));
    }

    @Test void pointMultipartAndEmptySemanticsAreExplicit() throws Exception {
        for (String wkt : new String[]{"POINT (12 34)", "MULTIPOINT ((12 34),(-170 -70))"}) {
            var geometry = READER.read(wkt); var bounds = Wgs84GeometryBounds.of(geometry);
            for (var coordinate : geometry.getCoordinates()) contains(bounds, ecef(coordinate.x, coordinate.y));
        }
        assertNull(Wgs84GeometryBounds.of(null));
        assertNull(Wgs84GeometryBounds.of(READER.read("POINT EMPTY")));
    }

    @Test void invalidOrUnsupportedGeometryFailsWithExistingSafeCodes() throws Exception {
        var bad = READER.read("LINESTRING (0 0,181 0)");
        assertEquals("GEODESIC_DISTANCE_COORDINATE_INVALID", assertThrows(IllegalArgumentException.class,
                () -> Wgs84GeometryBounds.of(bad)).getMessage());
        var collection = READER.read("GEOMETRYCOLLECTION (POINT (0 0),LINESTRING (0 0,1 1))");
        assertEquals("GEODESIC_DISTANCE_GEOMETRY_UNSUPPORTED", assertThrows(IllegalArgumentException.class,
                () -> Wgs84GeometryBounds.of(collection)).getMessage());
    }

    private static void contains(Wgs84GeometryBounds.Bounds bounds, double[] point) {
        var envelope = bounds.xyEnvelope().getEnvelopeInternal();
        assertTrue(envelope.contains(point[0], point[1]), () -> envelope + " does not contain XY point");
        assertTrue(point[2] >= bounds.minZ() && point[2] <= bounds.maxZ());
    }

    private static double[] ecef(double longitude, double latitude) {
        double lat = Math.toRadians(latitude), lon = Math.toRadians(longitude);
        double eccentricitySquared = Constants.WGS84_f * (2 - Constants.WGS84_f);
        double sin = Math.sin(lat), cos = Math.cos(lat);
        double normal = Constants.WGS84_a / Math.sqrt(1 - eccentricitySquared * sin * sin);
        return new double[]{normal * cos * Math.cos(lon), normal * cos * Math.sin(lon),
                normal * (1 - eccentricitySquared) * sin};
    }
}
