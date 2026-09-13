package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Position;
import net.sf.geographiclib.Geodesic;
import org.locationtech.jts.geom.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Local minor regions on WGS84. Never uses rendered longitude seams to locate a point. */
final class Wgs84PolygonRegion {
    enum Location { INSIDE, OUTSIDE, BOUNDARY, UNRESOLVED }
    private final List<Part> parts;
    private final Set<Position> boundaryVertices;
    private Wgs84PolygonRegion(List<Part> parts) {
        this.parts=List.copyOf(parts);
        var vertices=new HashSet<Position>();
        for (Part part : parts) {
            for (Position vertex : part.shell) vertices.add(canonical(vertex));
            for (List<Position> hole : part.holes) for (Position vertex : hole) vertices.add(canonical(vertex));
        }
        boundaryVertices=Set.copyOf(vertices);
    }

    static Wgs84PolygonRegion prepare(Geometry shape) {
        if (!(shape instanceof Polygon || shape instanceof MultiPolygon) || shape.isEmpty())
            throw failure("GEODESIC_DISTANCE_GEOMETRY_UNSUPPORTED");
        try {
            TrackGeodesicPolygon.Source validated=validated(shape,null);
            Position reference=position(validated.reference());
            return new Wgs84PolygonRegion(parts(shape,java.util.Collections.nCopies(shape.getNumGeometries(),reference)));
        } catch (IllegalArgumentException error) {
            if (!(shape instanceof MultiPolygon)
                    || !"GEODESIC_DISTANCE_REGION_RANGE_NOT_SUPPORTED".equals(error.getMessage())) throw error;
        }

        // A MultiPolygon need not fit in one common convex chart. Fall back only when every
        // component is independently local and every component pair is provably separated.
        // Touching, overlapping, nested or numerically unresolved global components remain
        // rejected instead of being interpreted through a longitude seam or planar JTS area.
        var budget=new Wgs84SegmentDistance.Budget(Wgs84SegmentDistance.MAX_EVALUATIONS);
        var references=new ArrayList<Position>();
        var boundaries=new ArrayList<List<Wgs84SegmentDistance.Arc>>();
        for (int index=0;index<shape.getNumGeometries();index++) {
            Polygon polygon=(Polygon)shape.getGeometryN(index);
            references.add(position(validated(polygon,budget).reference()));
            boundaries.add(Wgs84LinearDistance.edges(polygon.getBoundary()));
        }
        var parts=parts(shape,references);
        for (int first=0;first<parts.size();first++) for (int second=first+1;second<parts.size();second++) {
            var relation=Wgs84BoundaryIntersection.relate(boundaries.get(first),boundaries.get(second),budget);
            if (relation==Wgs84BoundaryIntersection.Relation.INTERSECTING)
                throw failure("GEODESIC_DISTANCE_GEOMETRY_INVALID");
            if (relation==Wgs84BoundaryIntersection.Relation.UNRESOLVED) {
                var distance=Wgs84SegmentDistance.nearestArcs(boundaries.get(first),boundaries.get(second),0.0001,budget);
                if (distance.lowerBoundMetres()<=Wgs84SegmentDistance.ROUNDOFF_METRES)
                    throw failure("GEODESIC_DISTANCE_PRECISION_NOT_REACHED");
            }
            requireOutside(parts.get(first),parts.get(second),budget);
            requireOutside(parts.get(second),parts.get(first),budget);
        }
        return new Wgs84PolygonRegion(parts);
    }

    private static TrackGeodesicPolygon.Source validated(Geometry shape,Wgs84SegmentDistance.Budget budget) {
        // Source topology uses original continuous arcs. Preparing a distance region must
        // not allocate a sampled render or depend on its chord length/Boolean acceptance.
        TrackGeodesicPolygon.Source validated;
        try { validated=budget==null ? TrackGeodesicPolygon.prepareSource(shape) : TrackGeodesicPolygon.prepareSource(shape,budget); }
        catch (RuntimeException error) {
            String code=error.getMessage();
            if ("GEODESIC_TOPOLOGY_PRECISION_NOT_REACHED".equals(code) || "GEODESIC_DISTANCE_WORK_LIMIT_EXCEEDED".equals(code))
                throw failure(code);
            if ("TRACK_GEODESIC_COORDINATE_INVALID".equals(code)) throw failure("GEODESIC_DISTANCE_COORDINATE_INVALID");
            if ("TRACK_GEODESIC_HULL_RANGE_NOT_SUPPORTED".equals(code)) throw failure("GEODESIC_DISTANCE_REGION_RANGE_NOT_SUPPORTED");
            if ("TRACK_AREA_VERTEX_LIMIT_EXCEEDED".equals(code) || "TRACK_GEODESIC_HULL_WORK_LIMIT_EXCEEDED".equals(code))
                throw failure("GEODESIC_DISTANCE_WORK_LIMIT_EXCEEDED");
            throw failure("GEODESIC_DISTANCE_GEOMETRY_INVALID");
        }
        return validated;
    }

