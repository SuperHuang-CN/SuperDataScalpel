package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Arc;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Budget;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Position;
import net.sf.geographiclib.Geodesic;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Continuous shortest-arc relations in a verified common strongly convex WGS84 domain. */
final class Wgs84ArcTopology {
    enum Kind { DISJOINT, CROSS, TOUCH, OVERLAP, UNRESOLVED }
    record Relation(Kind kind,Position contact) { }
    private enum Side { LEFT, RIGHT, ZERO, UNKNOWN }
    private static final double ANGULAR_GUARD=1e-12;
    private static final Comparator<Position> POSITION_ORDER=Comparator.comparingDouble(Position::longitude)
            .thenComparingDouble(Position::latitude);
    private Wgs84ArcTopology() { }

    /** Cheap continuous probe; an unverified local domain or uncertain bearing stays unresolved. */
    static Relation probeLocal(Arc left,Arc right,Budget budget) {
        if (budget==null) throw new IllegalArgumentException("INVALID_GEODESIC_DISTANCE_WORK_LIMIT");
        if (left==null||right==null) throw new IllegalArgumentException("GEODESIC_DISTANCE_COORDINATE_INVALID");
        var points=List.of(canonical(left.start()),canonical(left.end()),canonical(right.start()),canonical(right.end()));
        double x=0,y=0,z=0;
        for (Position point : points) {
            budget.consume();
            double latitude=Math.toRadians(point.latitude()), longitude=Math.toRadians(point.longitude());
            x+=Math.cos(latitude)*Math.cos(longitude); y+=Math.cos(latitude)*Math.sin(longitude); z+=Math.sin(latitude);
        }
        if (Math.hypot(Math.hypot(x,y),z)<=1e-12) return relation(Kind.UNRESOLVED);
        // The unit-vector average only proposes a domain; every endpoint is subsequently
        // checked with WGS84. It is not used as a centroid distance or intersection witness.
        var reference=new Position(Math.toDegrees(Math.atan2(y,x)),Math.toDegrees(Math.atan2(z,Math.hypot(x,y))));
        var domain=new Domain(reference,budget);
        for (Position point : points) if (!domain.contains(point)) return relation(Kind.UNRESOLVED);
        return domain.relate(left,right,false);
    }

    /** A domain instance caches only verified positions and shares the caller's work budget. */
    static final class Domain {
        private final Position reference;
        private final Budget budget;
        private final Set<Position> checked=new HashSet<>();
        Domain(Position reference,Budget budget) {
            this.reference=canonical(reference);
            if (budget==null) throw new IllegalArgumentException("INVALID_GEODESIC_DISTANCE_WORK_LIMIT");
            this.budget=budget;
        }

        Relation relate(Arc left,Arc right) {
            return relate(left,right,true);
        }

        private Relation relate(Arc left,Arc right,boolean refineSeparation) {
            if (left==null||right==null) throw new IllegalArgumentException("GEODESIC_DISTANCE_COORDINATE_INVALID");
            Position a=check(left.start()), b=check(left.end()), c=check(right.start()), d=check(right.end());
            if (a.equals(b)) return pointRelation(a,c,d,refineSeparation);
            if (c.equals(d)) return pointRelation(c,a,b,refineSeparation);
            int common=(a.equals(c)||a.equals(d) ? 1 : 0)+(b.equals(c)||b.equals(d) ? 1 : 0);
            if (common==2) return new Relation(Kind.OVERLAP,POSITION_ORDER.compare(a,b)<=0 ? a : b);
            if (common==1) {
                Position contact=a.equals(c)||a.equals(d) ? a : b;
                Position first=a.equals(contact) ? b : a, second=c.equals(contact) ? d : c;
                if (sameAxis(contact,first,second))
                    return axisBetween(contact,first,second) ? new Relation(Kind.TOUCH,contact) : new Relation(Kind.OVERLAP,contact);
                double firstAzimuth=inverse(contact,first).azi1, secondAzimuth=inverse(contact,second).azi1;
                double change=Math.toRadians(secondAzimuth-firstAzimuth);
                // Opposite rays cannot overlap even when their tiny signed angle is uncertain.
                if (Math.cos(change)<0 || Math.abs(Math.sin(change))>ANGULAR_GUARD) return new Relation(Kind.TOUCH,contact);
                return relation(Kind.UNRESOLVED);
            }
            Side ac=side(a,b,c), ad=side(a,b,d), ca=side(c,d,a), cb=side(c,d,b);
            if (sameNonzero(ac,ad)||sameNonzero(ca,cb)) return relation(Kind.DISJOINT);
            if (opposite(ac,ad)&&opposite(ca,cb)) {
                Position contact = Wgs84GeodesicIntersection.crossing(new Arc(a,b),new Arc(c,d),budget);
                return new Relation(Kind.CROSS,contact);
            }
            // Structural meridians/equator have exact zero orientation; other near-collinear
            // coordinates remain UNKNOWN rather than being snapped onto the supporting arc.
            Position contact=null;
            int contacts=0;
            if (ac==Side.ZERO && axisBetween(c,a,b)) { contact=first(contact,c); contacts++; }
            if (ad==Side.ZERO && axisBetween(d,a,b)) { contact=first(contact,d); contacts++; }
            if (ca==Side.ZERO && axisBetween(a,c,d)) { contact=first(contact,a); contacts++; }
            if (cb==Side.ZERO && axisBetween(b,c,d)) { contact=first(contact,b); contacts++; }
            if (contacts>=2) return new Relation(Kind.OVERLAP,contact);
            if (contacts==1) return new Relation(Kind.TOUCH,contact);
            if (ac==Side.UNKNOWN||ad==Side.UNKNOWN||ca==Side.UNKNOWN||cb==Side.UNKNOWN)
                return refineSeparation ? separatedOrUnknown(a,b,c,d) : relation(Kind.UNRESOLVED);
            return relation(Kind.DISJOINT);
        }

