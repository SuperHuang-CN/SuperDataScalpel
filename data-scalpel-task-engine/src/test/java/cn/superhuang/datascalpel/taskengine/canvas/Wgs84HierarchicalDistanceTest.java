package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Arc;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Budget;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Position;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Result;
import net.sf.geographiclib.Constants;
import net.sf.geographiclib.Geodesic;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class Wgs84HierarchicalDistanceTest {
    private static Position p(double x,double y) { return new Position(x,y); }
    private static Arc arc(double x,double y,double xx,double yy) { return new Arc(p(x,y),p(xx,yy)); }
    private static double distance(Position a,Position b) {
        return Geodesic.WGS84.Inverse(a.latitude(),a.longitude(),b.latitude(),b.longitude()).s12;
    }

    @Test void millionsOfSeparatedPairsDoNotRequireQuadraticInitialization() {
        var left=new ArrayList<Arc>(); var right=new ArrayList<Arc>();
        for (int i=0;i<2048;i++) {
            double x=-150+i*0.1;
            left.add(arc(x,-40,x+0.001,-40));
            right.add(arc(x,40,x+0.001,40));
        }
        // The only near pair is deliberately last, with a nearest position inside the arc.
        left.add(arc(-0.02,0,0.06,0)); right.add(arc(0.007,0.0001,0.007,0.0002));
        var result=Wgs84SegmentDistance.nearestArcs(left,right,0.001,new Budget(20_000));
        contains(result,distance(p(0.007,0),p(0.007,0.0001)),0.001);
        assertEquals(0,result.first().latitude(),1e-12);
        // A millimetre distance interval does not promise an exact endpoint coordinate.
        // Require a real point on the finite candidate arc, not a snapped endpoint.
        assertEquals(0.007,result.second().longitude(),1e-12);
        assertTrue(result.second().latitude()>=0.0001 && result.second().latitude()<=0.0002);
        assertTrue(Wgs84SegmentDistance.withinArcs(left,right,12,new Budget(20_000)));
        assertFalse(Wgs84SegmentDistance.withinArcs(left,right,10,new Budget(20_000)));
    }

    @Test void treeMinimumContainsTheExhaustivePairMinimumAndSurvivesPermutation() {
        var random=new Random(97531);
        var left=randomArcs(random,12); var right=randomArcs(random,9);
        double lower=Double.POSITIVE_INFINITY, upper=Double.POSITIVE_INFINITY;
        for (Arc a : left) for (Arc b : right) {
            var result=Wgs84SegmentDistance.nearest(a.start(),a.end(),b.start(),b.end(),0.01);
            lower=Math.min(lower,result.lowerBoundMetres());
            upper=Math.min(upper,result.distanceMetres()+Wgs84SegmentDistance.ROUNDOFF_METRES);
        }
        for (int iteration=0;iteration<4;iteration++) {
            Collections.shuffle(left,random); Collections.shuffle(right,random);
            var result=Wgs84SegmentDistance.nearestArcs(left,right,0.02,new Budget(250_000));
            assertTrue(result.lowerBoundMetres()<=upper);
            assertTrue(result.distanceMetres()+Wgs84SegmentDistance.ROUNDOFF_METRES>=lower);
            assertTrue(result.uncertaintyMetres()<=0.02);
            assertEquals(result.distanceMetres(),distance(result.first(),result.second()),1e-8);
            var swapped=Wgs84SegmentDistance.nearestArcs(right,left,0.02,new Budget(250_000));
            assertEquals(result.distanceMetres(),swapped.distanceMetres(),0.02);
        }
    }

    @Test void conservativeBoxesContainWholeGlobalArcsNotOnlyTheirEndpoints() {
        var arcs=new ArrayList<>(List.of(arc(-45,60,45,60),arc(179,10,-179,11),
                arc(-90,80,90,80),arc(0,0,180,0),arc(0,90,100,90),arc(-150,-89,30,-89)));
        var random=new Random(672945);
        for (int i=0;i<64;i++) arcs.add(arc(-180+360*random.nextDouble(),-90+180*random.nextDouble(),
                -180+360*random.nextDouble(),-90+180*random.nextDouble()));
        for (Arc arc : arcs) {
            var box=Wgs84SegmentDistance.arcBox(arc);
            var line=Geodesic.WGS84.InverseLine(arc.start().latitude(),arc.start().longitude(),arc.end().latitude(),arc.end().longitude());
            for (int i=0;i<=100;i++) {
                var sample=line.Position(line.Distance()*i/100);
                double[] xyz=ecef(sample.lon2,sample.lat2);
                for (int axis=0;axis<3;axis++) {
                    assertTrue(xyz[axis]>=box.min(axis),"whole arc must be inside conservative box");
                    assertTrue(xyz[axis]<=box.max(axis),"whole arc must be inside conservative box");
                }
            }
        }
    }

    @Test void datelineInteriorWitnessCannotBePrunedByGeographicEnvelope() {
        var left=List.of(arc(0,-60,1,-60),arc(179.98,0,-179.94,0));
        var right=List.of(arc(-80,40,-79,40),arc(180,0.01,180,0.02));
        var result=Wgs84SegmentDistance.nearestArcs(left,right,0.01,new Budget(250_000));
        contains(result,distance(p(180,0),p(180,0.01)),0.01);
        assertTrue(Math.abs(result.first().longitude())>179.99);
    }

    @Test void completeValidationCannotBeHiddenBehindAnEarlyZeroOrAFarBox() {
        var common=arc(0,0,1,0);
        var bad=arc(50,30,181,30);
        var failure=assertThrows(IllegalArgumentException.class,()->Wgs84SegmentDistance.nearestArcs(
                List.of(common),List.of(common,bad),0.01,new Budget(250_000)));
        assertEquals("GEODESIC_DISTANCE_COORDINATE_INVALID",failure.getMessage()); assertNull(failure.getCause());
        var threshold=assertThrows(IllegalArgumentException.class,()->Wgs84SegmentDistance.withinArcs(
                List.of(common),List.of(common,bad),100,new Budget(250_000)));
        assertEquals("GEODESIC_DISTANCE_COORDINATE_INVALID",threshold.getMessage());
    }

    @Test void hierarchyConstructionSharesTheSearchBudgetAndDoesNotMutateInputs() {
        var left=List.of(arc(-10,0,-9,0),arc(10,0,11,0));
        var right=List.of(arc(0,1,0,2),arc(0,10,0,11));
        var before=List.copyOf(left);
        var failure=assertThrows(IllegalArgumentException.class,()->Wgs84SegmentDistance.nearestArcs(left,right,0.01,new Budget(3)));
        assertEquals("GEODESIC_DISTANCE_WORK_LIMIT_EXCEEDED",failure.getMessage()); assertNull(failure.getCause());
        assertEquals(before,left);
        var result=Wgs84SegmentDistance.nearestArcs(left,right,0.1,new Budget(250_000));
        assertTrue(result.uncertaintyMetres()<=0.1); assertEquals(before,left);
    }

    private static ArrayList<Arc> randomArcs(Random random,int count) {
        var arcs=new ArrayList<Arc>();
        for (int i=0;i<count;i++) {
            double x=179+2*random.nextDouble(),y=60+random.nextDouble();
            double xx=x+0.003+0.01*random.nextDouble(),yy=y+0.003+0.01*random.nextDouble();
            arcs.add(arc(x>180 ? x-360 : x,y,xx>180 ? xx-360 : xx,yy));
        }
        return arcs;
    }
    private static void contains(Result result,double expected,double precision) {
        assertTrue(result.lowerBoundMetres()<=expected+Wgs84SegmentDistance.ROUNDOFF_METRES,result.toString());
        assertTrue(result.distanceMetres()+Wgs84SegmentDistance.ROUNDOFF_METRES>=expected,result.toString());
        assertTrue(result.uncertaintyMetres()<=precision,result.toString());
        assertEquals(result.distanceMetres(),distance(result.first(),result.second()),1e-8);
    }
    private static double[] ecef(double longitude,double latitude) {
        double lat=Math.toRadians(latitude),lon=Math.toRadians(longitude);
        double e2=Constants.WGS84_f*(2-Constants.WGS84_f),sin=Math.sin(lat),cos=Math.cos(lat);
        double n=Constants.WGS84_a/Math.sqrt(1-e2*sin*sin);
        return new double[]{n*cos*Math.cos(lon),n*cos*Math.sin(lon),n*(1-e2)*sin};
    }
}
