package cn.superhuang.datascalpel.taskengine.canvas;

import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DwellRangeAssignmentTest {
    private static final GeometryFactory FACTORY = new GeometryFactory();
    private static Point point(double x, double y) { return FACTORY.createPoint(new Coordinate(x, y)); }
    private static Row row(long key, long minute, Point point) {
        return RowFactory.create(key, Timestamp.from(java.time.Instant.EPOCH.plusSeconds(minute * 60)), point);
    }
    private static DwellRangeAssignment assignment(double radius, long minutes, boolean geodesic) {
        return new DwellRangeAssignment(new int[]{0}, 2, 1, radius, minutes * 60_000d, geodesic);
    }
    @Test void doesNotMistakeSlowDriftForDwell() {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i <= 20; i++) rows.add(row(1, i, point(i * 50, 0)));
        assertTrue(assignment(100, 10, false).assign(rows).stream().allMatch(r -> r.isNullAt(3)));
    }
    @Test void includesEqualDistanceAndDurationThresholdsAndExtendsBothDirections() {
        // A failed seed at -2.5 is outside the next seed's radius but inside its mean radius.
        var rows = List.of(row(1, 0, point(-2.5, 0)), row(1, 10, point(0, 0)),
                row(1, 20, point(-2, 0)), row(1, 30, point(-2.5, 0)));
        var output = assignment(2, 10, false).assign(rows);
        assertEquals(List.of(1L, 1L, 1L, 1L), output.stream().map(r -> r.getLong(3)).toList());
        var equality = assignment(2, 20, false).assign(List.of(row(1, 0, point(0, 0)),
                row(1, 10, point(1, 0)), row(1, 20, point(2, 0))));
        assertTrue(equality.stream().allMatch(r -> r.getLong(3) == 1L));
    }
    @Test void nullAndEmptyBreakMembershipAndSeparateSegmentsDoNotReuseObservations() throws Exception {
        var rows = List.of(row(1, 0, point(0, 0)), row(1, 10, point(0, 0)),
                row(1, 20, null), row(1, 30, FACTORY.createPoint()),
                row(1, 40, point(0, 0)), row(1, 50, point(0, 0)),
                row(2, 0, point(0, 0)), row(2, 10, point(0, 0)));
        List<Row> output = new ArrayList<>();
        assignment(1, 10, false).call(rows.iterator()).forEachRemaining(output::add);
        assertEquals(8, output.size());
        assertNull(output.get(2).get(3)); assertNull(output.get(3).get(3));
        assertEquals(1L, output.get(0).getLong(3));
        assertEquals(2L, output.get(4).getLong(3));
        assertEquals(1L, output.get(6).getLong(3));
    }
    @Test void geodesicMeanAndMembershipHandleDateline() {
        Point a = point(179.999, 0), b = point(-179.999, 0);
        var output = assignment(300, 10, true).assign(List.of(row(1, 0, a), row(1, 10, b)));
        assertEquals(1L, output.get(1).getLong(3));
        Point mean = DwellRangeAssignment.meanCenter(new Point[]{a, b}, true);
        assertEquals(180, Math.abs(mean.getX()), 1e-9);
        assertEquals(0, mean.getY(), 1e-9);
    }
    @Test void invalidCoordinatesFailWithoutLeakingCoordinates() {
        var error = assertThrows(IllegalArgumentException.class, () -> assignment(1, 10, true)
                .assign(List.of(row(1, 0, point(999, 0)))));
        assertEquals("TRACK_DWELL_POINT_INVALID", error.getMessage());
    }
    @Test void geodesicHullSplitsDatelineInsteadOfCoveringGreenwich() {
        var points = FACTORY.createMultiPointFromCoords(new Coordinate[]{
                new Coordinate(179.9, 0), new Coordinate(-179.9, 0), new Coordinate(179.9, 1),
                new Coordinate(-179.9, 1)});
        var hull = DwellHullGeometry.hull(points, true);
        assertTrue(hull.isValid());
        assertEquals(0.2, hull.getArea(), 1e-9);
        assertFalse(hull.intersects(point(0, 0.5)));
        assertTrue(hull.covers(point(179.95, 0.5)));
        assertTrue(hull.covers(point(-179.95, 0.5)));
    }
}
