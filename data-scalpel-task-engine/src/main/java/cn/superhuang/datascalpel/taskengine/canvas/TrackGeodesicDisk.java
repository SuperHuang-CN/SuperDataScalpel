package cn.superhuang.datascalpel.taskengine.canvas;

import net.sf.geographiclib.Geodesic;
import org.locationtech.jts.geom.*;
import cn.superhuang.datascalpel.taskengine.canvas.TrackGeodesicAreaBoundary.Vertex;

import java.util.ArrayList;
import java.util.List;

/**
 * WGS84 point-footprint primitive for area tracks. Sampling vertices and rendering seams are
 * deliberately separate: cuts must not become track observations, and artificial pole-closure
 * edges must not become input to the future adjacent-footprint hull. Meridian intersections
 * in boundary are genuine circle points, solved on the radius curve rather than interpolated.
 * Not yet enabled by TrackAreaPlan; polygon buffering and geodesic connections remain required.
 */
final class TrackGeodesicDisk {
    private static final int MAX_VERTICES = 1_000_000;
    // This primitive handles minor disks only. It must not silently select a complement at a cut locus.
    static final double MAX_RADIUS_EXCLUSIVE = Geodesic.WGS84.Inverse(0, 0, 90, 0).s12;

    private TrackGeodesicDisk() { }

    static TrackGeodesicAreaBoundary sample(Point point, Double radiusMetres, double maximumArcLengthMetres) {
        if (point == null || point.isEmpty() || point.getSRID() != 4326)
            throw failure("TRACK_GEODESIC_COORDINATE_INVALID");
        Coordinate center = point.getCoordinate();
        if (!Double.isFinite(center.x) || !Double.isFinite(center.y)
                || Math.abs(center.x) > 180 || Math.abs(center.y) > 90
                || !Double.isNaN(center.getZ()) || !Double.isNaN(center.getM()))
            throw failure("TRACK_GEODESIC_COORDINATE_INVALID");
        if (radiusMetres == null || !Double.isFinite(radiusMetres) || radiusMetres <= 0)
            throw failure("TRACK_BUFFER_DISTANCE_INVALID");
        if (radiusMetres >= MAX_RADIUS_EXCLUSIVE)
            throw failure("TRACK_GEODESIC_BUFFER_RANGE_NOT_SUPPORTED");
        if (!Double.isFinite(maximumArcLengthMetres) || maximumArcLengthMetres <= 0)
            throw failure("INVALID_TRACK_GEODESIC_SEGMENT_LENGTH");
        // A geodesic circle's arc is no longer than radius * azimuth on this positive-curvature domain.
        // 64 directions is a minimum discretization, not a positional-error guarantee.
        double requested = Math.max(64, 4 * Math.ceil((2 * Math.PI * radiusMetres / maximumArcLengthMetres) / 4));
        if (!Double.isFinite(requested) || requested > MAX_VERTICES - 16)
            throw failure("TRACK_AREA_VERTEX_LIMIT_EXCEEDED");
        int samples = (int) requested;
        double latitude = center.y;
        double longitude = Math.abs(latitude) == 90 ? 0 : center.x == 180 ? -180 : center.x;
        double step = 360d / samples;
        // Avoid placing a sample exactly on a pole when a circle is tangent to it. That pole's
        // longitude is undefined; midpoint azimuth sampling retains an unambiguous cyclic ring.
        double startAzimuth = step / 2;
        List<Vertex> ring = new ArrayList<>(samples + 8);
        var initial = position(latitude, longitude, radiusMetres, startAzimuth);
        add(ring, initial);
        for (int i = 1; i <= samples; i++) {
            double azimuth = startAzimuth + i * step;
            Vertex previous = ring.getLast();
            var raw = position(latitude, longitude, radiusMetres, azimuth);
            var current = new Vertex(unwrap(raw.longitude(), previous.longitude()), raw.latitude());
            int oldSlab = slab(previous.longitude()), newSlab = slab(current.longitude());
            if (oldSlab != newSlab) {
                double cut = current.longitude() > previous.longitude()
                        ? 180 + 360d * oldSlab : -180 + 360d * oldSlab;
                if (cut != previous.longitude() && cut != current.longitude()) {
                    double low = azimuth - step, high = azimuth;
                    boolean increasing = current.longitude() > previous.longitude();
                    for (int iteration = 0; iteration < 60; iteration++) {
                        double mid = (low + high) / 2;
                        var candidate = position(latitude, longitude, radiusMetres, mid);
                        double unwrapped = unwrap(candidate.longitude(), previous.longitude());
                        if ((unwrapped < cut) == increasing) low = mid; else high = mid;
                    }
                    var crossing = position(latitude, longitude, radiusMetres, (low + high) / 2);
                    add(ring, new Vertex(cut, crossing.latitude()));
                }
            }
            add(ring, current);
        }
        double winding = 360 * Math.rint((ring.getLast().longitude() - initial.longitude()) / 360);
        if (Math.abs(winding) > 360) throw failure("TRACK_AREA_GEOMETRY_INVALID");
        ring.set(ring.size() - 1, new Vertex(initial.longitude() + winding, initial.latitude()));
        Integer pole = null;
        if (winding != 0) {
            boolean north = radiusMetres > Geodesic.WGS84.Inverse(latitude, longitude, 90, 0).s12;
            boolean south = radiusMetres > Geodesic.WGS84.Inverse(latitude, longitude, -90, 0).s12;
            if (north == south) throw failure("TRACK_AREA_GEOMETRY_INVALID");
            pole = north ? 90 : -90;
        }
        return new TrackGeodesicAreaBoundary(ring, pole);
    }

    private static Vertex position(double latitude, double longitude, double radius, double azimuth) {
        var value = Geodesic.WGS84.Direct(latitude, longitude, azimuth, radius);
        if (!Double.isFinite(value.lon2) || !Double.isFinite(value.lat2))
            throw failure("TRACK_AREA_GEOMETRY_INVALID");
        return new Vertex(value.lon2, value.lat2);
    }

    private static double unwrap(double longitude, double reference) {
        return longitude + 360 * Math.rint((reference - longitude) / 360);
    }

    private static int slab(double longitude) { return (int) Math.floor((longitude + 180) / 360); }

    private static void add(List<Vertex> vertices, Vertex point) {
        if (vertices.size() >= MAX_VERTICES - 4) throw failure("TRACK_AREA_VERTEX_LIMIT_EXCEEDED");
        vertices.add(point);
    }

    private static IllegalArgumentException failure(String code) { return new IllegalArgumentException(code); }
}
