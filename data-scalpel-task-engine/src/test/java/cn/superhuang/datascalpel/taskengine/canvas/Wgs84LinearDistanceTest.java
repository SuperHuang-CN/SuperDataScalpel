package cn.superhuang.datascalpel.taskengine.canvas;

import net.sf.geographiclib.Geodesic;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class Wgs84LinearDistanceTest {
    private static final GeometryFactory FACTORY=new GeometryFactory(new PrecisionModel(),4326);
    private static Geometry read(String wkt) throws Exception { return new WKTReader(FACTORY).read(wkt); }

    @Test void searchesAllRealEdgesAndNotCentroidsOrAnArtificialClosingEdge() throws Exception {
        var point=read("POINT (0.007 0.01)");
        var line=read("LINESTRING (-0.02 0,0.06 0,0.06 0.06)");
        var result=Wgs84LinearDistance.nearest(point,line,0.01);
        assertEquals(Geodesic.WGS84.Inverse(0.01,0.007,0,0.007).s12,result.distanceMetres(),0.01);
        assertTrue(result.uncertaintyMetres()<=0.01);
        var open=read("LINESTRING (0 0,0.01 0,0.01 0.01)");
        var diagonal=Wgs84LinearDistance.nearest(read("POINT (0.005 0.005)"),open,0.1);
        assertTrue(diagonal.distanceMetres()>500,"must not close the open line across this point");
    }

    @Test void multipartsAndMultipointsSelectTheClosestRealMemberWithoutConnectingParts() throws Exception {
        var lines=read("MULTILINESTRING ((0 0,0.001 0),(0.009 0,0.01 0))");
        var point=read("POINT (0.005 0)");
        var result=Wgs84LinearDistance.nearest(point,lines,0.1);
        assertEquals(Geodesic.WGS84.Inverse(0,0.005,0,0.001).s12,result.distanceMetres(),0.1);
        assertTrue(result.distanceMetres()>400);
        var points=read("MULTIPOINT ((0.005 0),(0.0095 0))");
        assertTrue(Wgs84LinearDistance.nearest(points,lines,0.01).distanceMetres()<0.01);
    }

    @Test void datelineShortArcAndSelfCrossingLinesKeepTheirTrueGeographicPath() throws Exception {
        var dateline=read("LINESTRING (179.99 0,-179.99 0)");
        assertTrue(Wgs84LinearDistance.nearest(read("POINT (180 0)"),dateline,0.001).distanceMetres()<0.001);
        assertTrue(Wgs84LinearDistance.nearest(read("POINT (0 0)"),dateline,0.1).distanceMetres()>19_000_000);
        var crossing=read("LINESTRING (-0.01 -0.01,0.01 0.01,-0.01 0.01,0.01 -0.01)");
        assertTrue(Wgs84LinearDistance.nearest(read("POINT (0 0)"),crossing,0.01).distanceMetres()<0.01);
    }

    @Test void doesNotMutateInputsOrTreatAnEmptyMemberAsAnOriginPoint() throws Exception {
        var input=read("MULTILINESTRING (EMPTY,(1 0,1 0.01))"); var original=input.copy();
        var other=read("POINT (1 0.005)");
        assertTrue(Wgs84LinearDistance.nearest(input,other,0.1).distanceMetres()<0.1);
        assertTrue(input.equalsExact(original));
        assertNull(Wgs84LinearDistance.nearest(null,other,0.1));
        assertNull(Wgs84LinearDistance.nearest(read("LINESTRING EMPTY"),other,0.1));
    }

    @Test void validatesEveryCoordinateBeforeAnEarlyIntersectionAndRejectsPolygonEdgeOnlyShortcuts() throws Exception {
        var point=read("POINT (0 0)");
        for (String wkt : List.of("LINESTRING (0 0,181 0)","LINESTRING Z (0 0 1,1 0 2)","LINESTRING M (0 0 1,1 0 2)")) {
            var invalid=read(wkt);
            failure("GEODESIC_DISTANCE_COORDINATE_INVALID",()->Wgs84LinearDistance.nearest(point,invalid,0.1));
        }
        var polygon=read("POLYGON ((-1 -1,1 -1,1 1,-1 1,-1 -1))");
        failure("GEODESIC_DISTANCE_GEOMETRY_UNSUPPORTED",()->Wgs84LinearDistance.nearest(point,polygon,0.1));
        var wrongCrs=read("POINT (0 0)"); wrongCrs.setSRID(3857);
        failure("GEODESIC_DISTANCE_COORDINATE_INVALID",()->Wgs84LinearDistance.nearest(wrongCrs,point,0.1));
    }

    private static void failure(String code,org.junit.jupiter.api.function.Executable action) {
        var error=assertThrows(IllegalArgumentException.class,action); assertEquals(code,error.getMessage()); assertNull(error.getCause());
    }
}
