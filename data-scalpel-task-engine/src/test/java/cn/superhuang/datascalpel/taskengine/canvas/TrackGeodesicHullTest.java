package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.TrackGeodesicAreaBoundary.Vertex;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.union.UnaryUnionOp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrackGeodesicHullTest {
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static Vertex vertex(double longitude, double latitude) { return new Vertex(longitude, latitude); }
    private static Point point(Vertex vertex) { return FACTORY.createPoint(vertex.coordinate()); }
    private static TrackGeodesicAreaBoundary disk(Vertex center, double radius) {
        return TrackGeodesicDisk.sample(point(center), radius, 25_000);
    }
    private static TrackGeodesicAreaBoundary pair(Vertex a, double radiusA, Vertex b, double radiusB) {
        var vertices = new ArrayList<>(disk(a, radiusA).boundary()); vertices.addAll(disk(b, radiusB).boundary());
        return TrackGeodesicHull.build(vertices, TrackGeodesicHull.midpoint(a, b), 25_000);
    }
    private static void assertGeometry(MultiPolygon geometry) {
        assertFalse(geometry.isEmpty()); assertTrue(geometry.isValid()); assertEquals(4326, geometry.getSRID());
        for (Coordinate coordinate : geometry.getCoordinates()) {
            assertTrue(Double.isFinite(coordinate.x) && Math.abs(coordinate.x) <= 180);
            assertTrue(Double.isFinite(coordinate.y) && Math.abs(coordinate.y) <= 90);
        }
    }

    @Test void equatorialPairConnectsUnequalFootprints() {
        Vertex a = vertex(-5, 0), b = vertex(5, 0);
        var area = pair(a, 50_000, b, 100_000).render(); assertGeometry(area);
        assertTrue(area.covers(point(a))); assertTrue(area.covers(point(b)));
        assertTrue(area.covers(point(vertex(0, 0))));
        assertTrue(area.covers(point(vertex(5, 0.8)))); assertFalse(area.covers(point(vertex(-5, 0.8))));
        assertFalse(area.covers(point(vertex(0, 3))));
    }

    @Test void highLatitudeConnectionFollowsGeodesicInsteadOfALatitudeChord() {
        Vertex a = vertex(-45, 60), b = vertex(45, 60);
        var area = pair(a, 50_000, b, 50_000).render(); assertGeometry(area);
        Vertex midpoint = TrackGeodesicHull.midpoint(a, b);
        assertTrue(midpoint.latitude() > 67); assertTrue(area.covers(point(midpoint)));
        assertFalse(area.covers(point(vertex(0, 60))));
    }

    @Test void datelineConnectionDoesNotMakeAPolygonAcrossGreenwich() {
        Vertex a = vertex(175, 60), b = vertex(-175, 60);
        var area = pair(a, 100_000, b, 200_000).render(); assertGeometry(area);
        assertEquals(2, area.getNumGeometries()); assertTrue(area.covers(point(a))); assertTrue(area.covers(point(b)));
        assertTrue(area.covers(point(TrackGeodesicHull.midpoint(a, b))));
        assertFalse(area.covers(point(vertex(0, 60))));
        for (int i = 0; i < area.getNumGeometries(); i++)
            assertTrue(area.getGeometryN(i).getEnvelopeInternal().getWidth() < 20);
    }

    @Test void polarFootprintsDoNotFeedRenderingClosuresIntoTheHull() {
        for (int sign : new int[]{-1, 1}) {
            Vertex a = vertex(-45, sign * 89), b = vertex(100, sign * 88);
            var boundary = pair(a, 200_000, b, 100_000);
            var area = boundary.render(); assertGeometry(area);
            assertEquals(sign * 90, boundary.enclosedPole());
            assertTrue(area.covers(point(a))); assertTrue(area.covers(point(b)));
            assertFalse(area.covers(point(vertex(0, 0))));
            for (int longitude : new int[]{-170, -90, 0, 90, 170})
                assertTrue(area.covers(point(vertex(longitude, sign * 89.99))));
        }
    }

    @Test void adjacentPairUnionPreservesTheBendInsteadOfMakingAWholeTrackHull() {
        Vertex a = vertex(0, 0), b = vertex(10, 0), c = vertex(10, 10);
        var first = pair(a, 50_000, b, 50_000).render();
        var second = pair(b, 50_000, c, 50_000).render();
        Geometry result = UnaryUnionOp.union(List.of(first, second));
        assertTrue(result.isValid()); assertTrue(result.covers(point(vertex(5, 0))));
        assertTrue(result.covers(point(vertex(10, 5)))); assertFalse(result.covers(point(vertex(4, 6))));
    }

    @Test void originalPolygonVerticesAndInteriorSamplesHaveTheSameHull() {
        var ring = List.of(vertex(-2, -1), vertex(-2, 1), vertex(2, 1), vertex(2, -1));
        var expected = TrackGeodesicHull.build(ring, vertex(0, 0), 10_000).render();
        var input = new ArrayList<>(ring); input.add(vertex(0, 0)); input.add(vertex(1, 0)); input.addAll(ring);
        Collections.reverse(input);
        assertTrue(expected.equalsExact(TrackGeodesicHull.build(input, vertex(0, 0), 10_000).render()));
        assertGeometry(expected); assertTrue(expected.covers(point(vertex(0, 0))));
    }

    @Test void inputPermutationAndEquivalentDatelineVerticesDoNotChangeTheBoundary() {
        var samples = new ArrayList<>(disk(vertex(179, 40), 200_000).boundary());
        samples.addAll(disk(vertex(-179, 40), 100_000).boundary());
        var reference = vertex(180, 40);
        var expected = TrackGeodesicHull.build(samples, reference, 20_000);
        Collections.shuffle(samples, new java.util.Random(1903));
        var actual = TrackGeodesicHull.build(samples, vertex(-180, 40), 20_000);
        assertEquals(expected, actual); assertTrue(expected.render().equalsExact(actual.render()));
    }

    @Test void convexDomainAndDegenerateInputsFailSafely() {
        assertFailure("TRACK_GEODESIC_HULL_INVALID", () -> TrackGeodesicHull.build(
                List.of(vertex(0, 0), vertex(1, 0), vertex(2, 0)), vertex(0, 0), 1000));
        assertFailure("TRACK_GEODESIC_HULL_INVALID", () -> TrackGeodesicHull.build(
                List.of(vertex(0, 0), vertex(0, 0), vertex(0, 0)), vertex(0, 0), 1000));
        assertFailure("TRACK_GEODESIC_HULL_RANGE_NOT_SUPPORTED", () -> TrackGeodesicHull.build(
                List.of(vertex(0, 0), vertex(100, 0), vertex(50, 1)), vertex(0, 0), 1000));
        assertFailure("TRACK_GEODESIC_HULL_RANGE_NOT_SUPPORTED", () -> TrackGeodesicHull.midpoint(vertex(0, 0), vertex(180, 0)));
        assertFailure("TRACK_GEODESIC_COORDINATE_INVALID", () -> TrackGeodesicHull.build(
                List.of(vertex(0, 91), vertex(1, 0), vertex(0, 0)), vertex(0, 0), 1000));
        assertFailure("INVALID_TRACK_GEODESIC_SEGMENT_LENGTH", () -> TrackGeodesicHull.build(
                List.of(vertex(0, 1), vertex(1, 0), vertex(0, 0)), vertex(0, 0), Double.NaN));
    }

    @Test void supportValidationHasAnExplicitWorkBoundBeforeTheQuadraticPass() {
        var circle = TrackGeodesicDisk.sample(point(vertex(0, 0)), 1000d, 2 * Math.PI * 1000 / 4500);
        assertFailure("TRACK_GEODESIC_HULL_WORK_LIMIT_EXCEEDED",
                () -> TrackGeodesicHull.build(circle.boundary(), vertex(0, 0), 100));
    }

    @Test void genuinePoleVerticesUseIncomingAndOutgoingMeridiansWithoutSlantedPolarLegs() {
        for (int sign : new int[]{-1, 1}) {
            var boundary = TrackGeodesicHull.build(List.of(vertex(-40, sign * 85), vertex(0, sign * 90),
                    vertex(40, sign * 85)), vertex(0, sign * 86), 25_000);
            assertGeometry(boundary.render());
            assertPolarLegs(boundary);
        }
    }

    @Test void anEdgePassingThroughAPoleAlsoUsesTheCorrectMeridians() {
        for (int sign : new int[]{-1, 1}) {
            var boundary = TrackGeodesicHull.build(List.of(vertex(0, sign * 85), vertex(180, sign * 85),
                    vertex(90, sign * 80)), vertex(90, sign * 85), 25_000);
            var rendered = boundary.render(); assertGeometry(rendered); assertPolarLegs(boundary);
            assertTrue(rendered.covers(point(vertex(90, sign * 89))));
            assertFalse(rendered.covers(point(vertex(-90, sign * 89))), "must not select the polar cap complement");
            assertTrue(boundary.boundary().stream().anyMatch(vertex -> Math.abs(vertex.latitude()) == 90));
        }
    }

    private static void assertPolarLegs(TrackGeodesicAreaBoundary boundary) {
        var vertices = boundary.boundary();
        for (int i = 1; i < vertices.size(); i++) {
            Vertex a = vertices.get(i - 1), b = vertices.get(i);
            if ((Math.abs(a.latitude()) == 90) != (Math.abs(b.latitude()) == 90)) {
                double delta = a.longitude() - b.longitude();
                assertEquals(0, delta - 360 * Math.rint(delta / 360), 1e-10,
                        "a geodesic entering/leaving the pole must use the adjacent meridian");
            }
        }
    }

    @Test void deterministicLocalCloudsAndLargeConvexDomainsHaveFiniteVerifiedHulls() {
        var random = new java.util.Random(27092026);
        for (int sample = 0; sample < 40; sample++) {
            Vertex reference = vertex(random.nextDouble(-180, 180), random.nextDouble(-85, 85));
            double extent = sample < 20 ? 100_000 : 8_000_000;
            var input = new ArrayList<Vertex>();
            for (int i = 0; i < 24; i++) {
                var position = net.sf.geographiclib.Geodesic.WGS84.Direct(reference.latitude(), reference.longitude(),
                        random.nextDouble(360), random.nextDouble(100, extent));
                input.add(vertex(position.lon2, position.lat2));
            }
            var hull = TrackGeodesicHull.build(input, reference, 20_000);
            var rendered = hull.render(); assertGeometry(rendered);
            for (Vertex vertex : input) assertTrue(rendered.distance(point(vertex)) < 1e-8,
                    "sampled longitude/latitude rendering must contain input points up to numeric rounding");
            var original = new ArrayList<>(input); Collections.reverse(input);
            assertEquals(hull, TrackGeodesicHull.build(input, reference, 20_000));
            assertEquals(original.size(), input.size());
        }
    }

    private static void assertFailure(String code, org.junit.jupiter.api.function.Executable action) {
        var error = assertThrows(IllegalArgumentException.class, action);
        assertEquals(code, error.getMessage()); assertNull(error.getCause());
    }
}
