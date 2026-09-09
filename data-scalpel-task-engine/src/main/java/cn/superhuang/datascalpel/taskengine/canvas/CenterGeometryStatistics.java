package cn.superhuang.datascalpel.taskengine.canvas;

import org.locationtech.jts.geom.*;
import java.util.List;

/** Executor-local bounded-group numerical kernel. No Spark actions, I/O, or data-bearing errors. */
final class CenterGeometryStatistics {
    static final int MAX_GROUP_FEATURES = 100_000;
    static final int MAX_CENTRAL_FEATURES = 5_000;
    static final long MAX_GROUP_VERTICES = 1_000_000;
    static final int MAX_MEDIAN_ITERATIONS = 10_000;
    static final double MEDIAN_RELATIVE_GAP = 1e-10;
    record Observation(Geometry geometry, double weight, Object id, int order) { }
    record Position(double x, double y) { }
    private final List<Observation> observations;
    private final double[] x, y, w;
    private final double originX, originY, scale, totalWeight;
    private final Position mean;
    private final GeometryFactory factory;

    static Geometry checked(Geometry geometry) {
        if (geometry == null || geometry.isEmpty()) return null;
        if (!(geometry instanceof Point || geometry instanceof MultiPoint || geometry instanceof LineString
                || geometry instanceof MultiLineString || geometry instanceof Polygon || geometry instanceof MultiPolygon)
                || !geometry.isValid()) throw failure("SPATIAL_CENTER_GEOMETRY_INVALID");
        for (var c : geometry.getCoordinates()) if (!Double.isFinite(c.x) || !Double.isFinite(c.y)) throw failure("SPATIAL_CENTER_GEOMETRY_INVALID");
        Point centroid = geometry.getCentroid();
        if (centroid.isEmpty() || !Double.isFinite(centroid.getX()) || !Double.isFinite(centroid.getY())) throw failure("SPATIAL_CENTER_GEOMETRY_INVALID");
        return geometry;
    }

    CenterGeometryStatistics(List<Observation> observations, int srid) {
        if (observations.isEmpty() || observations.size() > MAX_GROUP_FEATURES) throw failure("SPATIAL_CENTER_GROUP_LIMIT_EXCEEDED");
        if (observations.stream().filter(o -> o.geometry() != null).mapToLong(o -> o.geometry().getNumPoints()).sum() > MAX_GROUP_VERTICES)
            throw failure("SPATIAL_CENTER_GROUP_LIMIT_EXCEEDED");
        this.observations = List.copyOf(observations);
        factory = new GeometryFactory(new PrecisionModel(), srid);
        int size = observations.size(); x = new double[size]; y = new double[size]; w = new double[size];
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY, minY = minX, maxY = maxX, maximumWeight = 0;
        for (int i = 0; i < size; i++) {
            var item = observations.get(i);
            Geometry geometry = checked(item.geometry());
            if (geometry == null) throw failure("SPATIAL_CENTER_GEOMETRY_INVALID");
            if (!Double.isFinite(item.weight()) || item.weight() < 0) throw failure("SPATIAL_CENTER_WEIGHT_INVALID");
            Point p = geometry.getCentroid(); x[i] = p.getX(); y[i] = p.getY(); w[i] = item.weight();
            // Zero-weight features remain central-feature candidates, but must not
            // set the numerical frame of statistics to which they contribute nothing.
            if (w[i] > 0) {
                minX = Math.min(minX, x[i]); maxX = Math.max(maxX, x[i]);
                minY = Math.min(minY, y[i]); maxY = Math.max(maxY, y[i]);
            }
            maximumWeight = Math.max(maximumWeight, w[i]);
        }
        originX = maximumWeight == 0 ? 0 : minX / 2 + maxX / 2;
        originY = maximumWeight == 0 ? 0 : minY / 2 + maxY / 2;
        scale = maximumWeight == 0 ? 1 : Math.max(1, Math.max(Math.max(Math.abs(maxX - originX), Math.abs(minX - originX)), Math.max(Math.abs(maxY - originY), Math.abs(minY - originY))));
        Sum sw = new Sum(), sx = new Sum(), sy = new Sum();
        for (int i = 0; i < size; i++) {
            x[i] = (x[i] - originX) / scale; y[i] = (y[i] - originY) / scale;
            w[i] = maximumWeight == 0 ? 0 : w[i] / maximumWeight;
            if (w[i] == 0) continue;
            sw.add(w[i]); sx.add(w[i] * x[i]); sy.add(w[i] * y[i]);
        }
        totalWeight = sw.value;
        mean = totalWeight == 0 ? null : new Position(sx.value / totalWeight, sy.value / totalWeight);
    }

    boolean hasWeight() { return totalWeight > 0; }
    Point mean() { return point(mean); }

