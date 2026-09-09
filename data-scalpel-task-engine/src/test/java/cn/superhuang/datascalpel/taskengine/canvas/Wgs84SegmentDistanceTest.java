package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Position;
import net.sf.geographiclib.Geodesic;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class Wgs84SegmentDistanceTest {
    private static Position p(double longitude,double latitude) { return new Position(longitude,latitude); }
    private static double distance(Position a,Position b) {
        return Geodesic.WGS84.Inverse(a.latitude(),a.longitude(),b.latitude(),b.longitude()).s12;
    }
    private static void contains(Wgs84SegmentDistance.Result result,double expected,double tolerance) {
        assertTrue(result.lowerBoundMetres() <= expected + Wgs84SegmentDistance.ROUNDOFF_METRES, result.toString());
        assertTrue(result.distanceMetres() + Wgs84SegmentDistance.ROUNDOFF_METRES >= expected,result.toString());
        assertTrue(result.uncertaintyMetres() <= tolerance,result.toString());
        assertEquals(result.distanceMetres(),distance(result.first(),result.second()),1e-8);
    }

    @Test void pointSegmentsRetainExactWgs84DistanceAndCanonicalCoordinates() {
        for (Position a : List.of(p(0,0),p(179,70),p(80,90))) {
            var b=p(-80,-60); var result=Wgs84SegmentDistance.nearest(a,a,b,b,0.001);
            contains(result,distance(a,b),0.001);
        }
        assertEquals(0,Wgs84SegmentDistance.nearest(p(180,0),p(-180,0),p(-180,0),p(180,0),0.001).distanceMetres());
        assertEquals(0,Wgs84SegmentDistance.nearest(p(70,90),p(-70,90),p(0,90),p(120,90),0.001).distanceMetres());
    }

    @Test void pointToSegmentFindsAnInteriorPositionNotAnEndpointOrCentroid() {
        var a=p(-0.02,0); var b=p(0.06,0); var point=p(0.007,0.01);
        // This test also checks witness position, so request a tighter DISTANCE bound;
        // a one-centimetre distance bound alone allows metre-scale movement near the minimum.
        var result=Wgs84SegmentDistance.nearest(point,point,a,b,0.0001);
        contains(result,distance(point,p(point.longitude(),0)),0.0001);
        assertEquals(point.longitude(),result.second().longitude(),0.00001);
        assertEquals(0,result.second().latitude(),1e-12);
        assertTrue(result.distanceMetres()<distance(point,a)); assertTrue(result.distanceMetres()<distance(point,b));
        assertTrue(result.distanceMetres()<distance(point,p(0.02,0)));
    }

    @Test void endpointMinimumIsNotExtendedPastTheFiniteArc() {
        var point=p(-1,0); var a=p(0,0); var b=p(0.1,0);
        var result=Wgs84SegmentDistance.nearest(point,point,a,b,0.01);
        contains(result,distance(point,a),0.01); assertEquals(a,result.second());
    }

    @Test void crossedSegmentInteriorsReturnCoincidentNearestPositions() {
        var result=Wgs84SegmentDistance.nearest(p(-0.02,0),p(0.06,0),p(0.007,-0.03),p(0.007,0.02),0.01);
        contains(result,0,0.01); assertTrue(result.distanceMetres()<0.01);
        assertEquals(0.007,result.first().longitude(),0.000001);
        assertEquals(0,result.second().latitude(),0.000001);
    }

    @Test void disjointSegmentsHaveAClosestEndpointToInteriorPair() {
        var result=Wgs84SegmentDistance.nearest(p(-0.02,0),p(0.06,0),p(0.007,0.01),p(0.007,0.03),0.02);
        contains(result,distance(p(0.007,0),p(0.007,0.01)),0.02);
        assertEquals(0.007,result.first().longitude(),0.00002);
        assertEquals(0.01,result.second().latitude(),0.000001);
    }

    @Test void datelineAndHighLatitudeUseGeodesicEdgesInsteadOfLongitudeChords() {
        var point=p(180,0.01);
        contains(Wgs84SegmentDistance.nearest(point,point,p(179.98,0),p(-179.94,0),0.01),
                distance(point,p(180,0)),0.01);
        var line=Geodesic.WGS84.InverseLine(60,-45,60,45); var centre=line.Position(line.Distance()/2);
        var onArc=p(centre.lon2,centre.lat2);
        contains(Wgs84SegmentDistance.nearest(onArc,onArc,p(-45,60),p(45,60),0.001),0,0.001);
        assertTrue(centre.lat2>67);
    }

    @Test void polarAndAntipodalArcsAreDeterministicWhenEndpointsAreReversed() {
        for (var endpoints : List.of(List.of(p(-90,80),p(90,80)),List.of(p(0,0),p(180,0)))) {
            var point=p(0,90);
            var forward=Wgs84SegmentDistance.nearest(point,point,endpoints.getFirst(),endpoints.getLast(),0.01);
            var reverse=Wgs84SegmentDistance.nearest(point,point,endpoints.getLast(),endpoints.getFirst(),0.01);
            assertEquals(forward,reverse); contains(forward,0,0.01);
        }
    }

    @Test void constructedNormalFootpointsAndCrossingsHoldAcrossLatitudesAndBearings() {
        var random=new java.util.Random(284731);
        for (int i=0;i<32;i++) {
            double latitude=-75+150*random.nextDouble(), longitude=-180+360*random.nextDouble(), bearing=360*random.nextDouble();
            var foot=p(longitude,latitude);
            var from=Geodesic.WGS84.Direct(latitude,longitude,bearing+180,1000+100_000*random.nextDouble());
            var to=Geodesic.WGS84.Direct(latitude,longitude,bearing,1000+100_000*random.nextDouble());
            double offset=100+5000*random.nextDouble();
            var normal=Geodesic.WGS84.Direct(latitude,longitude,bearing+90,offset);
            var a=p(from.lon2,from.lat2); var b=p(to.lon2,to.lat2); var point=p(normal.lon2,normal.lat2);
            contains(Wgs84SegmentDistance.nearest(point,point,a,b,0.1),offset,0.1);
            var opposite=Geodesic.WGS84.Direct(latitude,longitude,bearing-90,1000+5000*random.nextDouble());
            var crossing=Wgs84SegmentDistance.nearest(a,b,p(opposite.lon2,opposite.lat2),point,0.1);
            contains(crossing,0,0.1);
            assertTrue(distance(crossing.first(),foot)<1);
        }
    }

    @Test void reversingEitherArcAndSwappingSidesPreservesTheMinimumWithinItsStatedError() {
        var a=p(179.96,60); var b=p(-179.98,60.03); var c=p(179.99,60.04); var d=p(-179.97,60.04);
        var reference=Wgs84SegmentDistance.nearest(a,b,c,d,0.1);
        for (var result : List.of(Wgs84SegmentDistance.nearest(b,a,c,d,0.1),
                Wgs84SegmentDistance.nearest(a,b,d,c,0.1),Wgs84SegmentDistance.nearest(c,d,a,b,0.1))) {
            assertEquals(reference.distanceMetres(),result.distanceMetres(),0.1);
            assertTrue(result.uncertaintyMetres()<=0.1);
        }
    }

    @Test void nearParallelAndNearlyEquidistantConfigurationsDoNotPretendToConverge() {
        // Parallel geodesic arcs can have a broad near-flat minimum. A short pair converges;
        // a deliberately tiny shared budget must fail instead of returning endpoint distance.
        var result=Wgs84SegmentDistance.nearest(p(0,0),p(0.001,0),p(0,0.001),p(0.001,0.001),0.1);
        contains(result,distance(p(0,0),p(0,0.001)),0.1);
        failure("GEODESIC_DISTANCE_WORK_LIMIT_EXCEEDED",()->Wgs84SegmentDistance.nearest(p(0,90),p(0,90),
                p(-45,0),p(45,0),0.1,new Wgs84SegmentDistance.Budget(100)));
    }

    @Test void invalidCoordinatesPrecisionAndWorkExhaustionNeverReturnAnUnverifiedApproximation() {
        for (Position bad : List.of(p(Double.NaN,0),p(181,0),p(0,91),p(Double.POSITIVE_INFINITY,0)))
            failure("GEODESIC_DISTANCE_COORDINATE_INVALID",()->Wgs84SegmentDistance.nearest(bad,p(0,0),p(1,0),p(1,1),0.01));
        for (double precision : new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,Double.MIN_VALUE})
            failure("INVALID_GEODESIC_DISTANCE_PRECISION",()->Wgs84SegmentDistance.nearest(p(0,0),p(0,1),p(1,0),p(1,1),precision));
        failure("GEODESIC_DISTANCE_WORK_LIMIT_EXCEEDED",()->Wgs84SegmentDistance.nearest(p(0.007,0.01),p(0.007,0.01),
                p(-0.02,0),p(0.06,0),0.01,new Wgs84SegmentDistance.Budget(5)));
        var budget=new Wgs84SegmentDistance.Budget(1);
        Wgs84SegmentDistance.nearest(p(0,0),p(0,0),p(1,0),p(1,0),0.01,budget);
        failure("GEODESIC_DISTANCE_WORK_LIMIT_EXCEEDED",()->Wgs84SegmentDistance.nearest(p(0,0),p(0,0),p(1,0),p(1,0),0.01,budget));
    }

    private static void failure(String code,org.junit.jupiter.api.function.Executable action) {
        var error=assertThrows(IllegalArgumentException.class,action); assertEquals(code,error.getMessage()); assertNull(error.getCause());
    }
}
