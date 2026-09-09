package cn.superhuang.datascalpel.taskengine.canvas;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.union.UnaryUnionOp;

import java.util.ArrayList;
import java.util.List;

/** Planar observation footprints and adjacent-pair hulls, not a hull of the entire track. */
final class TrackAreaGeometry {
    private static final int MAX_VERTICES = 1_000_000;
    private TrackAreaGeometry() { }

    static Geometry footprint(Geometry geometry, Double distance, boolean buffer) {
        if (geometry == null || geometry.isEmpty()) return null;
        check(geometry);
        if (!(geometry instanceof Point || geometry instanceof Polygon || geometry instanceof MultiPolygon))
            throw failure("TRACK_AREA_GEOMETRY_INVALID");
        if (buffer && (distance == null || !Double.isFinite(distance) || distance < 0
                || geometry instanceof Point && distance == 0))
            throw failure("TRACK_BUFFER_DISTANCE_INVALID");
        try {
            Geometry result = buffer && distance != 0 ? geometry.buffer(distance, 16) : geometry.copy();
            if (!(result instanceof Polygon || result instanceof MultiPolygon) || result.isEmpty())
                throw failure("TRACK_AREA_GEOMETRY_INVALID");
            result.setSRID(geometry.getSRID());
            check(result);
            return result;
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("TRACK_")) throw exception;
            throw failure("TRACK_AREA_GEOMETRY_INVALID");
        } catch (RuntimeException exception) {
            // JTS topology messages can contain coordinates. Do not attach that cause to diagnostics.
            throw failure("TRACK_AREA_GEOMETRY_INVALID");
        }
    }

    static Geometry connect(List<Geometry> observations) {
        if (observations.isEmpty()) throw failure("TRACK_AREA_GEOMETRY_INVALID");
        GeometryFactory factory = observations.getFirst().getFactory();
        List<Geometry> pieces = new ArrayList<>();
        long vertices = 0;
        Geometry previous = null;
        for (Geometry current : observations) {
            check(current);
            vertices += current.getNumPoints();
            if (vertices > MAX_VERTICES) throw failure("TRACK_AREA_VERTEX_LIMIT_EXCEEDED");
            Geometry piece;
            try {
                piece = previous == null ? current : factory.createGeometryCollection(new Geometry[]{previous, current}).convexHull();
            } catch (RuntimeException exception) {
                throw failure("TRACK_AREA_GEOMETRY_INVALID");
            }
            pieces.add(piece);
            previous = current;
        }
        try {
            Geometry area = UnaryUnionOp.union(pieces);
            check(area);
            MultiPolygon result;
            if (area instanceof Polygon polygon) result = factory.createMultiPolygon(new Polygon[]{polygon});
            else if (area instanceof MultiPolygon polygons) result = polygons;
            else throw failure("TRACK_AREA_GEOMETRY_INVALID");
            result.setSRID(observations.getFirst().getSRID());
            return result;
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("TRACK_")) throw exception;
            throw failure("TRACK_AREA_GEOMETRY_INVALID");
        } catch (RuntimeException exception) {
            throw failure("TRACK_AREA_GEOMETRY_INVALID");
        }
    }

    private static void check(Geometry geometry) {
        if (geometry == null || geometry.isEmpty()) throw failure("TRACK_AREA_GEOMETRY_INVALID");
        if (geometry.getNumPoints() > MAX_VERTICES) throw failure("TRACK_AREA_VERTEX_LIMIT_EXCEEDED");
        for (Coordinate coordinate : geometry.getCoordinates()) {
            if (!Double.isFinite(coordinate.x) || !Double.isFinite(coordinate.y))
                throw failure("TRACK_AREA_GEOMETRY_INVALID");
        }
        try {
            if (!geometry.isValid()) throw failure("TRACK_AREA_GEOMETRY_INVALID");
        } catch (RuntimeException exception) {
            throw failure("TRACK_AREA_GEOMETRY_INVALID");
        }
    }

    private static IllegalArgumentException failure(String code) { return new IllegalArgumentException(code); }
}