        private Relation pointRelation(Position point,Position a,Position b,boolean refineSeparation) {
            if (point.equals(a)||point.equals(b)) return new Relation(Kind.TOUCH,point);
            if (a.equals(b)) return relation(Kind.DISJOINT);
            Side side=side(a,b,point);
            if (side==Side.ZERO) return axisBetween(point,a,b) ? new Relation(Kind.TOUCH,point) : relation(Kind.DISJOINT);
            if (side!=Side.UNKNOWN) return relation(Kind.DISJOINT);
            return refineSeparation ? separatedOrUnknown(point,point,a,b) : relation(Kind.UNRESOLVED);
        }

        private Relation separatedOrUnknown(Position a,Position b,Position c,Position d) {
            var distance=Wgs84SegmentDistance.nearest(a,b,c,d,0.0001,budget);
            return relation(distance.lowerBoundMetres()>0 ? Kind.DISJOINT : Kind.UNRESOLVED);
        }

        private Side side(Position a,Position b,Position point) {
            if (point.equals(a)||point.equals(b)||sameAxis(a,b,point)) return Side.ZERO;
            var edge=inverse(a,b); var query=inverse(a,point);
            double sine=Math.sin(Math.toRadians(query.azi1-edge.azi1));
            return Math.abs(sine)<=ANGULAR_GUARD ? Side.UNKNOWN : sine>0 ? Side.RIGHT : Side.LEFT;
        }

        private net.sf.geographiclib.GeodesicData inverse(Position a,Position b) {
            budget.consume(); return Geodesic.WGS84.Inverse(a.latitude(),a.longitude(),b.latitude(),b.longitude());
        }

        private Position check(Position point) {
            point=canonical(point);
            if (!contains(point)) throw new IllegalArgumentException("GEODESIC_DISTANCE_REGION_RANGE_NOT_SUPPORTED");
            return point;
        }

        private boolean contains(Position point) {
            if (!checked.contains(point)) {
                if (inverse(reference,point).s12>=TrackGeodesicHull.MAX_REFERENCE_RADIUS)
                    return false;
                checked.add(point);
            }
            return true;
        }
    }

    private static boolean sameAxis(Position a,Position b,Position point) {
        // Inside the verified domain an equatorial shortest arc cannot select a polar
        // antipodal branch. Every meridian is represented as a full closed great ellipse.
        if (a.latitude()==0 && b.latitude()==0 && point.latitude()==0) return true;
        Position base=Math.abs(a.latitude())!=90 ? a : b;
        return onMeridian(b,base.longitude()) && onMeridian(a,base.longitude()) && onMeridian(point,base.longitude());
    }

    private static boolean axisBetween(Position point,Position a,Position b) {
        if (a.latitude()==0 && b.latitude()==0) return cyclicBetween(point.longitude(),a.longitude(),b.longitude());
        double meridian=Math.abs(a.latitude())!=90 ? a.longitude() : b.longitude();
        return cyclicBetween(meridianPosition(point,meridian),meridianPosition(a,meridian),meridianPosition(b,meridian));
    }
    private static boolean onMeridian(Position point,double longitude) {
        return Math.abs(point.latitude())==90 || point.longitude()==longitude || Math.abs(point.longitude()-longitude)==180;
    }
    private static double meridianPosition(Position point,double longitude) {
        if (Math.abs(point.latitude())==90 || point.longitude()==longitude) return point.latitude();
        double value=180-point.latitude(); return value>180 ? value-360 : value;
    }
    private static boolean cyclicBetween(double point,double a,double b) {
        double low=Math.min(a,b), high=Math.max(a,b);
        return high-low<180 ? point>=low && point<=high : point<=low || point>=high;
    }
    private static boolean sameNonzero(Side a,Side b) { return a==b && (a==Side.LEFT||a==Side.RIGHT); }
    private static boolean opposite(Side a,Side b) { return a==Side.LEFT&&b==Side.RIGHT || a==Side.RIGHT&&b==Side.LEFT; }
    private static Position first(Position a,Position b) { return a==null || POSITION_ORDER.compare(b,a)<0 ? b : a; }
    private static Relation relation(Kind kind) { return new Relation(kind,null); }
    private static Position canonical(Position point) {
        if (point==null || !Double.isFinite(point.longitude()) || !Double.isFinite(point.latitude())
                || Math.abs(point.longitude())>180 || Math.abs(point.latitude())>90)
            throw new IllegalArgumentException("GEODESIC_DISTANCE_COORDINATE_INVALID");
        double longitude=Math.abs(point.latitude())==90 ? 0 : point.longitude()==180 ? -180 : point.longitude();
        return new Position(longitude==0 ? 0 : longitude,point.latitude()==0 ? 0 : point.latitude());
    }
}
