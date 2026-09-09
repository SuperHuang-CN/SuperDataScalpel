package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Position;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Arc;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Result;
import org.locationtech.jts.geom.*;

import java.util.ArrayList;
import java.util.List;

/** Internal point/linear geometry distance. Polygon containment is deliberately not an edge-only distance. */
final class Wgs84LinearDistance {
    private static final int MAX_VERTICES = 1_000_000;
    private Wgs84LinearDistance() { }

    static Result nearest(Geometry first, Geometry second, double toleranceMetres) {
        if (first == null || second == null || first.isEmpty() || second.isEmpty()) return null;
        // Validate both complete inputs before considering an early zero-distance result.
        List<Arc> left=edges(first), right=edges(second);
        var budget=new Wgs84SegmentDistance.Budget(Wgs84SegmentDistance.MAX_EVALUATIONS);
        return nearestEdges(left,right,toleranceMetres,budget);
    }

    static Result nearestEdges(List<Arc> left, List<Arc> right, double toleranceMetres, Wgs84SegmentDistance.Budget budget) {
        if (left.isEmpty() || right.isEmpty()) return null;
        return Wgs84SegmentDistance.nearestArcs(left,right,toleranceMetres,budget);
    }

    static List<Arc> edges(Geometry geometry) {
        if (geometry.getSRID()!=4326) throw failure("GEODESIC_DISTANCE_COORDINATE_INVALID");
        if (!(geometry instanceof Point || geometry instanceof MultiPoint || geometry instanceof LineString || geometry instanceof MultiLineString))
            throw failure("GEODESIC_DISTANCE_GEOMETRY_UNSUPPORTED");
        if (geometry.getNumPoints()>MAX_VERTICES) throw failure("GEODESIC_DISTANCE_WORK_LIMIT_EXCEEDED");
        var edges=new ArrayList<Arc>();
        for (int part=0;part<geometry.getNumGeometries();part++) {
            Geometry member=geometry.getGeometryN(part);
            if (member.isEmpty()) continue;
            var coordinates=member.getCoordinates();
            Position previous=null;
            for (Coordinate coordinate : coordinates) {
                if (!Double.isFinite(coordinate.x) || !Double.isFinite(coordinate.y)
                        || Math.abs(coordinate.x)>180 || Math.abs(coordinate.y)>90
                        || !Double.isNaN(coordinate.getZ()) || !Double.isNaN(coordinate.getM()))
                    throw failure("GEODESIC_DISTANCE_COORDINATE_INVALID");
                var next=new Position(coordinate.x,coordinate.y);
                if (previous!=null) edges.add(new Arc(previous,next));
                else if (member instanceof Point) edges.add(new Arc(next,next));
                previous=next;
            }
        }
        return edges;
    }

    private static IllegalArgumentException failure(String code) { return new IllegalArgumentException(code); }
}
