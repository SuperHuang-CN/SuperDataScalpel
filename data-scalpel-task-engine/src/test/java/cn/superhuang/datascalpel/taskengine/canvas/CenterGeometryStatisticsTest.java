package cn.superhuang.datascalpel.taskengine.canvas;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CenterGeometryStatisticsTest {
    private CenterGeometryStatistics.Observation item(double x, double y, double weight, int order) {
        return new CenterGeometryStatistics.Observation(new GeometryFactory().createPoint(new CoordinateXY(x, y)), weight, order, order);
    }
    @Test void medianHasAConvergenceCertificateRatherThanAFixedIterationGuess() {
        var stats = new CenterGeometryStatistics(List.of(item(0, 0, 1, 0), item(2, 0, 1, 1), item(0, 2, 1, 2)), 3857);
        assertEquals(2d / 3, stats.mean().getX(), 1e-12);
        double expected = 1 - 1 / Math.sqrt(3);
        assertEquals(expected, stats.median().getX(), 1e-8); assertEquals(expected, stats.median().getY(), 1e-8);
        assertEquals("SPATIAL_CENTER_MEDIAN_NOT_CONVERGED", assertThrows(IllegalArgumentException.class, () -> stats.median(0, 1e-10)).getMessage());
    }
    @Test void strongAndCoincidentWeightsConvergeAtAnObservedPoint() {
        var stats = new CenterGeometryStatistics(List.of(item(0, 0, 100, 0), item(2, 0, 1, 1), item(0, 2, 1, 2), item(0, 0, 3, 3)), 3857);
        assertEquals(0, stats.median().getX(), 1e-8); assertEquals(0, stats.median().getY(), 1e-8);
        assertEquals(2d / 105, stats.mean().getX(), 1e-12);
    }
    @Test void lineCentroidsAreRepresentativesButCentralReturnsTheOriginalGeometry() throws Exception {
        var line = new WKTReader().read("LINESTRING (0 0, 2 0)");
        var stats = new CenterGeometryStatistics(List.of(new CenterGeometryStatistics.Observation(line, 1, "line", 1), item(1, 0, 1, 2)), 3857);
        assertEquals(1, stats.mean().getX()); assertSame(line, stats.central().geometry());
    }
    @Test void ellipsesHaveDefinedAxesScaleAndDegenerateEmptyResults() {
        var stats = new CenterGeometryStatistics(List.of(item(-2, 0, 1, 1), item(2, 0, 1, 2), item(0, -1, 1, 3), item(0, 1, 1, 4)), 3857);
        var ellipse = stats.dispersion(1, true); assertTrue(ellipse.isValid());
        assertEquals(4, ellipse.getEnvelopeInternal().getWidth(), 1e-10); assertEquals(2, ellipse.getEnvelopeInternal().getHeight(), 1e-10);
        assertEquals(4 * ellipse.getArea(), stats.dispersion(2, true).getArea(), 1e-9);
        var degenerate = new CenterGeometryStatistics(List.of(item(0, 0, 1, 1), item(2, 0, 1, 2)), 3857);
        assertTrue(degenerate.dispersion(1, true).isEmpty());
        assertTrue(new CenterGeometryStatistics(List.of(item(1, 1, 1, 1)), 3857).dispersion(1, false).isEmpty());
    }
    @Test void zeroWeightsAndInvalidInputsHaveExplicitSafeBoundaries() throws Exception {
        assertFalse(new CenterGeometryStatistics(List.of(item(0, 0, 0, 1)), 3857).hasWeight());
        for (double weight : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY})
            assertEquals("SPATIAL_CENTER_WEIGHT_INVALID", assertThrows(IllegalArgumentException.class,
                    () -> new CenterGeometryStatistics(List.of(item(0, 0, weight, 1)), 3857)).getMessage());
        assertNull(CenterGeometryStatistics.checked(new WKTReader().read("POINT EMPTY")));
        assertThrows(IllegalArgumentException.class, () -> CenterGeometryStatistics.checked(new WKTReader().read("GEOMETRYCOLLECTION (POINT (0 0))")));
    }
    @Test void vertexBudgetRejectsFewLargeShapesBeforeGroupComputation() {
        Coordinate[] coordinates = new Coordinate[1001];
        for (int i = 0; i < coordinates.length; i++) coordinates[i] = new CoordinateXY(i, 0);
        var line = new GeometryFactory().createLineString(coordinates);
        var observations = java.util.Collections.nCopies(1000, new CenterGeometryStatistics.Observation(line, 1, 1, 1));
        assertEquals("SPATIAL_CENTER_GROUP_LIMIT_EXCEEDED", assertThrows(IllegalArgumentException.class,
                () -> new CenterGeometryStatistics(observations, 3857)).getMessage());
    }

    @Test void distantZeroWeightObservationsDoNotChangeWeightedStatistics() {
        var weighted = List.of(item(0, 0, 1, 1), item(2, 0, 1, 2), item(0, 2, 1, 3));
        var expected = new CenterGeometryStatistics(weighted, 3857);
        for (double far : new double[]{1e20, 1e200, Double.MAX_VALUE, -Double.MAX_VALUE}) {
            var observations = new java.util.ArrayList<>(weighted);
            observations.add(item(far, far, 0, 0));
            var actual = new CenterGeometryStatistics(observations, 3857);
            assertEquals(expected.mean().getX(), actual.mean().getX(), 1e-14);
            assertEquals(expected.mean().getY(), actual.mean().getY(), 1e-14);
            assertEquals(expected.median().getX(), actual.median().getX(), 1e-9);
            assertEquals(expected.median().getY(), actual.median().getY(), 1e-9);
            assertTrue(expected.dispersion(1, true).equalsExact(actual.dispersion(1, true), 1e-12));
            assertTrue(expected.dispersion(2, false).equalsExact(actual.dispersion(2, false), 1e-12));
            assertEquals(expected.central().id(), actual.central().id());
        }
    }

    @Test void zeroWeightFeaturesRemainEligibleCentralCandidates() {
        var zeroCenter = item(1, 1, 0, 0);
        var observations = List.of(item(0, 0, 1, 1), item(2, 0, 1, 2), item(2, 2, 1, 3), item(0, 2, 1, 4), zeroCenter);
        var statistics = new CenterGeometryStatistics(observations, 3857);
        assertSame(zeroCenter, statistics.central());
        assertEquals(1, statistics.mean().getX());
        assertEquals(1, statistics.mean().getY());
    }
}
