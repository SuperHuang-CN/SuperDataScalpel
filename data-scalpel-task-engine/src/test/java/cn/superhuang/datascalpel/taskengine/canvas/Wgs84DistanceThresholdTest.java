package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Arc;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Budget;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Position;
import net.sf.geographiclib.Geodesic;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Wgs84DistanceThresholdTest {
    private static final GeometryFactory FACTORY=new GeometryFactory(new PrecisionModel(),4326);
    private static Geometry read(String wkt) throws Exception { return new WKTReader(FACTORY).read(wkt); }
    private static Position p(double longitude,double latitude) { return new Position(longitude,latitude); }
    private static double distance(Position a,Position b) {
        return Geodesic.WGS84.Inverse(a.latitude(),a.longitude(),b.latitude(),b.longitude()).s12;
    }
    private static boolean within(Arc first,Arc second,double threshold) {
        return Wgs84SegmentDistance.withinArcs(List.of(first),List.of(second),threshold,new Budget(Wgs84SegmentDistance.MAX_EVALUATIONS));
    }

    @Test void refinesAcrossAThresholdInsteadOfComparingTheFirstApproximateDistance() {
        var point=p(0.007,0.01); var a=p(-0.02,0); var b=p(0.06,0);
        double exact=distance(point,p(point.longitude(),0));
        var coarse=Wgs84SegmentDistance.nearest(point,point,a,b,100);
        assertTrue(coarse.distanceMetres()>exact+0.001,"a coarse attained distance would make the wrong cutoff decision");
        assertTrue(within(new Arc(point,point),new Arc(a,b),exact+0.001));
        assertFalse(within(new Arc(point,point),new Arc(a,b),exact-0.001));
        assertTrue(within(new Arc(b,a),new Arc(point,point),exact+0.001));
        assertFalse(within(new Arc(b,a),new Arc(point,point),exact-0.001));
    }

    @Test void nearEqualityWithinRoundoffRemainsUnresolvedNotAnImplicitEpsilonMatch() {
        var a=p(0,0); var b=p(0.01,0.01); double exact=distance(a,b);
        Arc first=new Arc(a,a), second=new Arc(b,b);
        assertFalse(within(first,second,exact-0.00001));
        assertTrue(within(first,second,exact+0.00001));
        for (double threshold : new double[]{Math.nextDown(exact),exact,Math.nextUp(exact)})
            failure("GEODESIC_DISTANCE_PRECISION_NOT_REACHED",()->within(first,second,threshold));
    }

    @Test void originalPositionsAndContinuousCrossingsProveZeroButNearbyPositionsAreNotSnapped() {
        assertTrue(within(new Arc(p(179,0),p(180,0)),new Arc(p(-180,0),p(-179,1)),0));
        assertTrue(within(new Arc(p(17,90),p(17,80)),new Arc(p(-80,90),p(-80,80)),0));
        assertFalse(within(new Arc(p(0,0),p(0,0)),new Arc(p(0.00000001,0),p(0.00000001,0)),0));
        assertTrue(Wgs84SegmentDistance.withinArcs(List.of(new Arc(p(-0.02,0),p(0.06,0))),
                List.of(new Arc(p(0.007,-0.03),p(0.007,0.02))),0,new Budget(100)),"the continuous crossing now supplies topological zero evidence");
    }

    @Test void datelineHighLatitudeAndOpenMultipartEdgesUseTrueGeometry() throws Exception {
        var line=Geodesic.WGS84.InverseLine(60,-45,60,45); var middle=line.Position(line.Distance()/2);
        assertTrue(within(new Arc(p(middle.lon2,middle.lat2),p(middle.lon2,middle.lat2)),new Arc(p(-45,60),p(45,60)),0.001));
        assertFalse(within(new Arc(p(0,60),p(0,60)),new Arc(p(-45,60),p(45,60)),1000));
        assertTrue(Wgs84GeometryDistance.withinDistance(read("POINT (180 0)"),read("LINESTRING (179.9 0,-179.9 0)"),0.001));
        assertFalse(Wgs84GeometryDistance.withinDistance(read("POINT (0 0)"),
                read("MULTILINESTRING ((-2 -1,-2 1),(2 -1,2 1))"),1000));
    }

    @Test void containmentAndHolesAreConsideredBeforeBoundaryThresholds() throws Exception {
        Geometry shell=read("POLYGON ((0 0,0.01 0,0.01 0.01,0 0.01,0 0))");
        Geometry holed=read("POLYGON ((0 0,0.01 0,0.01 0.01,0 0.01,0 0),(0.004 0.004,0.006 0.004,0.006 0.006,0.004 0.006,0.004 0.004))");
        Geometry point=read("POINT (0.005 0.005)");
        assertTrue(Wgs84GeometryDistance.withinDistance(point,shell,0));
        assertFalse(Wgs84GeometryDistance.withinDistance(point,holed,100));
        assertTrue(Wgs84GeometryDistance.withinDistance(point,holed,120));
        assertTrue(Wgs84GeometryDistance.withinDistance(shell,read("LINESTRING (-1 -1,0.005 0.005)"),0));
    }

    @Test void unresolvedContainmentCannotTurnPositiveBoundaryDistanceIntoSeparation() throws Exception {
        var shape=read("POLYGON ((0 0,80 0,80 5,0 5,0 0))");
        var region=Wgs84PolygonRegion.prepare(shape);
        var outside=FACTORY.createPoint(new CoordinateXY(40,-5e-11));
        assertEquals(Wgs84PolygonRegion.Location.OUTSIDE,region.locate(p(40,-5e-11)));
        assertFalse(Wgs84GeometryDistance.withinDistance(outside,shape,0));
        // At this longitude the two-ray deviation is approximately 2*latitude*cot(40°).
        // 5e-11 degrees is outside the angular guard; 3e-11 degrees is inside it.
        var point=FACTORY.createPoint(new CoordinateXY(40,-3e-11));
        assertEquals(Wgs84PolygonRegion.Location.UNRESOLVED,region.locate(p(40,-3e-11)));
        assertFalse(Wgs84SegmentDistance.withinArcs(Wgs84LinearDistance.edges(point),Wgs84LinearDistance.edges(shape.getBoundary()),
                0,new Budget(Wgs84SegmentDistance.MAX_EVALUATIONS)),"the boundaries alone are provably separated");
        assertTrue(Wgs84GeometryDistance.withinDistance(point,shape,0.001),"a real close boundary witness is sufficient");
        failure("GEODESIC_DISTANCE_PRECISION_NOT_REACHED",()->Wgs84GeometryDistance.withinDistance(point,shape,0));
    }

    @Test void fullValidationNullAndSharedBudgetRulesArePreserved() throws Exception {
        var valid=read("POINT (0 0)");
        assertNull(Wgs84GeometryDistance.withinDistance(null,valid,100));
        assertNull(Wgs84GeometryDistance.withinDistance(read("POINT EMPTY"),valid,100));
        for (double threshold : new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY})
            failure("INVALID_GEODESIC_DISTANCE_THRESHOLD",()->Wgs84GeometryDistance.withinDistance(valid,valid,threshold));
        failure("GEODESIC_DISTANCE_COORDINATE_INVALID",()->Wgs84GeometryDistance.withinDistance(valid,read("LINESTRING (0 0,181 0)"),100));
        failure("GEODESIC_DISTANCE_WORK_LIMIT_EXCEEDED",()->Wgs84SegmentDistance.withinArcs(
                List.of(new Arc(p(0.007,0.01),p(0.007,0.01))),List.of(new Arc(p(-0.02,0),p(0.06,0))),1105,new Budget(3)));
        failure("INVALID_GEODESIC_DISTANCE_WORK_LIMIT",()->Wgs84SegmentDistance.withinArcs(
                List.of(new Arc(p(0,0),p(0,0))),List.of(new Arc(p(0,0),p(0,0))),0,null));
    }

    private static void failure(String code,org.junit.jupiter.api.function.Executable action) {
        var error=assertThrows(IllegalArgumentException.class,action); assertEquals(code,error.getMessage()); assertNull(error.getCause());
    }
}
