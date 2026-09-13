package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.Wgs84BoundaryIntersection.Relation;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Arc;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Budget;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Position;
import net.sf.geographiclib.Geodesic;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Wgs84BoundaryIntersectionTest {
    private static final GeometryFactory FACTORY=new GeometryFactory(new PrecisionModel(),4326);
    private static Position p(double longitude,double latitude) { return new Position(longitude,latitude); }
    private static Arc arc(double x1,double y1,double x2,double y2) { return new Arc(p(x1,y1),p(x2,y2)); }
    private static Budget budget() { return new Budget(Wgs84SegmentDistance.MAX_EVALUATIONS); }
    private static Geometry read(String wkt) throws Exception { return new WKTReader(FACTORY).read(wkt); }
    private static void relation(Arc first,Arc second,Relation expected) {
        for (Arc ordered : List.of(first,new Arc(first.end(),first.start()))) {
            assertEquals(expected,Wgs84BoundaryIntersection.relate(List.of(ordered),List.of(second),budget()));
            assertEquals(expected,Wgs84BoundaryIntersection.relate(List.of(second),List.of(ordered),budget()));
        }
    }

    @Test void highLatitudeDateLineAndPoleIntersectionsAreContinuousNotLongitudeChords() {
        relation(arc(-45,60,45,60),arc(0,65,0,70),Relation.INTERSECTING);
        relation(arc(-45,60,45,60),arc(0,59,0,61),Relation.DISJOINT);
        relation(arc(179,-1,-179,1),arc(179,1,-179,-1),Relation.INTERSECTING);
        relation(arc(-90,80,90,80),arc(0,85,180,85),Relation.INTERSECTING);
        assertTrue(Wgs84SegmentDistance.withinArcs(List.of(arc(-45,60,45,60)),List.of(arc(0,65,0,70)),0,budget()));
    }

    @Test void finiteTouchesOverlapsAndEquivalentPositionsProvideZeroEvidence() {
        relation(arc(0,0,1,0),arc(0.5,0,0.5,1),Relation.INTERSECTING);
        relation(arc(0,0,1,0),arc(0.5,0,2,0),Relation.INTERSECTING);
        relation(arc(0,0,1,1),arc(1,1,0,0),Relation.INTERSECTING);
        relation(arc(0,0,1,0),arc(2,0,2,1),Relation.DISJOINT);
        relation(arc(180,0,-180,0),arc(179,0,-179,0),Relation.INTERSECTING);
        relation(arc(80,90,-80,90),arc(0,80,180,80),Relation.INTERSECTING);
    }

    @Test void nearCollinearOrUnverifiedDomainsAreNotReportedAsDisjoint() {
        var line=Geodesic.WGS84.InverseLine(0,0,1,1); var mid=line.Position(line.Distance()/2);
        Arc point=new Arc(p(mid.lon2,mid.lat2),p(mid.lon2,mid.lat2));
        relation(arc(0,0,1,1),point,Relation.UNRESOLVED);
        relation(arc(0,0,1,0),arc(0.5,1e-8,0.5,0.1),Relation.DISJOINT);
        // These selected arcs meet at the pole, but no common local domain was verified.
        // Lack of a local proof must not silently remove a real global intersection.
        relation(arc(0,0,180,0),arc(-90,80,90,80),Relation.UNRESOLVED);
    }

    @Test void anUnresolvedCandidateDoesNotExhaustDistanceSearchBeforeALaterCrossing() {
        var line=Geodesic.WGS84.InverseLine(0,0,1,1); var mid=line.Position(line.Distance()/2);
        Arc point=new Arc(p(mid.lon2,mid.lat2),p(mid.lon2,mid.lat2)), cross=arc(0,1,1,0);
        for (List<Arc> order : List.of(List.of(point,cross),List.of(cross,point))) {
            assertEquals(Relation.INTERSECTING,Wgs84BoundaryIntersection.relate(List.of(arc(0,0,1,1)),order,new Budget(80)));
            assertTrue(Wgs84SegmentDistance.withinArcs(List.of(arc(0,0,1,1)),order,0,new Budget(80)));
        }
    }

    @Test void crossPolygonsWithNoContainedOriginalVerticesStillMeetAtZeroThreshold() throws Exception {
        Geometry horizontal=read("POLYGON ((-3 -1,3 -1,3 1,-3 1,-3 -1))");
        Geometry vertical=read("POLYGON ((-1 -3,1 -3,1 3,-1 3,-1 -3))");
        var left=Wgs84PolygonRegion.prepare(horizontal); var right=Wgs84PolygonRegion.prepare(vertical);
        for (Coordinate vertex : horizontal.getCoordinates()) assertEquals(Wgs84PolygonRegion.Location.OUTSIDE,right.locate(p(vertex.x,vertex.y)));
        for (Coordinate vertex : vertical.getCoordinates()) assertEquals(Wgs84PolygonRegion.Location.OUTSIDE,left.locate(p(vertex.x,vertex.y)));
        assertTrue(Wgs84GeometryDistance.withinDistance(horizontal,vertical,0));
        assertTrue(Wgs84GeometryDistance.withinDistance(vertical,horizontal,0));
        // The local CROSS proof is refined by the verified ellipsoidal-gnomonic solver;
        // Boolean-only intersections would still retain a null contact rather than invent one.
        var nearest=Wgs84GeometryDistance.nearest(horizontal,vertical,0.1);
        double witnessDistance=Geodesic.WGS84.Inverse(nearest.first().latitude(),nearest.first().longitude(),
                nearest.second().latitude(),nearest.second().longitude()).s12;
        assertEquals(witnessDistance,nearest.distanceMetres(),1e-8);
        assertEquals(0,nearest.distanceMetres());
        assertEquals(nearest.first(),nearest.second());
    }

    @Test void broadPhaseProvesLargeDisjointSetsWithoutAFullCartesianDistanceSearch() {
        var first=new ArrayList<Arc>(); var second=new ArrayList<Arc>();
        for (int index=0;index<1024;index++) {
            double longitude=-60+index*0.1;
            first.add(arc(longitude,0,longitude+0.001,0));
            second.add(arc(longitude+0.05,0,longitude+0.051,0));
        }
        // Over one million pairs exceed the shared budget. Continuous ECEF boxes cover
        // every arc, and prove these disjoint without enumerating every pair.
        assertEquals(Relation.DISJOINT,Wgs84BoundaryIntersection.relate(first,second,budget()));
        assertFalse(Wgs84SegmentDistance.withinArcs(first,second,0,budget()));
    }

    @Test void validationAndBudgetCannotBeBypassedByAnEarlyIntersection() {
        Arc valid=arc(0,0,1,1), invalid=arc(181,0,0,0);
        var error=assertThrows(IllegalArgumentException.class,()->Wgs84SegmentDistance.withinArcs(
                List.of(valid),List.of(valid,invalid),0,budget()));
        assertEquals("GEODESIC_DISTANCE_COORDINATE_INVALID",error.getMessage()); assertNull(error.getCause());
        error=assertThrows(IllegalArgumentException.class,()->Wgs84SegmentDistance.withinArcs(
                List.of(valid),List.of(arc(0,1,1,0)),0,new Budget(1)));
        assertEquals("GEODESIC_DISTANCE_WORK_LIMIT_EXCEEDED",error.getMessage()); assertNull(error.getCause());
    }
}
