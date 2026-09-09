package cn.superhuang.datascalpel.taskengine.canvas;

import net.sf.geographiclib.Geodesic;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.operation.union.UnaryUnionOp;

import java.util.ArrayList;
import java.util.List;

/** True sampled boundary, separate from artificial longitude-strip and pole closures. */
record TrackGeodesicAreaBoundary(List<Vertex> boundary, Integer enclosedPole) {
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final int MAX_VERTICES = 1_000_000;

    TrackGeodesicAreaBoundary { boundary = List.copyOf(boundary); }

    record Vertex(double longitude, double latitude) {
        Coordinate coordinate() { return new CoordinateXY(longitude, latitude); }
    }

    /** Ring order is clockwise on the ellipsoid; edges are shortest WGS84 geodesics. */
    static TrackGeodesicAreaBoundary fromClockwiseRing(List<Vertex> closedRing, double maximumSegmentLength) {
        if (closedRing.size() > MAX_VERTICES) throw failure("TRACK_AREA_VERTEX_LIMIT_EXCEEDED");
        if (closedRing.size() < 4 || !closedRing.getFirst().equals(closedRing.getLast()))
            throw failure("TRACK_AREA_GEOMETRY_INVALID");
        LineString ring = FACTORY.createLineString(polarMeridianCoordinates(closedRing));
        Geometry sampled = TrackGeodesicPath.build(ring, maximumSegmentLength);
        List<Vertex> vertices = new ArrayList<>();
        for (int part = 0; part < sampled.getNumGeometries(); part++) {
            for (Coordinate coordinate : sampled.getGeometryN(part).getCoordinates()) {
                double longitude = vertices.isEmpty() ? coordinate.x
                        : coordinate.x + 360 * Math.rint((vertices.getLast().longitude() - coordinate.x) / 360);
                if (!vertices.isEmpty() && Math.abs(coordinate.y) == 90 && vertices.getLast().latitude() == coordinate.y) {
                    // At a pole, +/-180 is not an ordinary longitude-unwrapping tie. The
                    // clockwise face lies below a northern pole edge (eastward) or above
                    // a southern pole edge (westward). A concave polygon can have a reflex
                    // interior angle here, so this zero-length physical turn may exceed 180.
                    // Ring topology is checked separately; this is not a long geodesic edge.
                    double delta = (coordinate.x - vertices.getLast().longitude()) % 360;
                    if (coordinate.y > 0 && delta < 0) delta += 360;
                    if (coordinate.y < 0 && delta > 0) delta -= 360;
                    longitude = vertices.getLast().longitude() + delta;
                }
                Vertex next = new Vertex(longitude, coordinate.y);
                if (vertices.isEmpty() || !vertices.getLast().equals(next)) vertices.add(next);
            }
        }
        if (vertices.size() < 4) throw failure("TRACK_AREA_GEOMETRY_INVALID");
        Vertex start = vertices.getFirst(), end = vertices.getLast();
        double winding = 360 * Math.rint((end.longitude() - start.longitude()) / 360);
        if (Math.abs(winding) > 360) throw failure("TRACK_AREA_GEOMETRY_INVALID");
        vertices.set(vertices.size() - 1, new Vertex(start.longitude() + winding, start.latitude()));
        // A clockwise northern cap winds west, a clockwise southern cap winds east.
        Integer pole = winding == 0 ? null : winding < 0 ? 90 : -90;
        return new TrackGeodesicAreaBoundary(vertices, pole);
    }

    private static Coordinate[] polarMeridianCoordinates(List<Vertex> ring) {
        int count = ring.size() - 1;
        int first = 0;
        for (Vertex vertex : ring) if (vertex == null || !Double.isFinite(vertex.longitude()) || !Double.isFinite(vertex.latitude())
                || Math.abs(vertex.longitude()) > 180 || Math.abs(vertex.latitude()) > 90)
            throw failure("TRACK_GEODESIC_COORDINATE_INVALID");
        while (first < count && Math.abs(ring.get(first).latitude()) == 90) first++;
        if (first == count) throw failure("TRACK_AREA_GEOMETRY_INVALID");
        var coordinates = new ArrayList<Coordinate>(ring.size() + 2);
        for (int i = 0; i < count; i++) {
            int index = (first + i) % count;
            Vertex vertex = ring.get(index);
            if (Math.abs(vertex.latitude()) == 90) {
                Vertex previous = ring.get((index + count - 1) % count), next = ring.get((index + 1) % count);
                if (Math.abs(previous.latitude()) == 90 || Math.abs(next.latitude()) == 90)
                    throw failure("TRACK_AREA_GEOMETRY_INVALID");
                // These are the same physical pole, represented on the incoming/outgoing meridian.
                // Starting a geodesic at the pole's arbitrary canonical longitude would draw a
                // spurious slanted leg when longitude/latitude coordinates are later rendered.
                coordinates.add(new CoordinateXY(previous.longitude(), vertex.latitude()));
                coordinates.add(new CoordinateXY(next.longitude(), vertex.latitude()));
            } else coordinates.add(vertex.coordinate());
            if (coordinates.size() >= MAX_VERTICES) throw failure("TRACK_AREA_VERTEX_LIMIT_EXCEEDED");
        }
        coordinates.add(coordinates.getFirst().copy());
        var expanded = new ArrayList<Coordinate>(coordinates.size() + 2);
        for (int i = 0; i < coordinates.size() - 1; i++) {
            Coordinate a = coordinates.get(i), b = coordinates.get(i + 1);
            expanded.add(a);
            double difference = b.x - a.x;
            difference -= 360 * Math.rint(difference / 360);
            if (Math.abs(a.y) != 90 && Math.abs(b.y) != 90 && Math.abs(difference) == 180) {
                var inverse = Geodesic.WGS84.Inverse(a.y, a.x, b.y, b.x);
                if (inverse.azi1 == 0 || Math.abs(inverse.azi1) == 180) {
                    // The pole can be inside an edge even when no input vertex is at it.
                    // Split there explicitly before the line sampler normalizes longitudes.
                    double pole = inverse.azi1 == 0 ? 90 : -90;
                    expanded.add(new CoordinateXY(a.x, pole));
                    expanded.add(new CoordinateXY(b.x, pole));
                }
            }
            if (expanded.size() >= MAX_VERTICES) throw failure("TRACK_AREA_VERTEX_LIMIT_EXCEEDED");
        }
        expanded.add(expanded.getFirst().copy());
        return expanded.toArray(Coordinate[]::new);
    }

