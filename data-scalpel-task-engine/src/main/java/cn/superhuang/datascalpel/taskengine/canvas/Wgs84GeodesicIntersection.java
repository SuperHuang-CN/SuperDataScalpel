package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Arc;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Budget;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Position;
import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.Gnomonic;
import net.sf.geographiclib.GnomonicData;

/** Numerically verified common position for two arcs already proven to cross in one local domain. */
final class Wgs84GeodesicIntersection {
    private static final Gnomonic PROJECTION = new Gnomonic(Geodesic.WGS84);
    private static final int MAX_ITERATIONS = 16;
    private static final double CONVERGENCE_METRES = 1e-7;
    private static final double MAX_PROJECTED_RESIDUAL_METRES = 1e-5;

    private Wgs84GeodesicIntersection() { }

    static Position crossing(Arc first, Arc second, Budget budget) {
        if (first == null || second == null || budget == null) return null;
        Position center = mean(first.start(), first.end(), second.start(), second.end());
        if (center == null) return null;

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            Projected projected = project(center, first, second, budget);
            if (projected == null) return null;
            Homogeneous intersection = projected.first().cross(projected.second());
            if (!intersection.finite() || Math.abs(intersection.z()) <= Math.ulp(1d)) return null;
            double x = intersection.x() / intersection.z();
            double y = intersection.y() / intersection.z();
            if (!Double.isFinite(x) || !Double.isFinite(y)) return null;
            budget.consume();
            GnomonicData reverse = PROJECTION.Reverse(center.latitude(), center.longitude(), x, y);
            Position next = position(reverse);
            if (next == null) return null;
            budget.consume();
            double movement = Geodesic.WGS84.Inverse(center.latitude(), center.longitude(),
                    next.latitude(), next.longitude()).s12;
            if (!Double.isFinite(movement)) return null;
            center = next;
            if (movement <= CONVERGENCE_METRES) {
                Projected verification = project(center, first, second, budget);
                if (verification != null
                        && verification.first().distanceFromOrigin() <= MAX_PROJECTED_RESIDUAL_METRES
                        && verification.second().distanceFromOrigin() <= MAX_PROJECTED_RESIDUAL_METRES) {
                    return center;
                }
                return null;
            }
        }
        return null;
    }

    private static Projected project(Position center, Arc first, Arc second, Budget budget) {
        Homogeneous a = point(center, first.start(), budget);
        Homogeneous b = point(center, first.end(), budget);
        Homogeneous c = point(center, second.start(), budget);
        Homogeneous d = point(center, second.end(), budget);
        if (a == null || b == null || c == null || d == null) return null;
        Homogeneous left = a.cross(b), right = c.cross(d);
        if (!left.finite() || !right.finite() || left.xyNorm() == 0 || right.xyNorm() == 0) return null;
        return new Projected(left, right);
    }

    private static Homogeneous point(Position center, Position point, Budget budget) {
        budget.consume();
        GnomonicData value = PROJECTION.Forward(center.latitude(), center.longitude(),
                point.latitude(), point.longitude());
        if (value == null || !Double.isFinite(value.x) || !Double.isFinite(value.y)
                || !Double.isFinite(value.rk) || value.rk <= 0) return null;
        return new Homogeneous(value.x, value.y, 1);
    }

    private static Position position(GnomonicData value) {
        if (value == null || !Double.isFinite(value.lon) || !Double.isFinite(value.lat)
                || Math.abs(value.lon) > 180 || Math.abs(value.lat) > 90) return null;
        double longitude = Math.abs(value.lat) == 90 ? 0 : value.lon == 180 ? -180 : value.lon;
        return new Position(longitude == 0 ? 0 : longitude, value.lat == 0 ? 0 : value.lat);
    }

    private static Position mean(Position... positions) {
        double x = 0, y = 0, z = 0;
        for (Position point : positions) {
            if (point == null || !Double.isFinite(point.longitude()) || !Double.isFinite(point.latitude())
                    || Math.abs(point.longitude()) > 180 || Math.abs(point.latitude()) > 90) return null;
            double latitude = Math.toRadians(point.latitude());
            double longitude = Math.toRadians(point.longitude());
            x += Math.cos(latitude) * Math.cos(longitude);
            y += Math.cos(latitude) * Math.sin(longitude);
            z += Math.sin(latitude);
        }
        double norm = Math.hypot(Math.hypot(x, y), z);
        if (!Double.isFinite(norm) || norm <= 1e-12) return null;
        return new Position(Math.toDegrees(Math.atan2(y, x)),
                Math.toDegrees(Math.atan2(z, Math.hypot(x, y))));
    }

    private record Projected(Homogeneous first, Homogeneous second) { }

    private record Homogeneous(double x, double y, double z) {
        Homogeneous cross(Homogeneous other) {
            return new Homogeneous(y * other.z - z * other.y,
                    z * other.x - x * other.z,
                    x * other.y - y * other.x);
        }

        boolean finite() {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
        }

        double xyNorm() {
            return Math.hypot(x, y);
        }

        double distanceFromOrigin() {
            double norm = xyNorm();
            return norm == 0 ? Double.POSITIVE_INFINITY : Math.abs(z) / norm;
        }
    }
}
