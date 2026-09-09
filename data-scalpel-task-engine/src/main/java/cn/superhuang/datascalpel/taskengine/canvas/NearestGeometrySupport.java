package cn.superhuang.datascalpel.taskengine.canvas;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.distance.DistanceOp;

final class NearestGeometrySupport {
    private NearestGeometrySupport() { }
    static Geometry checked(Geometry input, boolean geodesic) {
        if (input == null || input.isEmpty()) return null;
        if (!input.isValid()) throw new IllegalArgumentException("SPATIAL_NEAREST_GEOMETRY_INVALID");
        if (geodesic && !(input instanceof Point)) throw new IllegalArgumentException("GEODESIC_NEAREST_REQUIRES_POINTS");
        for (Coordinate coordinate : input.getCoordinates()) {
            if (!Double.isFinite(coordinate.x) || !Double.isFinite(coordinate.y)
                    || geodesic && (Math.abs(coordinate.x) > 180 || Math.abs(coordinate.y) > 90))
                throw new IllegalArgumentException("SPATIAL_NEAREST_GEOMETRY_INVALID");
        }
        return input;
    }

    static Geometry connection(Geometry source, Geometry candidate, boolean geodesic, double step) {
        if (source == null || candidate == null) return null;
        checked(source, geodesic); checked(candidate, geodesic);
        Coordinate[] endpoints = geodesic ? new Coordinate[]{source.getCoordinate(), candidate.getCoordinate()}
                : DistanceOp.nearestPoints(source, candidate);
        GeometryFactory factory = source.getFactory();
        LineString line = factory.createLineString(new Coordinate[]{new CoordinateXY(endpoints[0].x, endpoints[0].y),
                new CoordinateXY(endpoints[1].x, endpoints[1].y)});
        line.setSRID(source.getSRID());
        if (geodesic) {
            try { return TrackGeodesicPath.build(line, step); }
            catch (IllegalArgumentException failure) {
                if ("TRACK_GEODESIC_VERTEX_LIMIT_EXCEEDED".equals(failure.getMessage()))
                    throw new IllegalArgumentException("SPATIAL_NEAREST_CONNECTION_VERTEX_LIMIT_EXCEEDED");
                throw new IllegalArgumentException("SPATIAL_NEAREST_GEOMETRY_INVALID");
            }
        }
        MultiLineString result = factory.createMultiLineString(new LineString[]{line});
        result.setSRID(source.getSRID());
        return result;
    }
}
