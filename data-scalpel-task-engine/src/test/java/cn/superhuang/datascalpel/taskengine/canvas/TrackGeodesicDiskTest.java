package cn.superhuang.datascalpel.taskengine.canvas;

import net.sf.geographiclib.Geodesic;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrackGeodesicDiskTest {
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private static Point point(double longitude, double latitude) {
        return FACTORY.createPoint(new CoordinateXY(longitude, latitude));
    }

    private static void assertRadius(TrackGeodesicAreaBoundary disk, Point center, double radius, double maxArc) {
        var vertices = disk.boundary();
        assertTrue(vertices.size() >= 65);
        for (int i = 0; i < vertices.size(); i++) {
            var vertex = vertices.get(i);
            assertEquals(radius, Geodesic.WGS84.Inverse(center.getY(), center.getX(), vertex.latitude(), vertex.longitude()).s12, 2e-6);
            if (i > 0) {
                var previous = vertices.get(i - 1);
                assertTrue(Geodesic.WGS84.Inverse(previous.latitude(), previous.longitude(), vertex.latitude(), vertex.longitude()).s12
                        <= maxArc + 2e-6, "adjacent boundary vertices must respect the sampling bound");
            }
        }
    }

    private static void assertRendered(MultiPolygon result) {
        assertFalse(result.isEmpty()); assertTrue(result.isValid()); assertEquals(4326, result.getSRID());
        for (Coordinate coordinate : result.getCoordinates()) {
            assertTrue(Double.isFinite(coordinate.x) && Math.abs(coordinate.x) <= 180);
            assertTrue(Double.isFinite(coordinate.y) && Math.abs(coordinate.y) <= 90);
            assertTrue(Double.isNaN(coordinate.getZ())); assertTrue(Double.isNaN(coordinate.getM()));
        }
    }

    @Test void boundariesAreTrueEllipsoidalRadiusAtEquatorAndHighLatitudes() {
        for (Point center : List.of(point(15, 0), point(110, 75), point(-30, -65))) {
            var original = center.copy();
            var disk = TrackGeodesicDisk.sample(center, 120_000d, 10_000);
            assertRadius(disk, center, 120_000, 10_000);
            assertNull(disk.enclosedPole()); assertRendered(disk.render());
            assertTrue(disk.render().covers(center)); assertTrue(center.equalsExact(original));
            assertEquals(1, disk.render().getNumGeometries());
        }
    }

    @Test void cutsAtTheCircleMeridianIntersectionNotAPlanarLatitudeInterpolation() {
        for (double longitude : new double[]{179.8, -179.8, 180, -180}) {
            Point center = point(longitude, 70);
            var disk = TrackGeodesicDisk.sample(center, 200_000d, 50_000);
            assertRadius(disk, center, 200_000, 50_000);
            MultiPolygon result = disk.render(); assertRendered(result);
            assertEquals(2, result.getNumGeometries());
            assertFalse(result.covers(point(0, 70)), "the dateline must not create a world-spanning footprint");
            assertTrue(result.covers(point(longitude == 180 ? -180 : longitude, 70)));
            for (Coordinate vertex : result.getCoordinates()) if (Math.abs(vertex.x) == 180)
                assertEquals(200_000, Geodesic.WGS84.Inverse(70, longitude, vertex.y, vertex.x).s12, 2e-6);
        }
    }

    @Test void equivalentDatelineAndPoleCoordinatesHaveIdenticalFootprints() {
        assertTrue(TrackGeodesicDisk.sample(point(180, 45), 100_000d, 5000).render()
                .equalsExact(TrackGeodesicDisk.sample(point(-180, 45), 100_000d, 5000).render()));
        for (int latitude : new int[]{90, -90})
            assertTrue(TrackGeodesicDisk.sample(point(110, latitude), 100_000d, 5000).render()
                    .equalsExact(TrackGeodesicDisk.sample(point(-30, latitude), 100_000d, 5000).render()));
    }

    @Test void disksContainingEitherPoleCloseThroughThatPoleWithoutAddingHullSamples() {
        for (Point center : List.of(point(45, 89), point(-120, -89), point(0, 90), point(50, -90))) {
            var disk = TrackGeodesicDisk.sample(center, 250_000d, 20_000);
            assertRadius(disk, center, 250_000, 20_000);
            assertEquals(center.getY() > 0 ? 90 : -90, disk.enclosedPole());
            MultiPolygon result = disk.render(); assertRendered(result);
            for (int longitude : new int[]{-179, -90, 0, 90, 179})
                assertTrue(result.covers(point(longitude, Math.copySign(89.99, center.getY()))));
            assertFalse(result.covers(point(center.getX(), 0)));
            assertTrue(disk.boundary().stream().noneMatch(vertex -> Math.abs(vertex.latitude()) == 90),
                    "rendering's polar seam must not pollute actual sampled buffer vertices");
        }
    }

    @Test void poleTangencyAndNearbyNonEnclosingCirclesStayValid() {
        for (int sign : new int[]{-1, 1}) {
            Point center = point(23, sign * 80);
            double poleDistance = Geodesic.WGS84.Inverse(center.getY(), center.getX(), sign * 90, 0).s12;
            for (double factor : new double[]{0.999, 1, 1.001}) {
                var disk = TrackGeodesicDisk.sample(center, poleDistance * factor, 20_000);
                assertRadius(disk, center, poleDistance * factor, 20_000);
                assertRendered(disk.render());
                assertEquals(factor > 1 ? sign * 90 : null, disk.enclosedPole());
            }
        }
    }

    @Test void radiusIsPerObservationAndFinerSamplingDoesNotChangeIt() {
        Point center = point(120, 40);
        var small = TrackGeodesicDisk.sample(center, 1000d, 100);
        var large = TrackGeodesicDisk.sample(center, 2000d, 100);
        var fine = TrackGeodesicDisk.sample(center, 1000d, 10);
        assertRadius(small, center, 1000, 100); assertRadius(large, center, 2000, 100);
        assertRadius(fine, center, 1000, 10);
        assertTrue(large.render().covers(small.render()));
        assertTrue(fine.boundary().size() > small.boundary().size());
    }

    @Test void largeMinorDisksAndSmallPolarDisksRemainLocalToTheCorrectHemisphere() {
        for (Point center : List.of(point(0, 0), point(179.999, 0.01), point(-179, 40), point(30, -70))) {
            for (double radius : new double[]{2_000_000, 9_000_000, TrackGeodesicDisk.MAX_RADIUS_EXCLUSIVE - 1}) {
                var disk = TrackGeodesicDisk.sample(center, radius, 50_000);
                assertRadius(disk, center, radius, 50_000);
                MultiPolygon rendered = disk.render(); assertRendered(rendered);
                assertTrue(rendered.covers(center));
                double opposite = center.getX() <= 0 ? center.getX() + 180 : center.getX() - 180;
                assertFalse(rendered.covers(point(opposite, -center.getY())), "must never render the complementary disk");
            }
        }
        for (int sign : new int[]{-1, 1}) {
            Point center = point(-179.99, sign * 89.999999);
            var disk = TrackGeodesicDisk.sample(center, 1d, 0.1);
            assertRadius(disk, center, 1, 0.1); assertRendered(disk.render());
            assertEquals(sign * 90, disk.enclosedPole());
        }
    }

    @Test void deterministicGeographicCoverageSamplesDoNotCreateWrappedChords() {
        var random = new java.util.Random(9020243);
        for (int i = 0; i < 80; i++) {
            Point center = point(random.nextDouble(-180, 180), random.nextDouble(-90, 90));
            double radius = random.nextDouble(10, 2_000_000);
            var disk = TrackGeodesicDisk.sample(center, radius, 25_000);
            assertRadius(disk, center, radius, 25_000);
            MultiPolygon result = disk.render(); assertRendered(result);
            assertTrue(result.covers(center));
            if (disk.enclosedPole() == null) for (int j = 0; j < result.getNumGeometries(); j++) {
                Coordinate[] ring = ((Polygon) result.getGeometryN(j)).getExteriorRing().getCoordinates();
                for (int k = 1; k < ring.length; k++)
                    assertTrue(Math.abs(ring[k].x - ring[k - 1].x) <= 180, "no artificial wrap across the earth");
            }
        }
    }

    @Test void topologyFailuresNeverRetainCoordinateBearingCauses() {
        var invalid = new TrackGeodesicAreaBoundary(List.of(new TrackGeodesicAreaBoundary.Vertex(12.345678, 45.678912)), null);
        assertFailure("TRACK_AREA_GEOMETRY_INVALID", invalid::render);
    }

    @Test void snapshotsAndRenderingCannotMutateTheSampledBoundary() {
        var disk = TrackGeodesicDisk.sample(point(179, 60), 200_000d, 10_000);
        var expected = disk.render();
        var mutable = disk.render(); mutable.apply((CoordinateFilter) coordinate -> coordinate.x += 500);
        assertTrue(expected.equalsExact(disk.render()));
        assertThrows(UnsupportedOperationException.class, () -> disk.boundary().clear());
    }

    @Test void invalidInputsAndUnboundedSamplingFailWithSafeCodes() {
        Point center = point(0, 0);
        for (Double radius : java.util.Arrays.asList(null, 0d, -1d, Double.NaN, Double.POSITIVE_INFINITY))
            assertFailure("TRACK_BUFFER_DISTANCE_INVALID", () -> TrackGeodesicDisk.sample(center, radius, 100));
        for (double step : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertFailure("INVALID_TRACK_GEODESIC_SEGMENT_LENGTH", () -> TrackGeodesicDisk.sample(center, 100d, step));
        for (Point invalid : List.of(point(181, 0), point(0, -91), point(Double.NaN, 0), FACTORY.createPoint(),
                new GeometryFactory().createPoint(new CoordinateXY(0, 0)), FACTORY.createPoint(new Coordinate(0, 0, 1))))
            assertFailure("TRACK_GEODESIC_COORDINATE_INVALID", () -> TrackGeodesicDisk.sample(invalid, 100d, 100));
        assertFailure("TRACK_GEODESIC_COORDINATE_INVALID", () -> TrackGeodesicDisk.sample(null, 100d, 100));
        assertFailure("TRACK_GEODESIC_BUFFER_RANGE_NOT_SUPPORTED",
                () -> TrackGeodesicDisk.sample(center, TrackGeodesicDisk.MAX_RADIUS_EXCLUSIVE, 1000));
        assertFailure("TRACK_AREA_VERTEX_LIMIT_EXCEEDED", () -> TrackGeodesicDisk.sample(center, 1000d, Double.MIN_VALUE));
    }

    private static void assertFailure(String code, org.junit.jupiter.api.function.Executable action) {
        var error = assertThrows(IllegalArgumentException.class, action);
        assertEquals(code, error.getMessage()); assertNull(error.getCause());
    }
}
