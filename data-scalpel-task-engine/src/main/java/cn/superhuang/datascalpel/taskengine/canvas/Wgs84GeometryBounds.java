package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.ArcBox;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Position;
import net.sf.geographiclib.Constants;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * Conservative ECEF bounds used only to recall cross-table geodesic candidates. Bounds can
 * admit false positives but must never exclude a real Geometry point. Final distance and
 * ranking always use WGS84, never this planar envelope.
 */
final class Wgs84GeometryBounds {
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 0);
    private static final Position[] AXIS_EXTREMA = {
            new Position(0, 0), new Position(180, 0),
            new Position(90, 0), new Position(-90, 0),
            new Position(0, 90), new Position(0, -90)
    };

    private Wgs84GeometryBounds() { }

    record Bounds(Geometry xyEnvelope, double minZ, double maxZ) { }

    static Bounds of(Geometry geometry) {
        if (geometry == null || geometry.isEmpty()) return null;
        var prepared = Wgs84GeometryDistance.prepare(geometry);
        ArcBox box = null;
        for (var edge : prepared.edges()) box = union(box, Wgs84SegmentDistance.arcBox(edge));
        if (box == null) return null;

        if (prepared.region() != null) {
            // A Cartesian coordinate restricted to the ellipsoid can have an interior
            // extremum only at its positive/negative axis point. Boundary arc boxes plus
            // every contained (or numerically unresolved) axis point therefore cover the
            // complete filled region, including holes and multipart polygons.
            var budget = new Wgs84SegmentDistance.Budget(Wgs84SegmentDistance.MAX_EVALUATIONS);
            for (Position axis : AXIS_EXTREMA) {
                var location = prepared.region().locate(axis, budget);
                if (location != Wgs84PolygonRegion.Location.OUTSIDE) box = include(box, axis);
            }
        }

        Geometry xy = FACTORY.toGeometry(new Envelope(box.minX(), box.maxX(), box.minY(), box.maxY()));
        return new Bounds(xy, box.minZ(), box.maxZ());
    }

    private static ArcBox include(ArcBox box, Position position) {
        double latitude = Math.toRadians(position.latitude());
        double longitude = Math.toRadians(position.longitude());
        double eccentricitySquared = Constants.WGS84_f * (2 - Constants.WGS84_f);
        double sin = Math.sin(latitude), cos = Math.cos(latitude);
        double normal = Constants.WGS84_a / Math.sqrt(1 - eccentricitySquared * sin * sin);
        double x = normal * cos * Math.cos(longitude);
        double y = normal * cos * Math.sin(longitude);
        double z = normal * (1 - eccentricitySquared) * sin;
        return new ArcBox(Math.min(box.minX(), x), Math.max(box.maxX(), x),
                Math.min(box.minY(), y), Math.max(box.maxY(), y),
                Math.min(box.minZ(), z), Math.max(box.maxZ(), z));
    }

    private static ArcBox union(ArcBox a, ArcBox b) {
        if (a == null) return b;
        return new ArcBox(Math.min(a.minX(), b.minX()), Math.max(a.maxX(), b.maxX()),
                Math.min(a.minY(), b.minY()), Math.max(a.maxY(), b.maxY()),
                Math.min(a.minZ(), b.minZ()), Math.max(a.maxZ(), b.maxZ()));
    }
}
