package cn.superhuang.datascalpel.taskengine.canvas;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrackGeodesicPolygonTest {
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private Geometry read(String text) throws Exception { return new WKTReader(FACTORY).read(text); }
    private Point point(double longitude, double latitude) { return FACTORY.createPoint(new CoordinateXY(longitude, latitude)); }
    private static void assertGeometry(MultiPolygon geometry) {
        assertFalse(geometry.isEmpty()); assertTrue(geometry.isValid()); assertEquals(4326, geometry.getSRID());
        for (Coordinate coordinate : geometry.getCoordinates()) {
            assertTrue(Double.isFinite(coordinate.x) && Math.abs(coordinate.x) <= 180);
            assertTrue(Double.isFinite(coordinate.y) && Math.abs(coordinate.y) <= 90);
            assertTrue(Double.isNaN(coordinate.getZ()) && Double.isNaN(coordinate.getM()));
        }
    }

    @Test void unbufferedPolygonUsesGeodesicEdgesAndPreservesItsHole() throws Exception {
        var source = read("POLYGON ((-45 60,45 60,45 65,-45 65,-45 60),(-2 70,-2 71,2 71,2 70,-2 70))");
        // The shell's lower great arc rises above 67 degrees; this hole is inside the geographic shell,
        // even though a raw longitude/latitude planar validity test would reject it.
        var footprint = TrackGeodesicAreaGeometry.footprint(source, null, false, 25_000);
        var area = footprint.area(); assertGeometry(area);
        assertTrue(area.covers(point(10, 69))); assertFalse(area.covers(point(0, 60)));
        assertFalse(area.covers(point(0, 70.5)));
        assertTrue(TrackGeodesicAreaGeometry.connect(List.of(footprint),25_000).equalsTopo(area));
    }

    @Test void positiveBufferExpandsTheExteriorAndShrinksOrClosesTheHole() throws Exception {
        var source = read("POLYGON ((0 0,0 0.1,0.1 0.1,0.1 0,0 0),(0.04 0.04,0.06 0.04,0.06 0.06,0.04 0.06,0.04 0.04))");
        var original = TrackGeodesicAreaGeometry.footprint(source,0d,true,1000).area();
        var small = TrackGeodesicAreaGeometry.footprint(source,200d,true,1000).area();
        var large = TrackGeodesicAreaGeometry.footprint(source,1500d,true,1000).area();
        assertGeometry(original); assertGeometry(small); assertGeometry(large);
        assertTrue(small.covers(original)); assertTrue(large.covers(original));
        assertFalse(small.covers(point(0.05,0.05))); assertTrue(large.covers(point(0.05,0.05)));
        assertTrue(small.covers(point(-0.001,0.05))); assertFalse(small.covers(point(-0.004,0.05)));
    }

    @Test void concavePolygonIsBufferedAlongItsBoundaryNotItsConvexHull() throws Exception {
        var source = read("POLYGON ((0 0,0.1 0,0.1 0.02,0.02 0.02,0.02 0.1,0 0.1,0 0))");
        var area = TrackGeodesicAreaGeometry.footprint(source,200d,true,1000).area(); assertGeometry(area);
        assertTrue(area.covers(point(0.05,0.01))); assertTrue(area.covers(point(0.01,0.05)));
        assertTrue(area.covers(point(0.021,0.04))); assertFalse(area.covers(point(0.05,0.05)));
    }

    @Test void aLongEdgeUsesNormalOffsetsRatherThanAnOverWideEndpointDiskHull() throws Exception {
        var source = read("POLYGON ((-45 60,45 60,45 61,-45 61,-45 60))");
        var area = TrackGeodesicAreaGeometry.footprint(source,10_000d,true,25_000).area(); assertGeometry(area);
        var line = net.sf.geographiclib.Geodesic.WGS84.InverseLine(60,-45,60,45);
        var middle = line.Position(line.Distance()/2);
        var inside = net.sf.geographiclib.Geodesic.WGS84.Direct(middle.lat2,middle.lon2,middle.azi2+90,9900);
        var outside = net.sf.geographiclib.Geodesic.WGS84.Direct(middle.lat2,middle.lon2,middle.azi2+90,10_200);
        assertTrue(area.covers(point(inside.lon2,inside.lat2)));
        assertFalse(area.covers(point(outside.lon2,outside.lat2)),"a hull of endpoint disks would bow farther than the uniform buffer");
    }

    @Test void datelineShellAndHoleAreValidatedInTheirGeographicDomain() throws Exception {
        var source = read("POLYGON ((179 -1,-179 -1,-179 1,179 1,179 -1),(179.5 -0.5,179.5 0.5,-179.5 0.5,-179.5 -0.5,179.5 -0.5))");
        for (double radius : new double[]{0,10_000}) {
            var footprint = TrackGeodesicAreaGeometry.footprint(source,radius,true,10_000);
            var area = footprint.area(); assertGeometry(area);
            assertFalse(area.covers(point(0,0))); assertFalse(area.covers(point(180,0)));
            assertTrue(area.covers(point(179.2,0))); assertTrue(area.covers(point(-179.2,0)));
            assertTrue(footprint.hullVertices().stream().noneMatch(vertex -> Math.abs(vertex.latitude())==90));
        }
    }

    @Test void multipartObservationsStayMultipartUntilTheirBuffersActuallyMeet() throws Exception {
        var source = read("MULTIPOLYGON (((0 0,0.01 0,0.01 0.01,0 0.01,0 0)),((0.03 0,0.04 0,0.04 0.01,0.03 0.01,0.03 0)))");
        var small = TrackGeodesicAreaGeometry.footprint(source,200d,true,500).area(); assertGeometry(small);
        assertEquals(2,small.getNumGeometries()); assertFalse(small.covers(point(0.02,0.005)));
        var large = TrackGeodesicAreaGeometry.footprint(source,1500d,true,500).area(); assertGeometry(large);
        assertEquals(1,large.getNumGeometries()); assertTrue(large.covers(point(0.02,0.005)));
    }

    @Test void polygonWindingDoesNotChooseTheSphericalComplement() throws Exception {
        var shape = read("POLYGON ((175 60,-175 60,-175 63,175 63,175 60))");
        var a = TrackGeodesicAreaGeometry.footprint(shape,2000d,true,10_000).area();
        var b = TrackGeodesicAreaGeometry.footprint(shape.reverse(),2000d,true,10_000).area();
        assertGeometry(a); assertGeometry(b); assertTrue(a.equalsTopo(b)); assertFalse(a.covers(point(0,60)));
    }

    @Test void orderedAssemblyKeepsUnequalPointBuffersAndOnlyAdjacentConnections() {
        var a = TrackGeodesicAreaGeometry.footprint(point(0,0),50_000d,true,25_000);
        var b = TrackGeodesicAreaGeometry.footprint(point(10,0),100_000d,true,25_000);
        var c = TrackGeodesicAreaGeometry.footprint(point(10,10),50_000d,true,25_000);
        var result = TrackGeodesicAreaGeometry.connect(List.of(a,b,c),25_000); assertGeometry(result);
        for (var footprint : List.of(a,b,c)) {
            // Union splits edges at computed double intersections. An exact covers predicate
            // observed a 2.47e-19 square-degree sliver; don't mutate/snap production geometry to
            // satisfy a bitwise topology assertion. Check both lost area and every original vertex.
            var original = footprint.area();
            assertTrue(original.difference(result).getArea() <= original.getArea()*1e-15);
            for (Coordinate coordinate : original.getCoordinates())
                assertTrue(result.distance(FACTORY.createPoint(coordinate)) <= 1e-10);
        }
        assertTrue(result.covers(point(5,0))); assertTrue(result.covers(point(10,5))); assertFalse(result.covers(point(4,6)));
    }

    @Test void separatePolygonObservationsAreConnectedWhileASingletonKeepsHoles() throws Exception {
        var left = TrackGeodesicAreaGeometry.footprint(read("POLYGON ((0 0,0.1 0,0.1 0.1,0 0.1,0 0))"),null,false,1000);
        var right = TrackGeodesicAreaGeometry.footprint(read("POLYGON ((1 0,1.1 0,1.1 0.1,1 0.1,1 0))"),null,false,1000);
        var result = TrackGeodesicAreaGeometry.connect(List.of(left,right),1000); assertGeometry(result);
        assertTrue(result.covers(point(0.5,0.05))); assertFalse(result.covers(point(0.5,0.5)));
    }

    @Test void polarShellAndPolarHoleAreNotConfusedWithDatelineClosures() throws Exception {
        for (int sign : new int[]{-1,1}) {
            var shell = FACTORY.createLinearRing(new Coordinate[]{new CoordinateXY(0,sign*87),new CoordinateXY(120,sign*87),
                    new CoordinateXY(-120,sign*87),new CoordinateXY(0,sign*87)});
            var hole = FACTORY.createLinearRing(new Coordinate[]{new CoordinateXY(0,sign*89),new CoordinateXY(120,sign*89),
                    new CoordinateXY(-120,sign*89),new CoordinateXY(0,sign*89)});
            var source = FACTORY.createPolygon(shell,new LinearRing[]{hole});
            var none = TrackGeodesicAreaGeometry.footprint(source,null,false,10_000).area(); assertGeometry(none);
            var small = TrackGeodesicAreaGeometry.footprint(source,10_000d,true,10_000).area(); assertGeometry(small);
            var large = TrackGeodesicAreaGeometry.footprint(source,200_000d,true,10_000).area(); assertGeometry(large);
            for (int longitude : new int[]{-170,-90,0,90,170}) {
                assertFalse(none.covers(point(longitude,sign*89.99)));
                assertFalse(small.covers(point(longitude,sign*89.99)));
                assertTrue(large.covers(point(longitude,sign*89.99)));
            }
            assertFalse(large.covers(point(0,0)));
        }
    }

    @Test void concavePolarVertexKeepsItsReflexInteriorInsteadOfSelectingTheMissingWedge() {
        for (int sign : new int[]{-1,1}) {
            var source = FACTORY.createPolygon(new Coordinate[]{new CoordinateXY(0,sign*80),new CoordinateXY(90,sign*80),
                    new CoordinateXY(180,sign*80),new CoordinateXY(-90,sign*80),new CoordinateXY(0,sign*90),
                    new CoordinateXY(0,sign*80)});
            for (Geometry ordered : List.of(source,source.reverse())) {
                var area = TrackGeodesicAreaGeometry.footprint(ordered,null,false,10_000).area(); assertGeometry(area);
                for (int longitude : new int[]{45,135,-135}) assertTrue(area.covers(point(longitude,sign*89)));
                assertFalse(area.covers(point(-45,sign*89))); assertFalse(area.covers(point(0,0)));
                var buffered = TrackGeodesicAreaGeometry.footprint(ordered,10_000d,true,10_000).area(); assertGeometry(buffered);
                assertTrue(buffered.covers(point(-45,sign*89.99)));
                assertFalse(buffered.covers(point(-45,sign*89)));
            }
        }
    }

    @Test void sourceAndFootprintSnapshotsRemainIndependentAndInactiveRadiusIsIgnored() throws Exception {
        var shape = read("POLYGON ((0 0,1 0,1 1,0 1,0 0))"); var original = shape.copy();
        var footprint = TrackGeodesicAreaGeometry.footprint(shape,Double.NaN,false,25_000);
        assertTrue(shape.equalsExact(original));
        var modified = footprint.area(); modified.apply((CoordinateFilter)coordinate -> coordinate.x+=500);
        assertGeometry(footprint.area()); assertThrows(UnsupportedOperationException.class,()->footprint.hullVertices().clear());
        assertNull(TrackGeodesicAreaGeometry.footprint(null,null,true,25_000));
        assertNull(TrackGeodesicAreaGeometry.footprint(read("POLYGON EMPTY"),null,true,25_000));
    }

    @Test void invalidGeographicTopologyAndDistancesFailSafelyWithoutPartialAssembly() throws Exception {
        var valid = read("POLYGON ((0 0,1 0,1 1,0 1,0 0))");
        for (Double radius : Arrays.asList(null,-1d,Double.NaN,Double.POSITIVE_INFINITY))
            assertFailure("TRACK_BUFFER_DISTANCE_INVALID",()->TrackGeodesicAreaGeometry.footprint(valid,radius,true,25_000));
        for (String text : List.of("POLYGON ((0 0,1 1,0 1,1 0,0 0))",
                "POLYGON ((0 0,1 0,1 1,0 1,0 0),(2 2,3 2,3 3,2 3,2 2))",
                "MULTIPOLYGON (((0 0,1 0,1 1,0 1,0 0)),((0.5 0.5,1.5 0.5,1.5 1.5,0.5 1.5,0.5 0.5)))",
                "MULTIPOLYGON (((0 0,1 0,1 1,0 1,0 0)),((1 0,2 0,2 1,1 1,1 0)))",
                "POLYGON ((0 0,3 0,3 3,0 3,0 0),(0.5 0.5,0.5 2,2 2,2 0.5,0.5 0.5),(1 1,1 2.5,2.5 2.5,2.5 1,1 1))")) {
            var invalid = read(text);
            assertFailure("TRACK_AREA_GEOMETRY_INVALID",()->TrackGeodesicAreaGeometry.footprint(invalid,0d,true,25_000));
        }
        assertFailure("TRACK_AREA_VERTEX_LIMIT_EXCEEDED",()->TrackGeodesicAreaGeometry.footprint(valid,1000d,true,Double.MIN_VALUE));
        var first = TrackGeodesicAreaGeometry.footprint(valid,null,false,25_000);
        assertFailure("TRACK_AREA_GEOMETRY_INVALID",()->TrackGeodesicAreaGeometry.connect(Arrays.asList(first,null),25_000));
        var safe = TrackGeodesicAreaGeometry.safe(new NullPointerException());
        assertEquals("TRACK_AREA_GEOMETRY_INVALID",safe.getMessage()); assertNull(safe.getCause());
    }

    private static void assertFailure(String code,org.junit.jupiter.api.function.Executable action) {
        var failure = assertThrows(IllegalArgumentException.class,action); assertEquals(code,failure.getMessage()); assertNull(failure.getCause());
    }
}