    private static List<Part> parts(Geometry shape,List<Position> references) {
        var parts=new ArrayList<Part>();
        for (int i=0;i<shape.getNumGeometries();i++) {
            Polygon polygon=(Polygon)shape.getGeometryN(i);
            var holes=new ArrayList<List<Position>>();
            for (int h=0;h<polygon.getNumInteriorRing();h++) holes.add(ring(polygon.getInteriorRingN(h)));
            List<Position> shell=ring(polygon.getExteriorRing());
            var vertices=new HashSet<Position>();
            shell.forEach(vertex->vertices.add(canonical(vertex)));
            holes.forEach(hole->hole.forEach(vertex->vertices.add(canonical(vertex))));
            parts.add(new Part(references.get(i),shell,List.copyOf(holes),Set.copyOf(vertices)));
        }
        return List.copyOf(parts);
    }

    private static void requireOutside(Part candidate,Part container,Wgs84SegmentDistance.Budget budget) {
        Location location=locatePart(candidate.shell.getFirst(),container,budget);
        if (location==Location.INSIDE || location==Location.BOUNDARY)
            throw failure("GEODESIC_DISTANCE_GEOMETRY_INVALID");
        if (location==Location.UNRESOLVED)
            throw failure("GEODESIC_DISTANCE_PRECISION_NOT_REACHED");
    }

    Location locate(Position point) {
        return locate(point,new Wgs84SegmentDistance.Budget(Wgs84SegmentDistance.MAX_EVALUATIONS));
    }

    Location locate(Position point,Wgs84SegmentDistance.Budget budget) {
        if (point==null || !Double.isFinite(point.longitude()) || !Double.isFinite(point.latitude())
                || Math.abs(point.longitude())>180 || Math.abs(point.latitude())>90)
            throw failure("GEODESIC_DISTANCE_COORDINATE_INVALID");
        // All rings lie in this strongly convex ball. Its minor region cannot contain a
        // point outside the ball. This also prevents antipodal azimuth winding false positives.
        // A hole vertex may touch the interior of a shell edge. Its exact boundary
        // evidence must not be hidden by the shell's unresolved opposite-bearing sweep.
        if (boundaryVertices.contains(canonical(point))) return Location.BOUNDARY;
        boolean unresolved=false;
        for (Part part : parts) {
            Location location=locatePart(point,part,budget);
            if (location==Location.INSIDE || location==Location.BOUNDARY) return location;
            unresolved|=location==Location.UNRESOLVED;
        }
        return unresolved ? Location.UNRESOLVED : Location.OUTSIDE;
    }

    private static Location locatePart(Position point,Part part,Wgs84SegmentDistance.Budget budget) {
        budget.consume();
        if (Geodesic.WGS84.Inverse(part.reference.latitude(),part.reference.longitude(),point.latitude(),point.longitude()).s12
                >=TrackGeodesicHull.MAX_REFERENCE_RADIUS) return Location.OUTSIDE;
        if (part.boundaryVertices.contains(canonical(point))) return Location.BOUNDARY;
        Location shell=locateRing(point,part.shell,budget);
        if (shell!=Location.INSIDE) return shell;
        boolean uncertainHole=false;
        for (var hole : part.holes) {
            Location location=locateRing(point,hole,budget);
            if (location==Location.BOUNDARY) return location;
            if (location==Location.INSIDE) return Location.OUTSIDE;
            uncertainHole|=location==Location.UNRESOLVED;
        }
        return uncertainHole ? Location.UNRESOLVED : Location.INSIDE;
    }

    static Location locateRing(Position point,List<Position> ring,Wgs84SegmentDistance.Budget budget) {
        double first=0, previous=0, sum=0, correction=0;
        for (int i=0;i<ring.size();i++) {
            Position vertex=ring.get(i);
            budget.consume();
            var inverse=Geodesic.WGS84.Inverse(point.latitude(),point.longitude(),vertex.latitude(),vertex.longitude());
            if (inverse.s12==0) return Location.BOUNDARY;
            if (i==0) { first=inverse.azi1; previous=first; continue; }
            double bearing=i==ring.size()-1 ? first : inverse.azi1;
            double change=bearing-previous; change-=360*Math.rint(change/360);
            // In a common strongly convex domain a geodesic triangle has minor angular
            // sweep. Opposite rays mean an edge passes through/very near the query; do not
            // round that angular ambiguity into an inside/outside or zero-distance claim.
            if (Math.abs(change)>=180-1e-10) return Location.UNRESOLVED;
            double term=change-correction, next=sum+term;
            correction=(next-sum)-term; sum=next; previous=bearing;
        }
        double winding=Math.rint(sum/360);
        if (Math.abs(sum-winding*360)>1e-7 || Math.abs(winding)>1) throw failure("GEODESIC_DISTANCE_GEOMETRY_INVALID");
        return winding==0 ? Location.OUTSIDE : Location.INSIDE;
    }

    private static List<Position> ring(LineString ring) {
        var positions=new ArrayList<Position>();
        for (Coordinate coordinate : ring.getCoordinates()) positions.add(new Position(coordinate.x,coordinate.y));
        return List.copyOf(positions);
    }
    private static Position position(TrackGeodesicAreaBoundary.Vertex vertex) {
        return new Position(vertex.longitude(),vertex.latitude());
    }
    private static Position canonical(Position point) {
        double longitude=Math.abs(point.latitude())==90 ? 0 : point.longitude()==180 ? -180 : point.longitude();
        return new Position(longitude==0 ? 0 : longitude,point.latitude()==0 ? 0 : point.latitude());
    }
    private record Part(Position reference,List<Position> shell,List<List<Position>> holes,Set<Position> boundaryVertices) { }
    private static IllegalArgumentException failure(String code) { return new IllegalArgumentException(code); }
}
