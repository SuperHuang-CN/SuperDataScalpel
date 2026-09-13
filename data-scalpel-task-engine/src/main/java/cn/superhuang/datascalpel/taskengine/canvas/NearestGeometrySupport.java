package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.SpatialNearestGeodesicGeometryMode;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.distance.DistanceOp;

final class NearestGeometrySupport {
    private NearestGeometrySupport() { }
    static Geometry checked(Geometry input, boolean geodesic) {
        return checked(input, geodesic, SpatialNearestGeodesicGeometryMode.POINT_ONLY);
    }

    static Geometry checked(Geometry input, boolean geodesic, SpatialNearestGeodesicGeometryMode mode) {
        if (input == null || input.isEmpty()) return null;
        if (geodesic && mode == SpatialNearestGeodesicGeometryMode.POINT_ONLY && !(input instanceof Point))
            throw new IllegalArgumentException("GEODESIC_NEAREST_REQUIRES_POINTS");
        if (geodesic && mode == SpatialNearestGeodesicGeometryMode.GEOMETRY && !supportedGeodesicGeometry(input))
            throw new IllegalArgumentException("GEODESIC_DISTANCE_GEOMETRY_UNSUPPORTED");
        // Longitude/latitude JTS validity is not authoritative for a geodesic region:
        // a valid dateline shell and hole are rendered as long planar chords and can be
        // reported as an invalid planar Polygon. Wgs84GeometryBounds / Wgs84NearestMatch
        // validate the original continuous arcs and region topology before using it.
        if (!geodesic && !input.isValid())
            throw new IllegalArgumentException("SPATIAL_NEAREST_GEOMETRY_INVALID");
        for (Coordinate coordinate : input.getCoordinates()) {
            if (!Double.isFinite(coordinate.x) || !Double.isFinite(coordinate.y)
                    || geodesic && (Math.abs(coordinate.x) > 180 || Math.abs(coordinate.y) > 90))
                throw new IllegalArgumentException("SPATIAL_NEAREST_GEOMETRY_INVALID");
        }
        return input;
    }

    private static boolean supportedGeodesicGeometry(Geometry input) {
        return input instanceof Point || input instanceof MultiPoint
                || input instanceof LineString || input instanceof MultiLineString
                || input instanceof Polygon || input instanceof MultiPolygon;
    }

    static Geometry connection(Geometry source, Geometry candidate, boolean geodesic, double step) {
        if (source == null || candidate == null) return null;
        checked(source, geodesic); checked(candidate, geodesic);
        Coordinate[] endpoints = geodesic ? new Coordinate[]{source.getCoordinate(), candidate.getCoordinate()}
                : DistanceOp.nearestPoints(source, candidate);
        GeometryFactory factory = source.getFactory();
        LineString line = factory.createLineString(new Coordinate[]{new CoordinateXY(endpoints[0].x, endpoints[0].y),
                new CoordinateXY(endpoints[1].x, endpoints[1].y)});
        line.setSRID(geodesic ? 4326 : source.getSRID());
        if (geodesic) return geodesicConnection(line, step);
        MultiLineString result = factory.createMultiLineString(new LineString[]{line});
        result.setSRID(source.getSRID());
        return result;
    }

    /** Render from the witnesses returned by the distance solve; never solve nearest positions again. */
    static Geometry geodesicConnection(Geometry witnesses, double step) {
        if (witnesses == null) return null;
        if (!(witnesses instanceof LineString line) || line.getNumPoints() != 2 || line.getSRID() != 4326)
            throw new IllegalArgumentException("SPATIAL_NEAREST_GEOMETRY_INVALID");
        try { return TrackGeodesicPath.build(line, step); }
        catch (IllegalArgumentException failure) {
            if ("TRACK_GEODESIC_VERTEX_LIMIT_EXCEEDED".equals(failure.getMessage()))
                throw new IllegalArgumentException("SPATIAL_NEAREST_CONNECTION_VERTEX_LIMIT_EXCEEDED");
            throw new IllegalArgumentException("SPATIAL_NEAREST_GEOMETRY_INVALID");
        }
    }
}
