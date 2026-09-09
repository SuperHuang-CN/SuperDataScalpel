package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.TrackGeodesicAreaBoundary.Vertex;
import net.sf.geographiclib.Constants;
import net.sf.geographiclib.Geodesic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * A clockwise, verified geodesic hull of sampled footprint vertices in a local convex domain.
 * Bearings and support tests use WGS84 inverse geodesics, never a longitude/latitude planar hull.
 * The caller supplies an explicit domain reference, normally the midpoint of two observations.
 */
final class TrackGeodesicHull {
    // Positive-curvature bound: pi / (2 * sqrt(max Gaussian curvature)) for WGS84.
    static final double MAX_REFERENCE_RADIUS = Math.PI * Constants.WGS84_a * (1 - Constants.WGS84_f) / 2;
    private static final int MAX_VERTICES = 1_000_000;
    private static final long MAX_SUPPORT_TESTS = 20_000_000;
    private static final double SIDE_TOLERANCE = 2e-14;
    private static final Comparator<Vertex> ORDER = Comparator.comparingDouble(Vertex::longitude).thenComparingDouble(Vertex::latitude);

    private TrackGeodesicHull() { }

    static TrackGeodesicAreaBoundary build(List<Vertex> input, Vertex reference, double maximumSegmentLength) {
        if (input == null || input.size() < 3) throw failure("TRACK_GEODESIC_HULL_INVALID");
        if (input.size() > MAX_VERTICES) throw failure("TRACK_AREA_VERTEX_LIMIT_EXCEEDED");
        if (!Double.isFinite(maximumSegmentLength) || maximumSegmentLength <= 0)
            throw failure("INVALID_TRACK_GEODESIC_SEGMENT_LENGTH");
        reference = canonical(reference);
        var distinct = new TreeSet<>(ORDER);
        for (Vertex vertex : input) distinct.add(canonical(vertex));
        if (distinct.size() < 3) throw failure("TRACK_GEODESIC_HULL_INVALID");
        var points = new ArrayList<>(distinct);
        Vertex anchor = null;
        double farthest = -1;
        for (Vertex vertex : points) {
            double distance = inverseDistance(reference, vertex);
            if (distance >= MAX_REFERENCE_RADIUS) throw failure("TRACK_GEODESIC_HULL_RANGE_NOT_SUPPORTED");
            // Within the local convex domain, the farthest point is an extreme point.
            // Canonical ordering supplies deterministic ties, independent of input order.
            if (distance > farthest) { farthest = distance; anchor = vertex; }
        }
        var radial = new ArrayList<Radial>(points.size() - 1);
        for (Vertex vertex : points) if (!vertex.equals(anchor)) {
            var inverse = Geodesic.WGS84.Inverse(anchor.latitude(), anchor.longitude(), vertex.latitude(), vertex.longitude());
            radial.add(new Radial(vertex, (inverse.azi1 + 360) % 360, inverse.s12));
        }
        radial.sort(Comparator.comparingDouble(Radial::bearing).thenComparingDouble(Radial::distance).thenComparing(Radial::point, ORDER));
        double largestGap = -1;
        int first = 0;
        for (int i = 0; i < radial.size(); i++) {
            double next = i + 1 < radial.size() ? radial.get(i + 1).bearing() : radial.getFirst().bearing() + 360;
            double gap = next - radial.get(i).bearing();
            if (gap > largestGap) { largestGap = gap; first = (i + 1) % radial.size(); }
        }
        if (largestGap < 180 - 1e-10) throw failure("TRACK_GEODESIC_HULL_RANGE_NOT_SUPPORTED");
        List<Vertex> hull = new ArrayList<>(); hull.add(anchor);
        for (int i = 0; i < radial.size(); i++) {
            Vertex vertex = radial.get((first + i) % radial.size()).point();
            while (hull.size() >= 2 && side(hull.get(hull.size() - 2), hull.getLast(), vertex) <= SIDE_TOLERANCE)
                hull.removeLast();
            hull.add(vertex);
        }
        while (hull.size() >= 3 && side(hull.get(hull.size() - 2), hull.getLast(), anchor) <= SIDE_TOLERANCE)
            hull.removeLast();
        if (hull.size() < 3 || side(hull.getLast(), anchor, hull.get(1)) <= SIDE_TOLERANCE)
            throw failure("TRACK_GEODESIC_HULL_INVALID");
        // Never return a merely plausible planar seed. Every original point must lie on the
        // interior (right-hand) side of every shortest-geodesic hull edge.
        if ((long) hull.size() * points.size() > MAX_SUPPORT_TESTS)
            throw failure("TRACK_GEODESIC_HULL_WORK_LIMIT_EXCEEDED");
        for (int i = 0; i < hull.size(); i++) {
            Vertex a = hull.get(i), b = hull.get((i + 1) % hull.size());
            for (Vertex point : points) if (side(a, b, point) < -SIDE_TOLERANCE)
                throw failure("TRACK_GEODESIC_HULL_INVALID");
        }
        hull.add(anchor);
        return TrackGeodesicAreaBoundary.fromClockwiseRing(hull, maximumSegmentLength);
    }

    static Vertex midpoint(Vertex a, Vertex b) {
        a = canonical(a); b = canonical(b);
        var line = Geodesic.WGS84.InverseLine(a.latitude(), a.longitude(), b.latitude(), b.longitude());
        if (line.Distance() >= 2 * MAX_REFERENCE_RADIUS) throw failure("TRACK_GEODESIC_HULL_RANGE_NOT_SUPPORTED");
        var mid = line.Position(line.Distance() / 2);
        return canonical(new Vertex(mid.lon2, mid.lat2));
    }

    /** Positive means to the right of the directed shortest geodesic a->b. */
    private static double side(Vertex a, Vertex b, Vertex point) {
        if (point.equals(a) || point.equals(b)) return 0;
        var edge = Geodesic.WGS84.Inverse(a.latitude(), a.longitude(), b.latitude(), b.longitude());
        var candidate = Geodesic.WGS84.Inverse(a.latitude(), a.longitude(), point.latitude(), point.longitude());
        return Math.sin(Math.toRadians(candidate.azi1 - edge.azi1));
    }

    private static double inverseDistance(Vertex a, Vertex b) {
        return Geodesic.WGS84.Inverse(a.latitude(), a.longitude(), b.latitude(), b.longitude()).s12;
    }

    private static Vertex canonical(Vertex vertex) {
        // Internal footprint boundaries may be unwrapped once around a pole/dateline.
        if (vertex == null || !Double.isFinite(vertex.longitude()) || !Double.isFinite(vertex.latitude())
                || Math.abs(vertex.longitude()) > 540 || Math.abs(vertex.latitude()) > 90)
            throw failure("TRACK_GEODESIC_COORDINATE_INVALID");
        double longitude = Math.abs(vertex.latitude()) == 90 ? 0
                : vertex.longitude() - 360 * Math.floor((vertex.longitude() + 180) / 360);
        return new Vertex(longitude == 0 ? 0 : longitude, vertex.latitude() == 0 ? 0 : vertex.latitude());
    }

    private record Radial(Vertex point, double bearing, double distance) { }
    private static IllegalArgumentException failure(String code) { return new IllegalArgumentException(code); }
}
