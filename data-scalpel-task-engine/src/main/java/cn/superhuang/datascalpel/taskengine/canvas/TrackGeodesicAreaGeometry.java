package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.TrackGeodesicAreaBoundary.Vertex;
import net.sf.geographiclib.Geodesic;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.union.UnaryUnionOp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Per-observation WGS84 footprints and adjacent-only area-track assembly. */
final class TrackGeodesicAreaGeometry {
    static final int MAX_VERTICES = 1_000_000;
    static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final Set<String> SAFE_CODES = Set.of("TRACK_AREA_GEOMETRY_INVALID", "TRACK_AREA_VERTEX_LIMIT_EXCEEDED",
            "TRACK_BUFFER_DISTANCE_INVALID", "TRACK_GEODESIC_COORDINATE_INVALID", "INVALID_TRACK_GEODESIC_SEGMENT_LENGTH",
            "TRACK_GEODESIC_VERTEX_LIMIT_EXCEEDED", "TRACK_GEODESIC_BUFFER_RANGE_NOT_SUPPORTED",
            "TRACK_GEODESIC_HULL_INVALID", "TRACK_GEODESIC_HULL_RANGE_NOT_SUPPORTED", "TRACK_GEODESIC_HULL_WORK_LIMIT_EXCEEDED",
            "GEODESIC_TOPOLOGY_PRECISION_NOT_REACHED", "GEODESIC_DISTANCE_WORK_LIMIT_EXCEEDED");

    private TrackGeodesicAreaGeometry() { }

    record Footprint(Vertex reference, List<Vertex> hullVertices, MultiPolygon area) {
        Footprint {
            hullVertices = List.copyOf(hullVertices);
            area = (MultiPolygon) area.copy();
        }
        @Override public MultiPolygon area() { return (MultiPolygon) area.copy(); }
    }

    static Footprint footprint(Geometry shape, Double radiusMetres, boolean buffer, double maximumSegmentLength) {
        if (shape == null || shape.isEmpty()) return null;
        try {
            if (!(shape instanceof Point || shape instanceof Polygon || shape instanceof MultiPolygon))
                throw failure("TRACK_AREA_GEOMETRY_INVALID");
            requireCoordinates(shape);
            if (!Double.isFinite(maximumSegmentLength) || maximumSegmentLength <= 0)
                throw failure("INVALID_TRACK_GEODESIC_SEGMENT_LENGTH");
            if (buffer && (radiusMetres == null || !Double.isFinite(radiusMetres) || radiusMetres < 0
                    || shape instanceof Point && radiusMetres == 0)) throw failure("TRACK_BUFFER_DISTANCE_INVALID");
            if (shape instanceof Point point) {
                if (!buffer) throw failure("TRACK_AREA_GEOMETRY_INVALID");
                var boundary = TrackGeodesicDisk.sample(point, radiusMetres, maximumSegmentLength);
                return new Footprint(vertex(point.getCoordinate()), boundary.boundary(), boundary.render());
            }
            return TrackGeodesicPolygon.footprint(shape, buffer ? radiusMetres : 0, maximumSegmentLength);
        } catch (RuntimeException error) { throw safe(error); }
    }

