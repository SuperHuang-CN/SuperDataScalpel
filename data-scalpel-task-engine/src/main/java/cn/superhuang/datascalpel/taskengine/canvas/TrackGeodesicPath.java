package cn.superhuang.datascalpel.taskengine.canvas;

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;
import net.sf.geographiclib.GeodesicLine;
import net.sf.geographiclib.GeodesicMask;
import org.locationtech.jts.geom.*;

import java.util.ArrayList;
import java.util.List;

/** WGS84 inverse geodesics sampled in metres, with geodesic (not linear-latitude) dateline cuts. */
final class TrackGeodesicPath {
    static final int MAX_VERTICES = 1_000_000;
    private static final int POSITION_MASK = GeodesicMask.STANDARD | GeodesicMask.LONG_UNROLL;
    private TrackGeodesicPath() { }

    static Geometry build(Geometry geometry, double stepMetres) {
        if (!Double.isFinite(stepMetres) || stepMetres <= 0) throw new IllegalArgumentException("INVALID_TRACK_GEODESIC_SEGMENT_LENGTH");
        if (geometry == null) return null;
        if (!(geometry instanceof LineString line)) throw new IllegalArgumentException("TRACK_GEODESIC_COORDINATE_INVALID");
        if (line.getNumPoints() > MAX_VERTICES) throw capacityError();
        Coordinate[] points = line.getCoordinates();
        for (Coordinate point : points) {
            if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || Math.abs(point.x) > 180 || Math.abs(point.y) > 90)
                throw new IllegalArgumentException("TRACK_GEODESIC_COORDINATE_INVALID");
        }
        Parts result = new Parts(line.getFactory());
        for (int i = 1; i < points.length; i++) edge(points[i - 1], points[i], stepMetres, result);
        return result.geometry();
    }

    private static void edge(Coordinate a, Coordinate b, double stepMetres, Parts output) {
        // Longitude at a pole is arbitrary. Use the outgoing meridian so the first
        // rendered segment does not become a slanted longitude/latitude chord.
        double startLongitude = Math.abs(a.y) == 90 && Math.abs(b.y) != 90 ? b.x : a.x;
        GeodesicLine geodesic = Geodesic.WGS84.InverseLine(a.y, startLongitude, b.y, b.x);
        double distance = geodesic.Distance();
        if (!Double.isFinite(distance)) throw new IllegalArgumentException("TRACK_GEODESIC_COORDINATE_INVALID");
        if (distance == 0) {
            // Repeated observations remain observations, including equivalent +180/-180 positions.
            Coordinate same = new CoordinateXY(normalize(a.x), a.y);
            output.add(List.of(same, same.copy()));
            return;
        }
        double required = Math.ceil(distance / stepMetres);
        if (!Double.isFinite(required) || required + 1 > MAX_VERTICES) throw capacityError();
        int steps = Math.max(1, (int) required);
        List<Coordinate> part = new ArrayList<>();
        part.add(new CoordinateXY(normalize(startLongitude), a.y));
        double previousDistance = 0;
        double previousLongitude = startLongitude;
        for (int i = 1; i <= steps; i++) {
            double currentDistance = distance * ((double) i / steps);
            GeodesicData position = geodesic.Position(currentDistance, POSITION_MASK);
            // GeographicLib may choose the opposite pole longitude at the endpoint.
            // Retain the incoming meridian; both longitudes denote the same physical pole.
            double longitude = i == steps && Math.abs(b.y) == 90 ? previousLongitude : stabilizeDateline(position.lon2);
            double latitude = i == steps ? b.y : position.lat2;
            if (!Double.isFinite(longitude) || !Double.isFinite(latitude)) throw new IllegalArgumentException("TRACK_GEODESIC_COORDINATE_INVALID");
            int region = region(previousLongitude);
            int nextRegion = region(longitude);
            while (region != nextRegion) {
                boolean east = nextRegion > region;
                double boundary = east ? 180d + region * 360d : -180d + region * 360d;
                double crossingLatitude = crossingLatitude(geodesic, previousDistance, currentDistance, boundary, east);
                append(part, new CoordinateXY(east ? 180 : -180, crossingLatitude));
                output.add(part);
                part = new ArrayList<>();
                part.add(new CoordinateXY(east ? -180 : 180, crossingLatitude));
                region += east ? 1 : -1;
            }
            append(part, new CoordinateXY(longitude - nextRegion * 360d, latitude));
            previousDistance = currentDistance;
            previousLongitude = longitude;
        }
        output.add(part);
    }

    private static double crossingLatitude(GeodesicLine line, double low, double high, double longitude, boolean east) {
        var start = line.Position(low, POSITION_MASK);
        var end = line.Position(high, POSITION_MASK);
        if (Math.abs(start.lon2 - longitude) < 1e-12) return start.lat2;
        if (Math.abs(end.lon2 - longitude) < 1e-12) return end.lat2;
        for (int i = 0; i < 60; i++) {
            double middle = low + (high - low) / 2;
            double current = line.Position(middle, POSITION_MASK).lon2;
            if (east ? current < longitude : current > longitude) low = middle;
            else high = middle;
        }
        return line.Position(low + (high - low) / 2, POSITION_MASK).lat2;
    }

    private static int region(double longitude) { return (int) Math.floor((longitude + 180d) / 360d); }
    private static double stabilizeDateline(double longitude) {
        // GeographicLib may return 180 - one ULP along a polar meridian. Adding 180 in
        // region() can round to 360, producing a longitude just outside [-180,180].
        double boundary = 180d + 360d * Math.rint((longitude - 180d) / 360d);
        return Math.abs(longitude - boundary) < 1e-12 ? boundary : longitude;
    }
    private static double normalize(double longitude) { return longitude - 360d * region(longitude); }
    private static IllegalArgumentException capacityError() { return new IllegalArgumentException("TRACK_GEODESIC_VERTEX_LIMIT_EXCEEDED"); }

    private static void append(List<Coordinate> points, Coordinate point) {
        if (!points.isEmpty() && points.getLast().equals2D(point)) return;
        if (points.size() >= MAX_VERTICES) throw capacityError();
        points.add(point);
    }

    private static final class Parts {
        private final GeometryFactory factory;
        private final List<List<Coordinate>> parts = new ArrayList<>();
        private int vertices;

        private Parts(GeometryFactory factory) { this.factory = factory; }

        private void add(List<Coordinate> part) {
            if (part.size() < 2) return;
            int start = 0;
            List<Coordinate> target;
            if (!parts.isEmpty() && parts.getLast().getLast().equals2D(part.getFirst(), 1e-10)) {
                target = parts.getLast(); start = 1;
            } else {
                target = new ArrayList<>(); parts.add(target);
            }
            if ((long) vertices + part.size() - start > MAX_VERTICES) throw capacityError();
            for (int i = start; i < part.size(); i++) target.add(part.get(i));
            vertices += part.size() - start;
        }

        private MultiLineString geometry() {
            LineString[] lines = parts.stream().map(p -> factory.createLineString(p.toArray(Coordinate[]::new))).toArray(LineString[]::new);
            MultiLineString geometry = factory.createMultiLineString(lines);
            geometry.setSRID(4326);
            return geometry;
        }
    }
}
