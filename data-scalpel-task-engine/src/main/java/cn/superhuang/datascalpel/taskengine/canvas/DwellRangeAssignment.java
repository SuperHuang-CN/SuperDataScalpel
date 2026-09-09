package cn.superhuang.datascalpel.taskengine.canvas;

import org.apache.spark.api.java.function.MapPartitionsFunction;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.sedona.common.sphere.Spheroid;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/** Executor-local, one sorted track segment at a time. No driver collection or Spark action. */
final class DwellRangeAssignment implements MapPartitionsFunction<Row, Row> {
    private final int[] keyIndices;
    private final int geometryIndex;
    private final int timeIndex;
    private final double tolerance;
    private final double minimumMillis;
    private final boolean geodesic;

    DwellRangeAssignment(int[] keyIndices, int geometryIndex, int timeIndex,
                         double tolerance, double minimumMillis, boolean geodesic) {
        this.keyIndices = keyIndices.clone();
        this.geometryIndex = geometryIndex;
        this.timeIndex = timeIndex;
        this.tolerance = tolerance;
        this.minimumMillis = minimumMillis;
        this.geodesic = geodesic;
    }

    @Override
    public Iterator<Row> call(Iterator<Row> source) {
        return new Iterator<>() {
            private Row pending;
            private Iterator<Row> output = List.<Row>of().iterator();

            @Override public boolean hasNext() {
                if (output.hasNext()) return true;
                if (pending == null && source.hasNext()) pending = source.next();
                if (pending == null) return false;
                List<Row> segment = new ArrayList<>();
                Object[] keys = keys(pending);
                segment.add(pending);
                pending = null;
                while (source.hasNext()) {
                    Row next = source.next();
                    if (!Arrays.deepEquals(keys, keys(next))) { pending = next; break; }
                    segment.add(next);
                }
                output = assign(segment).iterator();
                return output.hasNext();
            }

            @Override public Row next() {
                if (!hasNext()) throw new NoSuchElementException();
                return output.next();
            }
        };
    }

    private Object[] keys(Row row) {
        Object[] values = new Object[keyIndices.length];
        for (int index = 0; index < keyIndices.length; index++) values[index] = row.get(keyIndices[index]);
        return values;
    }

    List<Row> assign(List<Row> rows) {
        Point[] points = new Point[rows.size()];
        for (int index = 0; index < rows.size(); index++) {
            Geometry value = rows.get(index).isNullAt(geometryIndex) ? null : (Geometry) rows.get(index).get(geometryIndex);
            if (value == null || value.isEmpty()) continue;
            if (!(value instanceof Point point) || !Double.isFinite(point.getX()) || !Double.isFinite(point.getY())
                    || geodesic && (Math.abs(point.getX()) > 180 || Math.abs(point.getY()) > 90)) {
                throw new IllegalArgumentException("TRACK_DWELL_POINT_INVALID");
            }
            points[index] = point;
        }
        Long[] assignment = new Long[rows.size()];
        long sequence = 0;
        int unassignedStart = 0;
        for (int start = 0; start < rows.size();) {
            if (points[start] == null) { unassignedStart = ++start; continue; }
            int end = start;
            while (end + 1 < rows.size() && points[end + 1] != null
                    && distance(points[start], points[end + 1], geodesic) <= tolerance) end++;
            if (end == start || elapsedMillis(rows.get(start).getTimestamp(timeIndex), rows.get(end).getTimestamp(timeIndex)) < minimumMillis) {
                start++;
                continue;
            }
            // Freeze the seed mean while extending. Recentring after every point could create another drifting chain.
            Point center = meanCenter(Arrays.copyOfRange(points, start, end + 1), geodesic);
            int first = start;
            while (first > unassignedStart && points[first - 1] != null
                    && distance(center, points[first - 1], geodesic) <= tolerance) first--;
            while (end + 1 < rows.size() && points[end + 1] != null
                    && distance(center, points[end + 1], geodesic) <= tolerance) end++;
            sequence++;
            for (int index = first; index <= end; index++) assignment[index] = sequence;
            // Never reuse observations belonging to a previously emitted dwell.
            start = end + 1;
            unassignedStart = start;
        }
        // Emit each copied row on demand instead of keeping a second complete copy of the segment.
        return new AbstractList<>() {
            @Override public int size() { return rows.size(); }
            @Override public Row get(int index) {
                Row row = rows.get(index);
                Object[] fields = new Object[row.size() + 1];
                for (int field = 0; field < row.size(); field++) fields[field] = row.get(field);
                fields[row.size()] = assignment[index];
                return RowFactory.create(fields);
            }
        };
    }

    static double elapsedMillis(Timestamp start, Timestamp end) {
        var elapsed = java.time.Duration.between(start.toInstant(), end.toInstant());
        return elapsed.getSeconds() * 1000d + elapsed.getNano() / 1_000_000d;
    }

    static double distance(Point first, Point second, boolean geodesic) {
        return geodesic ? Spheroid.distance(first, second) : first.distance(second);
    }

    static Point meanCenter(Point[] points, boolean geodesic) {
        double x = 0, y = 0, z = 0;
        for (Point point : points) {
            if (geodesic) {
                double lon = Math.toRadians(point.getX()), lat = Math.toRadians(point.getY());
                x += Math.cos(lat) * Math.cos(lon);
                y += Math.cos(lat) * Math.sin(lon);
                z += Math.sin(lat);
            } else { x += point.getX(); y += point.getY(); }
        }
        if (geodesic && Math.sqrt(x * x + y * y + z * z) < 1e-12) {
            throw new IllegalArgumentException("TRACK_DWELL_CENTER_UNDEFINED");
        }
        Coordinate coordinate = geodesic
                ? new Coordinate(Math.toDegrees(Math.atan2(y, x)), Math.toDegrees(Math.atan2(z, Math.hypot(x, y))))
                : new Coordinate(x / points.length, y / points.length);
        GeometryFactory factory = points[0].getFactory();
        Point result = factory.createPoint(coordinate);
        result.setSRID(points[0].getSRID());
        return result;
    }

    static Point meanCenter(Geometry collection, boolean geodesic) {
        Point[] points = new Point[collection.getNumGeometries()];
        for (int index = 0; index < points.length; index++) points[index] = (Point) collection.getGeometryN(index);
        return meanCenter(points, geodesic);
    }
}