    MultiPolygon render() {
        try { return renderBoundary(); }
        catch (RuntimeException error) {
            if (error instanceof IllegalArgumentException
                    && "TRACK_AREA_VERTEX_LIMIT_EXCEEDED".equals(error.getMessage())) throw error;
            // JTS diagnostics may contain coordinates; don't retain the original message or cause.
            throw failure("TRACK_AREA_GEOMETRY_INVALID");
        }
    }

    private MultiPolygon renderBoundary() {
        if (boundary.size() > MAX_VERTICES - 4) throw failure("TRACK_AREA_VERTEX_LIMIT_EXCEEDED");
        List<Coordinate> coordinates = new ArrayList<>(boundary.size() + 3);
        for (Vertex vertex : boundary) {
            if (!Double.isFinite(vertex.longitude()) || !Double.isFinite(vertex.latitude())
                    || Math.abs(vertex.longitude()) > 540 || Math.abs(vertex.latitude()) > 90)
                throw failure("TRACK_AREA_GEOMETRY_INVALID");
            coordinates.add(vertex.coordinate());
        }
        if (enclosedPole != null) {
            if (Math.abs(enclosedPole) != 90) throw failure("TRACK_AREA_GEOMETRY_INVALID");
            Coordinate first = coordinates.getFirst(), last = coordinates.getLast();
            coordinates.add(new CoordinateXY(last.x, enclosedPole));
            coordinates.add(new CoordinateXY(first.x, enclosedPole));
            coordinates.add(first.copy());
        }
        Polygon unwrapped = FACTORY.createPolygon(coordinates.toArray(Coordinate[]::new));
        if (!unwrapped.isValid()) throw failure("TRACK_AREA_GEOMETRY_INVALID");
        Envelope envelope = unwrapped.getEnvelopeInternal();
        if (envelope.getWidth() > 360 + 1e-9) throw failure("TRACK_AREA_GEOMETRY_INVALID");
        List<Geometry> parts = new ArrayList<>();
        for (int index = slab(envelope.getMinX()); index <= slab(envelope.getMaxX()); index++) {
            Geometry strip = FACTORY.toGeometry(new Envelope(-180 + 360d * index, 180 + 360d * index, -90, 90));
            Geometry clipped = unwrapped.intersection(strip);
            if (!clipped.isEmpty() && clipped.getDimension() == 2)
                parts.add(AffineTransformation.translationInstance(-360d * index, 0).transform(clipped));
        }
        Geometry union = UnaryUnionOp.union(parts, FACTORY);
        List<Polygon> polygons = new ArrayList<>();
        collectPolygons(union, polygons);
        MultiPolygon result = FACTORY.createMultiPolygon(polygons.toArray(Polygon[]::new));
        if (result.getNumPoints() > MAX_VERTICES) throw failure("TRACK_AREA_VERTEX_LIMIT_EXCEEDED");
        if (result.isEmpty() || !result.isValid()) throw failure("TRACK_AREA_GEOMETRY_INVALID");
        return result;
    }

    private static int slab(double longitude) { return (int) Math.floor((longitude + 180) / 360); }

    private static void collectPolygons(Geometry geometry, List<Polygon> polygons) {
        if (geometry instanceof Polygon polygon) polygons.add(polygon);
        else if (geometry instanceof GeometryCollection collection)
            for (int i = 0; i < collection.getNumGeometries(); i++) collectPolygons(collection.getGeometryN(i), polygons);
    }

    private static IllegalArgumentException failure(String code) { return new IllegalArgumentException(code); }
}
