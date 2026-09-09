package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.Wgs84PolygonRegion.Location;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Position;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class Wgs84PolygonRegionTest {
    private static final GeometryFactory FACTORY=new GeometryFactory(new PrecisionModel(),4326);
    private static Geometry read(String wkt) throws Exception { return new WKTReader(FACTORY).read(wkt); }
    private static Position p(double longitude,double latitude) { return new Position(longitude,latitude); }

    @Test void holesConcavityAndExactBoundaryVerticesHaveDifferentLocations() throws Exception {
        var shape=read("POLYGON ((0 0,3 0,3 1,1 1,1 3,0 3,0 0),(0.2 0.2,0.8 0.2,0.8 0.8,0.2 0.8,0.2 0.2))");
        for (Geometry ordered : List.of(shape,shape.reverse())) {
            var region=Wgs84PolygonRegion.prepare(ordered);
            assertEquals(Location.INSIDE,region.locate(p(0.5,2)));
            assertEquals(Location.OUTSIDE,region.locate(p(2,2)));
            assertEquals(Location.OUTSIDE,region.locate(p(0.5,0.5)));
            assertEquals(Location.OUTSIDE,region.locate(p(-0.1,0.5)));
            assertEquals(Location.BOUNDARY,region.locate(p(0,0)));
            assertEquals(Location.BOUNDARY,region.locate(p(0.2,0.2)));
            assertEquals(Location.UNRESOLVED,region.locate(p(0.5,0)),"opposite rays are not silently snapped to boundary");
        }
    }

    @Test void datelineAndHighLatitudeUseOriginalGeodesicRingsNotRenderedSeams() throws Exception {
        var region=Wgs84PolygonRegion.prepare(read("POLYGON ((179 -1,-179 -1,-179 1,179 1,179 -1),(179.5 -0.5,-179.5 -0.5,-179.5 0.5,179.5 0.5,179.5 -0.5))"));
        assertEquals(Location.INSIDE,region.locate(p(180,0.8)));
        assertEquals(Location.OUTSIDE,region.locate(p(180,0)));
        assertEquals(Location.OUTSIDE,region.locate(p(0,0.8)),"antipodal winding must not select the complement");
        var high=Wgs84PolygonRegion.prepare(read("POLYGON ((-45 60,45 60,45 65,-45 65,-45 60))"));
        assertEquals(Location.INSIDE,high.locate(p(0,69)));
        assertEquals(Location.OUTSIDE,high.locate(p(0,60)));
        assertEquals(Location.OUTSIDE,high.locate(p(180,-69)));
    }

    @Test void polarCapsAndHolesAreIndependentOfPoleLongitude() {
        for (int sign : new int[]{-1,1}) {
            var shell=FACTORY.createLinearRing(new Coordinate[]{new CoordinateXY(0,sign*87),new CoordinateXY(120,sign*87),
                    new CoordinateXY(-120,sign*87),new CoordinateXY(0,sign*87)});
            var hole=FACTORY.createLinearRing(new Coordinate[]{new CoordinateXY(0,sign*89),new CoordinateXY(120,sign*89),
                    new CoordinateXY(-120,sign*89),new CoordinateXY(0,sign*89)});
            var cap=Wgs84PolygonRegion.prepare(FACTORY.createPolygon(shell));
            var holed=Wgs84PolygonRegion.prepare(FACTORY.createPolygon(shell,new LinearRing[]{hole}));
            for (int longitude : new int[]{-180,-90,0,90,180}) {
                assertEquals(Location.INSIDE,cap.locate(p(longitude,sign*90)));
                assertEquals(Location.OUTSIDE,holed.locate(p(longitude,sign*90)));
            }
            assertEquals(Location.INSIDE,holed.locate(p(0,sign*88)));
        }
    }

    @Test void multipartUnionDoesNotFillSpaceBetweenComponentsOrDropAnIslandInsideAHole() throws Exception {
        var region=Wgs84PolygonRegion.prepare(read("MULTIPOLYGON (((0 0,3 0,3 3,0 3,0 0),(1 1,2 1,2 2,1 2,1 1)),((1.2 1.2,1.8 1.2,1.8 1.8,1.2 1.8,1.2 1.2)))"));
        assertEquals(Location.INSIDE,region.locate(p(1.5,1.5)));
        assertEquals(Location.OUTSIDE,region.locate(p(1.1,1.5)));
        assertEquals(Location.INSIDE,region.locate(p(0.5,1.5)));
    }

    @Test void exactHoleContactVertexTakesPrecedenceOverUnresolvedShellBearing() throws Exception {
        Geometry shape=read("POLYGON ((0 0,4 0,4 4,0 4,0 0),(0 2,1 1,1 3,0 2))");
        for (Geometry ordered : List.of(shape,shape.reverse())) {
            var region=Wgs84PolygonRegion.prepare(ordered);
            assertEquals(Location.BOUNDARY,region.locate(p(0,2)));
            assertEquals(Location.OUTSIDE,region.locate(p(0.5,2)));
            assertEquals(Location.INSIDE,region.locate(p(2,2)));
            assertEquals(Location.OUTSIDE,region.locate(p(-0.00000001,2)),"near contact is not snapped");
        }
        var dateline=Wgs84PolygonRegion.prepare(read("POLYGON ((180 0,-176 0,-176 4,180 4,180 0),(-180 2,-179 1,-179 3,-180 2))"));
        assertEquals(Location.BOUNDARY,dateline.locate(p(180,2)));
        assertEquals(Location.BOUNDARY,dateline.locate(p(-180,2)));
    }

    @Test void geodesicAnnuliHaveConsistentMembershipAcrossLatitudeAndWinding() {
        var random=new java.util.Random(88271);
        for (int sample=0;sample<32;sample++) {
            double longitude=-180+360*random.nextDouble(), latitude=-85+170*random.nextDouble();
            LinearRing shell=circle(longitude,latitude,100_000), hole=circle(longitude,latitude,25_000);
            Polygon shape=FACTORY.createPolygon(shell,new LinearRing[]{hole});
            for (Geometry ordered : List.of(shape,shape.reverse())) {
                var region=Wgs84PolygonRegion.prepare(ordered);
                assertEquals(Location.OUTSIDE,region.locate(p(longitude,latitude)));
                for (int bearing : new int[]{0,90,180,270}) {
                    var middle=net.sf.geographiclib.Geodesic.WGS84.Direct(latitude,longitude,bearing,50_000);
                    var outside=net.sf.geographiclib.Geodesic.WGS84.Direct(latitude,longitude,bearing,150_000);
                    assertEquals(Location.INSIDE,region.locate(p(middle.lon2,middle.lat2)));
                    assertEquals(Location.OUTSIDE,region.locate(p(outside.lon2,outside.lat2)));
                }
            }
        }
    }

    private static LinearRing circle(double longitude,double latitude,double radius) {
        Coordinate[] points=new Coordinate[33];
        for (int i=0;i<32;i++) {
            var vertex=net.sf.geographiclib.Geodesic.WGS84.Direct(latitude,longitude,i*360d/32,radius);
            points[i]=new CoordinateXY(vertex.lon2,vertex.lat2);
        }
        points[32]=points[0].copy(); return FACTORY.createLinearRing(points);
    }

    @Test void invalidTopologyOrCoordinatesFailWithSafeDistanceCodes() throws Exception {
        var invalid=read("POLYGON ((0 0,1 1,0 1,1 0,0 0))");
        failure("GEODESIC_DISTANCE_GEOMETRY_INVALID",()->Wgs84PolygonRegion.prepare(invalid));
        var shape=read("POLYGON ((0 0,1 0,1 1,0 1,0 0))");
        var region=Wgs84PolygonRegion.prepare(shape);
        failure("GEODESIC_DISTANCE_COORDINATE_INVALID",()->region.locate(p(181,0)));
        failure("GEODESIC_DISTANCE_WORK_LIMIT_EXCEEDED",()->region.locate(p(0.5,0.5),new Wgs84SegmentDistance.Budget(1)));
    }
    private static void failure(String code,org.junit.jupiter.api.function.Executable action) {
        var error=assertThrows(IllegalArgumentException.class,action); assertEquals(code,error.getMessage()); assertNull(error.getCause());
    }
}
