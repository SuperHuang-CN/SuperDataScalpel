package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SnapTracksMapMatcherTest {
    private static final GeometryFactory FACTORY = new GeometryFactory();
    private final SnapTracksMapMatcher matcher = new SnapTracksMapMatcher(
            SpatialDistanceMethod.PLANAR, 10d, 1d);

    @Test
    void followsTwoDirectlyConnectedLines() {
        var first = candidate(1, "L1", "A", "B", SnapTracksMapMatcher.Direction.BOTH,
                0.9d, 10d, 0d);
        var second = candidate(2, "L2", "B", "C", SnapTracksMapMatcher.Direction.BOTH,
                0.1d, 10d, 0d);

        List<SnapTracksMapMatcher.Choice> result = matcher.match(List.of(
                observation(10, 1, 9, first), observation(11, 2, 11, second)));

        assertEquals(1L, result.getFirst().lineRowId());
        assertEquals(2L, result.getLast().lineRowId());
    }

    @Test
    void rejectsCloserDisconnectedCandidateWhenAConnectedCandidateExists() {
        var first = candidate(1, "L1", "A", "B", SnapTracksMapMatcher.Direction.BOTH,
                0.9d, 10d, 0d);
        var connected = candidate(2, "L2", "B", "C", SnapTracksMapMatcher.Direction.BOTH,
                0.1d, 10d, 1d);
        var disconnected = candidate(3, "L3", "X", "Y", SnapTracksMapMatcher.Direction.BOTH,
                0.1d, 10d, 0d);

        List<SnapTracksMapMatcher.Choice> result = matcher.match(List.of(
                observation(10, 1, 9, first),
                new SnapTracksMapMatcher.Observation(11, 2, point(11),
                        List.of(disconnected, connected))));

        assertEquals(2L, result.getLast().lineRowId());
    }

    @Test
    void doesNotTraverseAForwardOnlyLineBackwards() {
        var later = candidate(1, "L1", "A", "B", SnapTracksMapMatcher.Direction.FORWARD,
                0.8d, 10d, 0d);
        var earlier = candidate(1, "L1", "A", "B", SnapTracksMapMatcher.Direction.FORWARD,
                0.2d, 10d, 0d);

        List<SnapTracksMapMatcher.Choice> result = matcher.match(List.of(
                observation(10, 1, 8, later), observation(11, 2, 2, earlier)));

        assertEquals(1L, result.getFirst().lineRowId());
        assertNull(result.getLast().lineRowId());
    }

    @Test
    void leavesSingleObservationUnmatched() {
        var candidate = candidate(1, "L1", "A", "B", SnapTracksMapMatcher.Direction.BOTH,
                0.5d, 10d, 0d);

        assertNull(matcher.match(List.of(observation(10, 1, 5, candidate)))
                .getFirst().lineRowId());
    }

    private static SnapTracksMapMatcher.Observation observation(
            long id, long order, double x, SnapTracksMapMatcher.Candidate candidate
    ) {
        return new SnapTracksMapMatcher.Observation(id, order, point(x), List.of(candidate));
    }

    private static SnapTracksMapMatcher.Candidate candidate(
            long id,
            String key,
            Object from,
            Object to,
            SnapTracksMapMatcher.Direction direction,
            double fraction,
            double length,
            double distance
    ) {
        return new SnapTracksMapMatcher.Candidate(
                id, key, from, to, direction, fraction, length, distance);
    }

    private static Point point(double x) {
        return FACTORY.createPoint(new Coordinate(x, 0d));
    }
}
