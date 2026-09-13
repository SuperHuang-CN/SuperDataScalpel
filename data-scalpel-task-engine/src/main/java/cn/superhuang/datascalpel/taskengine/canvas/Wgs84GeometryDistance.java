package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Arc;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Result;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Position;
import org.locationtech.jts.geom.*;

import java.util.List;

/** Internal regional geometry distance; Operators retain their gates pending full pipeline integration. */
final class Wgs84GeometryDistance {
    private Wgs84GeometryDistance() { }

    static Result nearest(Geometry first,Geometry second,double toleranceMetres) {
        if (first==null || second==null || first.isEmpty() || second.isEmpty()) return null;
        if (!Double.isFinite(toleranceMetres) || toleranceMetres<4*Wgs84SegmentDistance.ROUNDOFF_METRES)
            throw new IllegalArgumentException("INVALID_GEODESIC_DISTANCE_PRECISION");
        Prepared left=prepare(first), right=prepare(second);
        var budget=new Wgs84SegmentDistance.Budget(Wgs84SegmentDistance.MAX_EVALUATIONS);
        AreaRelation relation=containment(left,right,budget);
        if (relation.common!=null) return new Result(relation.common,relation.common,0,0);
        Result boundary=Wgs84LinearDistance.nearestEdges(left.edges,right.edges,toleranceMetres,budget);
        if (boundary==null) return null;
        if (boundary.lowerBoundMetres()<=Wgs84SegmentDistance.ROUNDOFF_METRES) {
            var intersection=Wgs84BoundaryIntersection.match(left.edges,right.edges,budget);
            if (intersection.relation()==Wgs84BoundaryIntersection.Relation.INTERSECTING
                    && intersection.contact()!=null)
                return new Result(intersection.contact(),intersection.contact(),0,0);
        }
        // Crossing regions can have no contained vertices. Their boundary-pair minimum is
        // zero; interval search retains a small attained upper bound rather than snapping.
        if (relation.unresolved) {
            if (boundary.distanceMetres()+Wgs84SegmentDistance.ROUNDOFF_METRES>toleranceMetres)
                throw new IllegalArgumentException("GEODESIC_DISTANCE_PRECISION_NOT_REACHED");
            return new Result(boundary.first(),boundary.second(),boundary.distanceMetres(),0);
        }
        return boundary;
    }

    static Boolean withinDistance(Geometry first,Geometry second,double thresholdMetres) {
        if (first==null || second==null || first.isEmpty() || second.isEmpty()) return null;
        Wgs84SegmentDistance.requireThreshold(thresholdMetres);
        Prepared left=prepare(first), right=prepare(second);
        var budget=new Wgs84SegmentDistance.Budget(Wgs84SegmentDistance.MAX_EVALUATIONS);
        AreaRelation relation=containment(left,right,budget);
        if (relation.common!=null) return true;
        if (left.edges.isEmpty()||right.edges.isEmpty()) return null;
        boolean within=Wgs84SegmentDistance.withinArcs(left.edges,right.edges,thresholdMetres,budget);
        // Boundary witnesses prove proximity even with unresolved containment. Positive
        // boundary separation cannot prove region separation while containment is unknown.
        if (!within && relation.unresolved) throw new IllegalArgumentException("GEODESIC_DISTANCE_PRECISION_NOT_REACHED");
        return within;
    }

    private static AreaRelation containment(Prepared left,Prepared right,Wgs84SegmentDistance.Budget budget) {
        boolean unresolved=false;
        // A vertex contained by the other area is a genuine common location, even if the
        // boundaries are far apart. Check both directions and every multipart component.
        if (right.region!=null) for (Position vertex : left.vertices) {
            var location=right.region.locate(vertex,budget);
            if (location==Wgs84PolygonRegion.Location.INSIDE || location==Wgs84PolygonRegion.Location.BOUNDARY)
                return new AreaRelation(vertex,false);
            unresolved |= location==Wgs84PolygonRegion.Location.UNRESOLVED;
        }
        if (left.region!=null) for (Position vertex : right.vertices) {
            var location=left.region.locate(vertex,budget);
            if (location==Wgs84PolygonRegion.Location.INSIDE || location==Wgs84PolygonRegion.Location.BOUNDARY)
                return new AreaRelation(vertex,false);
            unresolved |= location==Wgs84PolygonRegion.Location.UNRESOLVED;
        }
        return new AreaRelation(null,unresolved);
    }

    static Prepared prepare(Geometry geometry) {
        if (geometry instanceof Polygon || geometry instanceof MultiPolygon) {
            var region=Wgs84PolygonRegion.prepare(geometry);
            return prepared(region,Wgs84LinearDistance.edges(geometry.getBoundary()));
        }
        return prepared(null,Wgs84LinearDistance.edges(geometry));
    }
    private static Prepared prepared(Wgs84PolygonRegion region,List<Arc> edges) {
        var vertices=new java.util.LinkedHashSet<Position>();
        for (Arc edge : edges) { vertices.add(edge.start()); vertices.add(edge.end()); }
        return new Prepared(region,edges,List.copyOf(vertices));
    }
    record Prepared(Wgs84PolygonRegion region,List<Arc> edges,List<Position> vertices) { }
    private record AreaRelation(Position common,boolean unresolved) { }
}
