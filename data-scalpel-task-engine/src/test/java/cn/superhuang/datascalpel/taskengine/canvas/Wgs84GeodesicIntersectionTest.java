package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Arc;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Budget;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Position;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Wgs84GeodesicIntersectionTest {
    @Test
    void localCrossingsReturnOnePositionOnBothFiniteWgs84Arcs() {
        for (List<Arc> pair : List.of(
                List.of(arc(-45, 60, 45, 60), arc(0, 65, 0, 70)),
                List.of(arc(179, -1, -179, 1), arc(179, 1, -179, -1)),
                List.of(arc(-90, 80, 90, 80), arc(0, 85, 180, 85)))) {
            Position contact = Wgs84GeodesicIntersection.crossing(pair.get(0), pair.get(1), budget());
            assertNotNull(contact);
            assertOnArc(contact, pair.get(0));
            assertOnArc(contact, pair.get(1));
        }
    }

    @Test
    void crossingPositionIsStableUnderSideAndEndpointOrder() {
        Arc first = arc(-45, 60, 45, 60), second = arc(0, 65, 0, 70);
        Position expected = Wgs84GeodesicIntersection.crossing(first, second, budget());
        assertNotNull(expected);
        for (List<Arc> pair : List.of(
                List.of(second, first),
                List.of(reverse(first), second),
                List.of(first, reverse(second)),
                List.of(reverse(second), reverse(first)))) {
            Position actual = Wgs84GeodesicIntersection.crossing(pair.get(0), pair.get(1), budget());
            assertNotNull(actual);
            assertEquals(0, distance(expected, actual), 1e-6);
        }
    }

    @Test
    void parallelPairDoesNotInventAContact() {
        assertNull(Wgs84GeodesicIntersection.crossing(
                arc(0, 0, 1, 0), arc(0, 1, 1, 1), budget()));
    }

    private static void assertOnArc(Position contact, Arc arc) {
        var result = Wgs84SegmentDistance.nearest(contact, contact, arc.start(), arc.end(),
                0.00001, budget());
        assertTrue(result.distanceMetres() <= 0.00001, result.toString());
    }

    private static double distance(Position a, Position b) {
        return net.sf.geographiclib.Geodesic.WGS84.Inverse(
                a.latitude(), a.longitude(), b.latitude(), b.longitude()).s12;
    }

    private static Arc reverse(Arc arc) {
        return new Arc(arc.end(), arc.start());
    }

    private static Arc arc(double x1, double y1, double x2, double y2) {
        return new Arc(new Position(x1, y1), new Position(x2, y2));
    }

    private static Budget budget() {
        return new Budget(Wgs84SegmentDistance.MAX_EVALUATIONS);
    }
}