    Point median() { return point(median(MAX_MEDIAN_ITERATIONS, MEDIAN_RELATIVE_GAP)); }
    Position median(int iterationLimit, double relativeGap) {
        Position current = mean;
        if (current == null) return null;
        for (int iteration = 0; iteration < iterationLimit; iteration++) {
            Sum denominator = new Sum(), deltaX = new Sum(), deltaY = new Sum(), coincident = new Sum();
            double radius = 0; int closest = -1; double closestDistance = Double.POSITIVE_INFINITY;
            for (int i = 0; i < x.length; i++) {
                if (w[i] == 0) continue;
                double dx = x[i] - current.x(), dy = y[i] - current.y(), distance = Math.hypot(dx, dy);
                radius = Math.max(radius, distance);
                if (distance < closestDistance) { closest = i; closestDistance = distance; }
                if (distance == 0) coincident.add(w[i]);
                else { denominator.add(w[i] / distance); deltaX.add(w[i] * dx / distance); deltaY.add(w[i] * dy / distance); }
            }
            double norm = Math.hypot(deltaX.value, deltaY.value);
            // Convexity bounds objective suboptimality by residual subgradient * hull radius.
            if (Math.max(0, norm - coincident.value) * radius <= relativeGap * totalWeight) return current;
            if (closest >= 0 && closestDistance < 1e-7 && isMedianVertex(closest)) return new Position(x[closest], y[closest]);
            if (!(denominator.value > 0) || !Double.isFinite(denominator.value)) throw failure("SPATIAL_CENTER_MEDIAN_NOT_CONVERGED");
            double factor = Math.max(0, 1 - coincident.value / norm) / denominator.value;
            Position next = new Position(current.x() + factor * deltaX.value, current.y() + factor * deltaY.value);
            if (!Double.isFinite(next.x()) || !Double.isFinite(next.y())) throw failure("SPATIAL_CENTER_NUMERIC_INVALID");
            current = next;
        }
        throw failure("SPATIAL_CENTER_MEDIAN_NOT_CONVERGED");
    }

    private boolean isMedianVertex(int vertex) {
        Sum rx = new Sum(), ry = new Sum(), coincident = new Sum();
        for (int i = 0; i < x.length; i++) {
            if (w[i] == 0) continue;
            double dx = x[i] - x[vertex], dy = y[i] - y[vertex], distance = Math.hypot(dx, dy);
            if (distance == 0) coincident.add(w[i]);
            else { rx.add(w[i] * dx / distance); ry.add(w[i] * dy / distance); }
        }
        return Math.hypot(rx.value, ry.value) <= coincident.value;
    }

    Observation central() {
        if (observations.size() > MAX_CENTRAL_FEATURES) throw failure("SPATIAL_CENTER_GROUP_LIMIT_EXCEEDED");
        int selected = -1; double best = Double.POSITIVE_INFINITY;
        for (int candidate = 0; candidate < x.length; candidate++) {
            // Positive-weight candidates lie in the normalized support box and have
            // finite scores. An unrepresentably distant zero-weight candidate cannot
            // beat them; in particular Infinity must never enter the ULP tie test.
            if (!Double.isFinite(x[candidate]) || !Double.isFinite(y[candidate])) continue;
            Sum score = new Sum();
            for (int i = 0; i < x.length; i++) {
                if (w[i] == 0) continue;
                score.add(w[i] * Math.hypot(x[i] - x[candidate], y[i] - y[candidate]));
                if (!Double.isFinite(score.value)) break;
            }
            if (!Double.isFinite(score.value)) continue;
            boolean tie = selected >= 0 && Math.abs(score.value - best) <= 8 * Math.ulp(Math.max(Math.abs(best), Math.abs(score.value)));
            if (selected < 0 || !tie && score.value < best || tie && observations.get(candidate).order() < observations.get(selected).order()) {
                selected = candidate; best = score.value;
            }
        }
        if (selected < 0) throw failure("SPATIAL_CENTER_NUMERIC_INVALID");
        return observations.get(selected);
    }

    Geometry dispersion(int deviations, boolean ellipse) {
        Sum vx = new Sum(), vy = new Sum(), covariance = new Sum();
        for (int i = 0; i < x.length; i++) {
            if (w[i] == 0) continue;
            double dx = x[i] - mean.x(), dy = y[i] - mean.y();
            vx.add(w[i] * dx * dx); vy.add(w[i] * dy * dy); covariance.add(w[i] * dx * dy);
        }
        double a = vx.value / totalWeight, b = vy.value / totalWeight, c = covariance.value / totalWeight;
        double root = Math.hypot(a - b, 2 * c);
        double major = Math.sqrt(Math.max(0, ellipse ? a + b + root : a + b)) * deviations;
        double minor = Math.sqrt(Math.max(0, ellipse ? a + b - root : a + b)) * deviations;
        double angle = ellipse ? .5 * Math.atan2(2 * c, a - b) : 0;
        if (major == 0 || minor == 0) return factory.createPolygon();
        Coordinate[] coordinates = new Coordinate[129];
        for (int i = 0; i < 128; i++) {
            double t = 2 * Math.PI * i / 128, px = major * Math.cos(t), py = minor * Math.sin(t);
            coordinates[i] = coordinate(new Position(mean.x() + px * Math.cos(angle) - py * Math.sin(angle), mean.y() + px * Math.sin(angle) + py * Math.cos(angle)));
        }
        coordinates[128] = coordinates[0].copy();
        Geometry polygon = factory.createPolygon(coordinates);
        if (!polygon.isValid()) throw failure("SPATIAL_CENTER_NUMERIC_INVALID");
        return polygon;
    }

    private Point point(Position value) { return value == null ? null : factory.createPoint(coordinate(value)); }
    private Coordinate coordinate(Position p) {
        double px = originX + p.x() * scale, py = originY + p.y() * scale;
        if (!Double.isFinite(px) || !Double.isFinite(py)) throw failure("SPATIAL_CENTER_NUMERIC_INVALID");
        return new CoordinateXY(px, py);
    }
    private static IllegalArgumentException failure(String code) { return new IllegalArgumentException(code); }
    private static final class Sum {
        private double value, compensation;
        private void add(double next) { double adjusted = next - compensation, total = value + adjusted; compensation = (total - value) - adjusted; value = total; }
    }
}
