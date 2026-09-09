package cn.superhuang.datascalpel.taskengine.canvas;

import net.sf.geographiclib.Geodesic;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;

import static org.junit.jupiter.api.Assertions.*;

class Wgs84GeometryDistanceTest {
    private static final GeometryFactory FACTORY=new GeometryFactory(new PrecisionModel(),4326);
    private static Geometry read(String wkt) throws Exception { return new WKTReader(FACTORY).read(wkt); }
    private static Wgs84SegmentDistance.Result nearest(Geometry a,Geometry b) { return Wgs84GeometryDistance.nearest(a,b,0.1); }

    @Test void containedPointAndContainedPolygonHaveZeroRegionDistanceRatherThanPositiveBoundaryDistance() throws Exception {
        var area=read("POLYGON ((0 0,0.01 0,0.01 0.01,0 0.01,0 0))");
        var point=read("POINT (0.005 0.005)");
        var small=read("POLYGON ((0.002 0.002,0.003 0.002,0.003 0.003,0.002 0.003,0.002 0.002))");
        for (Geometry shape : java.util.List.of(point,small)) {
            var result=nearest(shape,area); assertEquals(0,result.distanceMetres()); assertEquals(result.first(),result.second());
            assertEquals(0,nearest(area,shape).distanceMetres());
        }
    }

    @Test void pointInAHoleHasPositiveDistanceToTheHoleBoundary() throws Exception {
        var area=read("POLYGON ((0 0,0.01 0,0.01 0.01,0 0.01,0 0),(0.004 0.004,0.006 0.004,0.006 0.006,0.004 0.006,0.004 0.004))");
        var result=nearest(read("POINT (0.005 0.005)"),area);
        assertEquals(Geodesic.WGS84.Inverse(0.005,0.005,0.004,0.005).s12,result.distanceMetres(),0.1);
        assertTrue(result.lowerBoundMetres()>100); assertTrue(result.uncertaintyMetres()<=0.1);
    }

    @Test void crossingPolygonInteriorsWithoutAnyContainedVertexStillHaveZeroInTheirDistanceInterval() throws Exception {
        var a=read("POLYGON ((-0.003 -0.0005,0.003 -0.0005,0.003 0.0005,-0.003 0.0005,-0.003 -0.0005))");
        var b=read("POLYGON ((-0.0005 -0.003,0.0005 -0.003,0.0005 0.003,-0.0005 0.003,-0.0005 -0.003))");
        var result=nearest(a,b);
        assertTrue(result.distanceMetres()<0.1); assertEquals(0,result.lowerBoundMetres());
    }

    @Test void disjointRegionsAndLineEndInsideRegionHaveDifferentResults() throws Exception {
        var a=read("POLYGON ((0 0,0.001 0,0.001 0.001,0 0.001,0 0))");
        var b=read("POLYGON ((0.003 0,0.004 0,0.004 0.001,0.003 0.001,0.003 0))");
        var result=nearest(a,b); assertTrue(result.distanceMetres()>220); assertTrue(result.distanceMetres()<223);
        assertTrue(result.uncertaintyMetres()<=0.1);
        var line=read("LINESTRING (-0.001 0.0005,0.0005 0.0005)");
        var inside=nearest(line,a); assertEquals(0,inside.distanceMetres());
        assertEquals(0.0005,inside.first().longitude()); assertEquals(0.0005,inside.first().latitude());
    }

    @Test void nearBoundaryOutsidePointIsNotSnappedToZeroByContainment() throws Exception {
        var area=read("POLYGON ((0 0,0.01 0,0.01 0.01,0 0.01,0 0))");
        var point=read("POINT (0.005 -0.00000001)");
        var result=Wgs84GeometryDistance.nearest(point,area,0.0001);
        assertTrue(result.distanceMetres()>0.001); assertTrue(result.lowerBoundMetres()>0);
        assertTrue(result.uncertaintyMetres()<=0.0001);
    }

    @Test void dateLineSeamIsNotARealHoleBoundaryOrAConnectionAcrossGreenwich() throws Exception {
        var area=read("POLYGON ((179.99 -0.01,-179.99 -0.01,-179.99 0.01,179.99 0.01,179.99 -0.01),(179.996 -0.004,-179.996 -0.004,-179.996 0.004,179.996 0.004,179.996 -0.004))");
        assertEquals(0,nearest(read("POINT (180 0.008)"),area).distanceMetres());
        var inHole=nearest(read("POINT (180 0)"),area); assertTrue(inHole.distanceMetres()>440); assertTrue(inHole.distanceMetres()<446);
    }

    @Test void invalidOtherGeometryIsCheckedBeforeAContainedVertexCanShortCircuit() throws Exception {
        var area=read("POLYGON ((0 0,0.01 0,0.01 0.01,0 0.01,0 0))");
        var bad=read("LINESTRING (0.005 0.005,181 0)");
        var error=assertThrows(IllegalArgumentException.class,()->nearest(area,bad));
        assertEquals("GEODESIC_DISTANCE_COORDINATE_INVALID",error.getMessage()); assertNull(error.getCause());
        assertNull(nearest(null,area)); assertNull(nearest(read("POLYGON EMPTY"),area));
    }
}
