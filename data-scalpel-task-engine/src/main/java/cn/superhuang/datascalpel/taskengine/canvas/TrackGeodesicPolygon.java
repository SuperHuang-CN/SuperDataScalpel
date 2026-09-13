package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.TrackGeodesicAreaBoundary.Vertex;
import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.PolygonArea;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.union.UnaryUnionOp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static cn.superhuang.datascalpel.taskengine.canvas.TrackGeodesicAreaGeometry.*;

/** Local geodesic polygon footprints: original region plus sampled normal-offset boundary strips and round joins. */
final class TrackGeodesicPolygon {
    private TrackGeodesicPolygon() { }

    record Source(Vertex reference,List<List<Vertex>> rings) {
        Source { rings=rings.stream().map(List::copyOf).toList(); }
    }

    /** Validate the original continuous region independently of sampling or Boolean rendering. */
    static Source prepareSource(Geometry shape) {
        return prepareSource(shape,new Wgs84SegmentDistance.Budget(Wgs84SegmentDistance.MAX_EVALUATIONS));
    }

    /** Reuse one caller-owned topology budget when several independent regions form one input geometry. */
    static Source prepareSource(Geometry shape,Wgs84SegmentDistance.Budget topologyBudget) {
        if (!(shape instanceof Polygon||shape instanceof MultiPolygon)||shape.isEmpty()) throw failure("TRACK_AREA_GEOMETRY_INVALID");
        if (topologyBudget==null) throw failure("INVALID_GEODESIC_DISTANCE_WORK_LIMIT");
        requireCoordinates(shape);
        var allRings = new ArrayList<List<Vertex>>();
        var shells = new ArrayList<List<Vertex>>();
        var holeCounts = new ArrayList<Integer>();
        for (int index = 0; index < shape.getNumGeometries(); index++) {
            Polygon polygon = (Polygon) shape.getGeometryN(index);
            if (polygon.isEmpty()) throw failure("TRACK_AREA_GEOMETRY_INVALID");
            var outer = ring(polygon.getExteriorRing()); shells.add(outer); allRings.add(outer);
            holeCounts.add(polygon.getNumInteriorRing());
            for (int hole = 0; hole < polygon.getNumInteriorRing(); hole++) allRings.add(ring(polygon.getInteriorRingN(hole)));
        }
        Vertex reference = reference(shells);
        for (var ring : allRings) requireLocal(reference, ring);
        List<List<Vertex>> nodedRings = Wgs84PolygonArcCheck.validate(allRings,reference,topologyBudget);
        Wgs84PolygonTopology.validate(nodedRings,holeCounts,topologyBudget);
        return new Source(reference,nodedRings);
    }

    static Footprint footprint(Geometry shape, double radius, double step) {
        if (radius >= TrackGeodesicDisk.MAX_RADIUS_EXCLUSIVE) throw failure("TRACK_GEODESIC_BUFFER_RANGE_NOT_SUPPORTED");
        Source source=prepareSource(shape);
        Vertex reference=source.reference();
        List<List<Vertex>> allRings=source.rings();
        Budget budget = new Budget();
        var boundaries = new ArrayList<TrackGeodesicAreaBoundary>();
        for (var ring : allRings) boundaries.add(boundary(ring, step, budget));
        var hullVertices = new ArrayList<Vertex>();
        var regions = new ArrayList<Geometry>();
        int ringIndex = 0;
        for (int index = 0; index < shape.getNumGeometries(); index++) {
            Polygon polygon = (Polygon) shape.getGeometryN(index);
            var exterior = boundaries.get(ringIndex++);
            var shell = exterior.render();
            hullVertices.addAll(exterior.boundary());
            var holes = new ArrayList<Geometry>();
            for (int hole = 0; hole < polygon.getNumInteriorRing(); hole++) {
                var interior = boundaries.get(ringIndex++);
                Geometry area = interior.render();
                if (!shell.covers(area)) throw failure("TRACK_AREA_GEOMETRY_INVALID");
                holes.add(area);
            }
            Geometry region = holes.isEmpty() ? shell : shell.difference(UnaryUnionOp.union(holes, FACTORY));
            if (region.isEmpty() || !region.isValid()) throw failure("TRACK_AREA_GEOMETRY_INVALID");
            regions.add(region);
        }
        var pieces = new ArrayList<>(regions);
        if (radius > 0) for (var ring : allRings) {
            for (int i = 0; i < ring.size() - 1; i++) {
                Vertex a = ring.get(i), b = ring.get(i + 1);
                var circle = TrackGeodesicDisk.sample(FACTORY.createPoint(a.coordinate()), radius, step);
                budget.add(circle.boundary().size()); requireLocal(reference, circle.boundary());
                hullVertices.addAll(circle.boundary()); pieces.add(circle.render());
                var strip = offsetStrip(a, b, radius, step, reference, budget);
                if (strip != null) { hullVertices.addAll(strip.boundary()); pieces.add(strip.render()); }
            }
        }
        MultiPolygon area = multi(UnaryUnionOp.union(pieces, FACTORY)); budget.add(area.getNumPoints());
        if (hullVertices.size() > MAX_VERTICES) throw failure("TRACK_AREA_VERTEX_LIMIT_EXCEEDED");
        return new Footprint(reference, hullVertices, area);
    }