    static MultiPolygon connect(List<Footprint> observations, double maximumSegmentLength) {
        try {
            if (observations == null || observations.isEmpty()) throw failure("TRACK_AREA_GEOMETRY_INVALID");
            if (!Double.isFinite(maximumSegmentLength) || maximumSegmentLength <= 0)
                throw failure("INVALID_TRACK_GEODESIC_SEGMENT_LENGTH");
            List<Geometry> pieces = new ArrayList<>();
            long vertices = 0;
            Footprint previous = null;
            for (Footprint current : observations) {
                if (current == null) throw failure("TRACK_AREA_GEOMETRY_INVALID");
                MultiPolygon area = current.area();
                vertices += current.hullVertices().size() + area.getNumPoints();
                if (vertices > MAX_VERTICES) throw failure("TRACK_AREA_VERTEX_LIMIT_EXCEEDED");
                // Retain the original sampled footprint as well as the connecting hull. This
                // also preserves single-observation holes and protects sampled circular arcs.
                pieces.add(area);
                if (previous != null) {
                    var candidates = new ArrayList<>(previous.hullVertices()); candidates.addAll(current.hullVertices());
                    Vertex reference = TrackGeodesicHull.midpoint(previous.reference(), current.reference());
                    var connection = TrackGeodesicHull.build(candidates, reference, maximumSegmentLength).render();
                    vertices += connection.getNumPoints();
                    if (vertices > MAX_VERTICES) throw failure("TRACK_AREA_VERTEX_LIMIT_EXCEEDED");
                    pieces.add(connection);
                }
                previous = current;
            }
            return multi(UnaryUnionOp.union(pieces, FACTORY));
        } catch (RuntimeException error) { throw safe(error); }
    }

    static MultiPolygon multi(Geometry geometry) {
        var polygons = new ArrayList<Polygon>(); collect(geometry, polygons);
        MultiPolygon result = FACTORY.createMultiPolygon(polygons.toArray(Polygon[]::new));
        if (result.getNumPoints() > MAX_VERTICES) throw failure("TRACK_AREA_VERTEX_LIMIT_EXCEEDED");
        if (result.isEmpty() || !result.isValid()) throw failure("TRACK_AREA_GEOMETRY_INVALID");
        return result;
    }

    private static void collect(Geometry geometry, List<Polygon> polygons) {
        if (geometry instanceof Polygon polygon) polygons.add(polygon);
        else if (geometry instanceof GeometryCollection collection)
            for (int i = 0; i < collection.getNumGeometries(); i++) collect(collection.getGeometryN(i), polygons);
        else if (geometry != null && !geometry.isEmpty()) throw failure("TRACK_AREA_GEOMETRY_INVALID");
    }

    static Vertex vertex(Coordinate coordinate) {
        double longitude = Math.abs(coordinate.y) == 90 ? 0
                : coordinate.x - 360 * Math.floor((coordinate.x + 180) / 360);
        return new Vertex(longitude == 0 ? 0 : longitude, coordinate.y == 0 ? 0 : coordinate.y);
    }

    static void requireCoordinates(Geometry shape) {
        if (shape.getSRID()!=4326) throw failure("TRACK_GEODESIC_COORDINATE_INVALID");
        if (shape.getNumPoints()>MAX_VERTICES) throw failure("TRACK_AREA_VERTEX_LIMIT_EXCEEDED");
        for (Coordinate coordinate : shape.getCoordinates()) {
            if (!Double.isFinite(coordinate.x)||!Double.isFinite(coordinate.y)
                    ||Math.abs(coordinate.x)>180||Math.abs(coordinate.y)>90
                    ||!Double.isNaN(coordinate.getZ())||!Double.isNaN(coordinate.getM()))
                throw failure("TRACK_GEODESIC_COORDINATE_INVALID");
        }
    }

    static void requireLocal(Vertex reference, List<Vertex> vertices) {
        for (Vertex vertex : vertices) if (Geodesic.WGS84.Inverse(reference.latitude(), reference.longitude(), vertex.latitude(), vertex.longitude()).s12
                >= TrackGeodesicHull.MAX_REFERENCE_RADIUS) throw failure("TRACK_GEODESIC_HULL_RANGE_NOT_SUPPORTED");
    }

    static RuntimeException safe(RuntimeException error) {
        if (error instanceof IllegalArgumentException && "TRACK_GEODESIC_VERTEX_LIMIT_EXCEEDED".equals(error.getMessage()))
            return failure("TRACK_AREA_VERTEX_LIMIT_EXCEEDED");
        if (error instanceof IllegalArgumentException && error.getMessage() != null && SAFE_CODES.contains(error.getMessage())) return error;
        return failure("TRACK_AREA_GEOMETRY_INVALID");
    }

    static IllegalArgumentException failure(String code) { return new IllegalArgumentException(code); }
}
