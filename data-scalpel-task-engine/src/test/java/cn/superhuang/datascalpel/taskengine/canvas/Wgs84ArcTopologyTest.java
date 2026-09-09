package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.Wgs84ArcTopology.Kind;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Arc;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Budget;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Wgs84ArcTopologyTest {
    private static Position p(double longitude,double latitude) { return new Position(longitude,latitude); }
    private static Arc arc(double a,double b,double c,double d) { return new Arc(p(a,b),p(c,d)); }
    private static Wgs84ArcTopology.Domain domain(double longitude,double latitude) {
        return new Wgs84ArcTopology.Domain(p(longitude,latitude),new Budget(Wgs84SegmentDistance.MAX_EVALUATIONS));
    }
    private static void relation(Wgs84ArcTopology.Domain domain,Arc a,Arc b,Kind expected) {
        assertEquals(expected,domain.relate(a,b).kind());
        assertEquals(expected,domain.relate(b,a).kind());
        assertEquals(expected,domain.relate(new Arc(a.end(),a.start()),b).kind());
        assertEquals(expected,domain.relate(a,new Arc(b.end(),b.start())).kind());
    }

    @Test void highLatitudeCrossingDiffersFromTheStraightLongitudeLatitudeChord() {
        var domain=domain(0,65); var curved=arc(-45,60,45,60);
        relation(domain,curved,arc(0,65,0,70),Kind.CROSS);
        relation(domain,curved,arc(0,59,0,61),Kind.DISJOINT);
    }
    @Test void dateLineAndPolarCrossingsUseOriginalArcs() {
        relation(domain(180,0),arc(179,-1,-179,1),arc(179,1,-179,-1),Kind.CROSS);
        relation(domain(0,90),arc(-90,80,90,80),arc(0,85,180,85),Kind.CROSS);
        relation(domain(0,90),arc(-90,80,90,80),arc(-90,85,90,85),Kind.OVERLAP);
    }
    @Test void exactSharedEndpointsAndFiniteAxisContactsAreNotExtendedToSupportingLines() {
        var domain=domain(0,0);
        relation(domain,arc(0,0,1,1),arc(1,1,2,0),Kind.TOUCH);
        relation(domain,arc(0,0,1,0),arc(1,0,2,0),Kind.TOUCH);
        relation(domain,arc(0,0,1,0),arc(0.5,0,0.5,1),Kind.TOUCH);
        relation(domain,arc(0,0,1,0),arc(2,0,2,1),Kind.DISJOINT);
        relation(domain,arc(0,0,1,0),arc(0.5,0,2,0),Kind.OVERLAP);
        relation(domain,arc(0,0,1,0),arc(0.5,0,0.8,0),Kind.OVERLAP);
        relation(domain,arc(0,0,1,1),arc(1,1,0,0),Kind.OVERLAP);
    }
    @Test void pointArcsAndEquivalentPoleDateLineCoordinatesAreHandledExplicitly() {
        relation(domain(180,0),arc(180,0,-180,0),arc(179,0,-179,0),Kind.TOUCH);
        relation(domain(0,90),arc(80,90,-80,90),arc(0,80,0,90),Kind.TOUCH);
        relation(domain(0,0),arc(0,0,0,0),arc(1,0,1,0),Kind.DISJOINT);
        relation(domain(0,0),arc(2,0,2,0),arc(0,0,1,0),Kind.DISJOINT);
    }
    @Test void nearCollinearNonAxisContactRemainsUnresolvedRatherThanSnapped() {
        var domain=domain(0,0);
        var line=net.sf.geographiclib.Geodesic.WGS84.InverseLine(0,0,1,1);
        var mid=line.Position(line.Distance()/2);
        relation(domain,arc(0,0,1,1),new Arc(p(mid.lon2,mid.lat2),p(mid.lon2,mid.lat2)),Kind.UNRESOLVED);
        relation(domain,arc(0,0,1,0),arc(0.5,1e-8,0.5,0.1),Kind.DISJOINT);
    }
    @Test void aCommonConvexDomainAndSharedBudgetAreRequired() {
        var domain=domain(0,0);
        var error=assertThrows(IllegalArgumentException.class,()->domain.relate(arc(0,0,1,1),arc(180,0,179,1)));
        assertEquals("GEODESIC_DISTANCE_REGION_RANGE_NOT_SUPPORTED",error.getMessage()); assertNull(error.getCause());
        // Failed domain checks must not poison the verified-position cache.
        assertEquals("GEODESIC_DISTANCE_REGION_RANGE_NOT_SUPPORTED",assertThrows(IllegalArgumentException.class,
                ()->domain.relate(arc(0,0,1,1),arc(180,0,179,1))).getMessage());
        var limited=new Wgs84ArcTopology.Domain(p(0,0),new Budget(1));
        assertEquals("GEODESIC_DISTANCE_WORK_LIMIT_EXCEEDED",assertThrows(IllegalArgumentException.class,
                ()->limited.relate(arc(0,0,1,1),arc(0,1,1,0))).getMessage());
    }
}