    private static List<Vertex> ring(LineString line) {
        var vertices = new ArrayList<Vertex>();
        for (Coordinate coordinate : line.getCoordinates()) {
            Vertex next = vertex(coordinate);
            if (vertices.isEmpty() || !vertices.getLast().equals(next)) vertices.add(next);
        }
        if (vertices.size() < 4 || !vertices.getFirst().equals(vertices.getLast())) throw failure("TRACK_AREA_GEOMETRY_INVALID");
        return clockwise(vertices);
    }

    private static List<Vertex> clockwise(List<Vertex> vertices) {
        PolygonArea polygon = new PolygonArea(Geodesic.WGS84, false);
        vertices.forEach(vertex -> polygon.AddPoint(vertex.latitude(), vertex.longitude()));
        double area = polygon.Compute(false, true).area;
        if (!Double.isFinite(area) || area == 0) throw failure("TRACK_AREA_GEOMETRY_INVALID");
        if (area > 0) Collections.reverse(vertices);
        return vertices;
    }

    private static Vertex reference(List<List<Vertex>> shells) {
        double x = 0, y = 0, z = 0;
        int count = 0;
        for (var ring : shells) for (int i = 0; i < ring.size() - 1; i++) {
            Vertex vertex = ring.get(i);
            double latitude = Math.toRadians(vertex.latitude()), longitude = Math.toRadians(vertex.longitude());
            x += Math.cos(latitude) * Math.cos(longitude); y += Math.cos(latitude) * Math.sin(longitude); z += Math.sin(latitude); count++;
        }
        if (Math.hypot(Math.hypot(x, y), z) <= count * 1e-12) throw failure("TRACK_GEODESIC_HULL_RANGE_NOT_SUPPORTED");
        // This reference only selects a verifiable local domain. It is never used in place
        // of the polygon for buffering, intersections, track splitting or statistics.
        return new Vertex(Math.toDegrees(Math.atan2(y, x)), Math.toDegrees(Math.atan2(z, Math.hypot(x, y))));
    }

    private static TrackGeodesicAreaBoundary boundary(List<Vertex> ring, double step, Budget budget) {
        var boundary = TrackGeodesicAreaBoundary.fromClockwiseRing(ring, step);
        budget.add(boundary.boundary().size()); return boundary;
    }

    private static TrackGeodesicAreaBoundary offsetStrip(Vertex a, Vertex b, double radius, double step, Vertex reference, Budget budget) {
        var line = Geodesic.WGS84.InverseLine(a.latitude(), a.longitude(), b.latitude(), b.longitude());
        if (line.Distance() == 0) return null;
        double required = Math.ceil(line.Distance() / step);
        if (!Double.isFinite(required) || required * 2 + 3 > budget.remaining()) throw failure("TRACK_AREA_VERTEX_LIMIT_EXCEEDED");
        int samples = Math.max(1, (int) required);
        var left = new ArrayList<Vertex>(samples + 1); var right = new ArrayList<Vertex>(samples + 1);
        for (int i = 0; i <= samples; i++) {
            var position = line.Position(line.Distance() * i / samples);
            var l = Geodesic.WGS84.Direct(position.lat2, position.lon2, position.azi2 - 90, radius);
            var r = Geodesic.WGS84.Direct(position.lat2, position.lon2, position.azi2 + 90, radius);
            left.add(new Vertex(l.lon2, l.lat2)); right.add(new Vertex(r.lon2, r.lat2));
        }
        Collections.reverse(right); left.addAll(right); left.add(left.getFirst());
        requireLocal(reference, left);
        return boundary(clockwise(left), step, budget);
    }

    private static class Budget {
        private long used;
        long remaining() { return MAX_VERTICES - used; }
        void add(long count) { used += count; if (used > MAX_VERTICES) throw failure("TRACK_AREA_VERTEX_LIMIT_EXCEEDED"); }
    }
}
