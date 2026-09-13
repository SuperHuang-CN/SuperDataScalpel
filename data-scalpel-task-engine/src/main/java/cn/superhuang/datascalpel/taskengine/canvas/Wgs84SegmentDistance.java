package cn.superhuang.datascalpel.taskengine.canvas;

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicLine;
import net.sf.geographiclib.Constants;

import java.util.Comparator;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Internal shortest-geodesic segment distance, including degenerate point segments.
 * Searches the complete arc-length product, not just endpoints, centroids or a projection.
 * Operator callers consume only results whose global distance interval or discrete minimum is proven.
 */
final class Wgs84SegmentDistance {
    static final int MAX_EVALUATIONS = 250_000;
    // Engineering allowance for WGS84 double inverse/direct arithmetic, not interval arithmetic.
    static final double ROUNDOFF_METRES = 1e-6;
    // Minimum principal radius of curvature on WGS84 (equatorial meridional radius).
    private static final double MIN_CURVATURE_RADIUS=Constants.WGS84_a*Math.pow(1-Constants.WGS84_f,2);
    private static final Comparator<Position> POSITION_ORDER = Comparator.comparingDouble(Position::longitude)
            .thenComparingDouble(Position::latitude);
    private Wgs84SegmentDistance() { }

    record Position(double longitude, double latitude) { }
    record Arc(Position start, Position end) { }
    record ArcBox(double minX,double maxX,double minY,double maxY,double minZ,double maxZ) {
        double min(int axis) { return axis==0 ? minX : axis==1 ? minY : minZ; }
        double max(int axis) { return axis==0 ? maxX : axis==1 ? maxY : maxZ; }
        boolean overlaps(ArcBox other) {
            return minX<=other.maxX && maxX>=other.minX && minY<=other.maxY && maxY>=other.minY && minZ<=other.maxZ && maxZ>=other.minZ;
        }
    }

    static ArcBox arcBox(Arc arc) {
        return arcBox(new Segment(arc.start,arc.end));
    }

    private static ArcBox arcBox(Segment segment) {
        Vec a=ecef(segment.start), b=ecef(segment.end);
        // A unit-speed geodesic differs from its endpoint chord by at most L²/(8R).
        // Pad every Cartesian axis, never a sampled longitude/latitude envelope. This
        // covers polar and dateline arcs as well as the selected antipodal branch.
        double padding=segment.length*segment.length/(8*MIN_CURVATURE_RADIUS)+2*ROUNDOFF_METRES;
        return new ArcBox(Math.min(a.x,b.x)-padding,Math.max(a.x,b.x)+padding,
                Math.min(a.y,b.y)-padding,Math.max(a.y,b.y)+padding,
                Math.min(a.z,b.z)-padding,Math.max(a.z,b.z)+padding);
    }

    /** Distance is attained at the returned witnesses; lowerBound bounds unsampled locations. */
    record Result(Position first, Position second, double distanceMetres, double lowerBoundMetres, boolean exactMinimum) {
        Result(Position first,Position second,double distanceMetres,double lowerBoundMetres) {
            this(first,second,distanceMetres,lowerBoundMetres,false);
        }
        double uncertaintyMetres() { return distanceMetres + ROUNDOFF_METRES - lowerBoundMetres; }
    }

    /** Shared by subsequent edge pairs so a complex geometry cannot reset the work limit per edge. */
    static final class Budget {
        private int remaining;
        Budget(int maximumEvaluations) {
            if (maximumEvaluations < 1 || maximumEvaluations > MAX_EVALUATIONS)
                throw failure("INVALID_GEODESIC_DISTANCE_WORK_LIMIT");
            remaining = maximumEvaluations;
        }
        void consume() {
            if (remaining-- <= 0) throw failure("GEODESIC_DISTANCE_WORK_LIMIT_EXCEEDED");
        }
    }

    static Result nearest(Position a, Position b, Position c, Position d, double toleranceMetres) {
        return nearest(a,b,c,d,toleranceMetres,new Budget(MAX_EVALUATIONS));
    }

    static Result nearest(Position a, Position b, Position c, Position d, double toleranceMetres, Budget budget) {
        return nearestArcs(List.of(new Arc(a,b)),List.of(new Arc(c,d)),toleranceMetres,budget);
    }

    static Result nearestArcs(List<Arc> first, List<Arc> second, double toleranceMetres, Budget budget) {
        if (!Double.isFinite(toleranceMetres) || toleranceMetres < 4 * ROUNDOFF_METRES)
            throw failure("INVALID_GEODESIC_DISTANCE_PRECISION");
        return search(first,second,toleranceMetres,null,budget);
    }

    /** Decide inclusive distance <= threshold, without treating an approximate minimum as exact. */
    static boolean withinArcs(List<Arc> first,List<Arc> second,double thresholdMetres,Budget budget) {
        requireThreshold(thresholdMetres);
        validateArcs(first,second,budget);
        // A topology fact can prove distance zero without inventing a nearest coordinate.
        // Keep it separate from Result, whose positions must remain actual sampled witnesses.
        if (thresholdMetres<ROUNDOFF_METRES) {
            var relation=Wgs84BoundaryIntersection.relate(first,second,budget);
            if (relation==Wgs84BoundaryIntersection.Relation.INTERSECTING) return true;
            if (thresholdMetres==0 && relation==Wgs84BoundaryIntersection.Relation.DISJOINT) return false;
        }
        Result result=search(first,second,Double.NaN,thresholdMetres,budget);
        // Numeric search only returns after proving one side of the interval.
        return result.lowerBoundMetres<=thresholdMetres;
    }

    static void requireThreshold(double thresholdMetres) {
        if (!Double.isFinite(thresholdMetres)||thresholdMetres<0) throw failure("INVALID_GEODESIC_DISTANCE_THRESHOLD");
    }

    private static void validateArcs(List<Arc> first,List<Arc> second,Budget budget) {
        if (budget == null) throw failure("INVALID_GEODESIC_DISTANCE_WORK_LIMIT");
        if (first==null || second==null || first.isEmpty() || second.isEmpty()) throw failure("GEODESIC_DISTANCE_GEOMETRY_UNSUPPORTED");
        for (List<Arc> arcs : List.of(first,second)) for (Arc arc : arcs) {
            if (arc==null) throw failure("GEODESIC_DISTANCE_COORDINATE_INVALID");
            canonical(arc.start); canonical(arc.end);
        }
    }

    private static Result search(List<Arc> first,List<Arc> second,double toleranceMetres,Double thresholdMetres,Budget budget) {
        validateArcs(first,second,budget);
        boolean discrete=thresholdMetres==null && first.stream().allMatch(Wgs84SegmentDistance::isPoint)
                && second.stream().allMatch(Wgs84SegmentDistance::isPoint);
        var best=new Best();
        var cells = new WorkQueue();
        if (first.size()==1 && second.size()==1) {
            // Preserve the direct point/segment path and its one-sample point cost.
            Arc a=first.getFirst(), b=second.getFirst();
            initialize(new Search(new Segment(a.start,a.end),new Segment(b.start,b.end),budget,best),0,cells);
        } else {
            ArcTree left=tree(first,budget), right=tree(second,budget);
            // Seed only an attained upper bound. Every unvisited pair remains represented
            // by the tree pair below; this arbitrary seed never limits candidate recall.
            new Search(left.firstSegment(),right.firstSegment(),budget,best).sample(0,0);
            cells.add(pair(left,right,0));
        }
        while (!cells.isEmpty()) {
            Work work = cells.peek();
            if (discrete && work.lowerBound()>best.value.distance+ROUNDOFF_METRES)
                return best.result(best.value.distance,true);
            if (!discrete && finished(best,work.lowerBound(),toleranceMetres,thresholdMetres))
                return best.result(work.lowerBound(),false);
            cells.remove();
            if (work instanceof ArcPair pair) {
                budget.consume();
                ArcTree left=pair.left, right=pair.right;
                if (left.segment!=null && right.segment!=null) {
                    initialize(new Search(left.segment,right.segment,budget,best),pair.lowerBound,cells);
                } else if (right.segment!=null || left.segment==null && left.size>=right.size) {
                    cells.add(pair(left.low,right,pair.lowerBound));
                    cells.add(pair(left.high,right,pair.lowerBound));
                } else {
                    cells.add(pair(left,right.low,pair.lowerBound));
                    cells.add(pair(left,right.high,pair.lowerBound));
                }
                continue;
            }
            Cell cell=(Cell)work;
            // A degenerate point pair was fully evaluated when this terminal cell was
            // created. Removing it proves that no unvisited position remains in the pair.
            if (discrete && cell.lowFirst==cell.highFirst && cell.lowSecond==cell.highSecond) continue;
            Search search=cell.search;
            if (cell.highFirst-cell.lowFirst >= cell.highSecond-cell.lowSecond) {
                double middle = cell.lowFirst + (cell.highFirst-cell.lowFirst)/2;
                if (middle == cell.lowFirst || middle == cell.highFirst) throw failure("GEODESIC_DISTANCE_PRECISION_NOT_REACHED");
                cells.add(search.cell(cell.lowFirst,middle,cell.lowSecond,cell.highSecond,cell.lowerBound));
                cells.add(search.cell(middle,cell.highFirst,cell.lowSecond,cell.highSecond,cell.lowerBound));
            } else {
                double middle = cell.lowSecond + (cell.highSecond-cell.lowSecond)/2;
                if (middle == cell.lowSecond || middle == cell.highSecond) throw failure("GEODESIC_DISTANCE_PRECISION_NOT_REACHED");
                cells.add(search.cell(cell.lowFirst,cell.highFirst,cell.lowSecond,middle,cell.lowerBound));
                cells.add(search.cell(cell.lowFirst,cell.highFirst,middle,cell.highSecond,cell.lowerBound));
            }
        }
        return best.result(best.value.distance,discrete);
    }

    private static void initialize(Search search,double parentLower,WorkQueue cells) {
        double a=search.first.length, b=search.second.length;
        var lowLow=search.sample(0,0);
        if (a==0 && b==0) {
            cells.add(new Cell(search,0,0,0,0,Math.max(parentLower,Math.max(0,lowLow.distance-ROUNDOFF_METRES))));
            return;
        }
        var highHigh=search.sample(a,b);
        var lowHigh=search.sample(0,b);
        var highLow=search.sample(a,0);
        double lower=Math.max(parentLower,Math.max(0,Math.max(lowLow.distance+highHigh.distance,lowHigh.distance+highLow.distance)
                /2-(a+b)/2-2*ROUNDOFF_METRES));
        cells.add(search.cell(0,a,0,b,lower));
    }

    private static ArcTree tree(List<Arc> arcs,Budget budget) {
        var leaves=new ArcTree[arcs.size()];
        for (int i=0;i<leaves.length;i++) {
            budget.consume();
            Arc arc=arcs.get(i);
            Segment segment=new Segment(arc.start,arc.end);
            leaves[i]=new ArcTree(arcBox(segment),segment,null,null,1);
        }
        return tree(leaves,0,leaves.length,budget);
    }

    private static ArcTree tree(ArcTree[] leaves,int start,int end,Budget budget) {
        if (end-start==1) return leaves[start];
        budget.consume();
        ArcBox box=leaves[start].box;
        for (int i=start+1;i<end;i++) box=union(box,leaves[i].box);
        int axis=0;
        for (int i=1;i<3;i++) if (box.max(i)-box.min(i)>box.max(axis)-box.min(axis)) axis=i;
        final int splitAxis=axis;
        Arrays.sort(leaves,start,end,Comparator.comparingDouble(value -> value.box.min(splitAxis)+value.box.max(splitAxis)));
        int middle=start+(end-start)/2;
        return new ArcTree(box,null,tree(leaves,start,middle,budget),tree(leaves,middle,end,budget),end-start);
    }

    private static ArcBox union(ArcBox a,ArcBox b) {
        return new ArcBox(Math.min(a.minX,b.minX),Math.max(a.maxX,b.maxX),Math.min(a.minY,b.minY),Math.max(a.maxY,b.maxY),
                Math.min(a.minZ,b.minZ),Math.max(a.maxZ,b.maxZ));
    }

    private static ArcPair pair(ArcTree left,ArcTree right,double parentLower) {
        double squared=0;
        for (int axis=0;axis<3;axis++) {
            double gap=Math.max(0,Math.max(left.box.min(axis)-right.box.max(axis),right.box.min(axis)-left.box.max(axis)));
            squared+=gap*gap;
        }
        // ECEF box separation <= chord distance <= surface distance, including unsampled
        // edges represented by internal tree nodes. Rounding allowance is not a threshold.
        return new ArcPair(left,right,Math.max(parentLower,Math.max(0,Math.sqrt(squared)-2*ROUNDOFF_METRES)));
    }

    private static boolean finished(Best best,double lower,double tolerance,Double threshold) {
        double upper=best.value.distance+ROUNDOFF_METRES;
        return threshold==null ? upper-lower<=tolerance : upper<=threshold || lower>threshold;
    }

    private static boolean isPoint(Arc arc) {
        return canonical(arc.start).equals(canonical(arc.end));
    }

    private static final class Search {
        private final Segment first, second;
        private final Budget budget;
        private final Best best;
        private Search(Segment first, Segment second, Budget budget, Best best) {
            this.first=first; this.second=second; this.budget=budget; this.best=best;
        }

        private Sample sample(double s, double t) {
            budget.consume();
            Position a = first.position(s), b = second.position(t);
            double distance = Geodesic.WGS84.Inverse(a.latitude,a.longitude,b.latitude,b.longitude).s12;
            if (!Double.isFinite(distance) || distance < 0) throw failure("GEODESIC_DISTANCE_COORDINATE_INVALID");
            var sample = new Sample(a,b,distance);
            if (best.value == null || distance < best.value.distance || distance == best.value.distance && compare(sample,best.value) < 0) best.value=sample;
            return sample;
        }

        private Cell cell(double s0, double s1, double t0, double t1, double parentLower) {
            var middle = sample(s0+(s1-s0)/2,t0+(t1-t0)/2);
            // Distance is 1-Lipschitz in either arc-length parameter by the metric triangle
            // inequality. This lower bound applies everywhere inside the cell, including
            // unsampled interior minima and segment crossings.
            double lower = Math.max(parentLower,Math.max(0,middle.distance-(s1-s0)/2-(t1-t0)/2-2*ROUNDOFF_METRES));
            // An ambient chord is ONLY a lower-bound device, never the returned distance.
            // A unit-speed surface geodesic has ECEF acceleration <= 1/minCurvatureRadius.
            // Its deviation from linear endpoint interpolation is <= length^2/(8*radius).
            // Subtract both deviations from the segment-to-segment chord distance. This is
            // tight for short near-parallel arcs where midpoint Lipschitz cones converge slowly.
            double chord=chordLowerBound(ecef(first.position(s0)),ecef(first.position(s1)),
                    ecef(second.position(t0)),ecef(second.position(t1)));
            double deviation=((s1-s0)*(s1-s0)+(t1-t0)*(t1-t0))/(8*MIN_CURVATURE_RADIUS);
            lower=Math.max(lower,Math.max(0,chord-deviation-2*ROUNDOFF_METRES));
            return new Cell(this,s0,s1,t0,t1,lower);
        }
    }

    private static final class Best {
        private Sample value;
        private Result result(double lower,boolean exact) {
            return new Result(value.first,value.second,value.distance,exact ? value.distance : Math.min(lower,value.distance),exact);
        }
    }

    private static final class Segment {
        private final Position start, end;
        private final GeodesicLine line;
        private final double length;
        private Segment(Position a, Position b) {
            a = canonical(a); b = canonical(b);
            // Reversing an input edge must not choose a different antipodal geodesic or tie.
            if (POSITION_ORDER.compare(a,b) <= 0) { start=a; end=b; } else { start=b; end=a; }
            line = Geodesic.WGS84.InverseLine(start.latitude,start.longitude,end.latitude,end.longitude);
            length = line.Distance();
            if (!Double.isFinite(length)) throw failure("GEODESIC_DISTANCE_COORDINATE_INVALID");
        }
        private Position position(double distance) {
            if (distance == 0) return start;
            if (distance == length) return end;
            var position = line.Position(distance);
            return canonical(new Position(position.lon2,position.lat2));
        }
    }

    private static Position canonical(Position value) {
        if (value == null || !Double.isFinite(value.longitude) || !Double.isFinite(value.latitude)
                || Math.abs(value.longitude)>180 || Math.abs(value.latitude)>90)
            throw failure("GEODESIC_DISTANCE_COORDINATE_INVALID");
        double longitude = Math.abs(value.latitude)==90 ? 0 : value.longitude==180 ? -180 : value.longitude;
        return new Position(longitude==0 ? 0 : longitude,value.latitude==0 ? 0 : value.latitude);
    }

    private static int compare(Sample a, Sample b) {
        int order = POSITION_ORDER.compare(a.first,b.first);
        return order == 0 ? POSITION_ORDER.compare(a.second,b.second) : order;
    }
    private static Vec ecef(Position position) {
        double latitude=Math.toRadians(position.latitude), longitude=Math.toRadians(position.longitude);
        double eccentricitySquared=Constants.WGS84_f*(2-Constants.WGS84_f);
        double sin=Math.sin(latitude), cos=Math.cos(latitude);
        double normal=Constants.WGS84_a/Math.sqrt(1-eccentricitySquared*sin*sin);
        return new Vec(normal*cos*Math.cos(longitude),normal*cos*Math.sin(longitude),normal*(1-eccentricitySquared)*sin);
    }
    private static double chordLowerBound(Vec a,Vec b,Vec c,Vec d) {
        // A separation between the two projected intervals on ANY unit axis is a lower
        // bound on their Euclidean distance. Do not use a closest-segment routine that can
        // overestimate distance after clamping a parameter; an upper bound is unsafe here.
        // Endpoint-normal axes and the common normal include the usual closest-pair normal,
        // but correctness does not depend on finding it: a suboptimal axis only prunes less.
        Vec u=b.minus(a), v=d.minus(c);
        Vec[] axes={a.minus(closest(a,c,d)),b.minus(closest(b,c,d)),c.minus(closest(c,a,b)),d.minus(closest(d,a,b)),
                u.cross(v),c.minus(a).plus(d.minus(b))};
        double lower=0;
        for (Vec axis : axes) {
            double norm=axis.norm();
            if (norm==0) continue;
            Vec unit=axis.times(1/norm);
            double endpoint=u.dot(unit), first=c.minus(a).dot(unit), second=d.minus(a).dot(unit);
            double gap=Math.max(Math.min(first,second)-Math.max(0,endpoint),Math.min(0,endpoint)-Math.max(first,second));
            lower=Math.max(lower,gap);
        }
        if (!Double.isFinite(lower)) throw failure("GEODESIC_DISTANCE_PRECISION_NOT_REACHED");
        return lower;
    }
    private static Vec closest(Vec point,Vec a,Vec b) {
        Vec direction=b.minus(a);
        double lengthSquared=direction.dot(direction);
        if (lengthSquared==0) return a;
        double parameter=Math.max(0,Math.min(1,point.minus(a).dot(direction)/lengthSquared));
        return a.plus(direction.times(parameter));
    }
    private record Vec(double x,double y,double z) {
        Vec minus(Vec other) { return new Vec(x-other.x,y-other.y,z-other.z); }
        Vec plus(Vec other) { return new Vec(x+other.x,y+other.y,z+other.z); }
        Vec times(double factor) { return new Vec(x*factor,y*factor,z*factor); }
        double dot(Vec other) { return x*other.x+y*other.y+z*other.z; }
        Vec cross(Vec other) { return new Vec(y*other.z-z*other.y,z*other.x-x*other.z,x*other.y-y*other.x); }
        double norm() { return Math.hypot(Math.hypot(x,y),z); }
    }
    private record Sample(Position first, Position second, double distance) { }
    private sealed interface Work permits Cell,ArcPair { double lowerBound(); }
    private record Cell(Search search, double lowFirst, double highFirst, double lowSecond, double highSecond, double lowerBound) implements Work { }
    private record ArcPair(ArcTree left,ArcTree right,double lowerBound) implements Work { }
    private record ArcTree(ArcBox box,Segment segment,ArcTree low,ArcTree high,int size) {
        Segment firstSegment() { return segment!=null ? segment : low.firstSegment(); }
    }
    /** Stable insertion order breaks equal lower bounds without depending on object identity. */
    private static final class WorkQueue {
        private long sequence;
        private final PriorityQueue<Queued> queue=new PriorityQueue<>(Comparator.comparingDouble((Queued item)->item.work.lowerBound())
                .thenComparingLong(Queued::sequence));
        void add(Work work) { queue.add(new Queued(work,sequence++)); }
        boolean isEmpty() { return queue.isEmpty(); }
        Work peek() { return queue.element().work; }
        void remove() { queue.remove(); }
        private record Queued(Work work,long sequence) { }
    }
    private static IllegalArgumentException failure(String code) { return new IllegalArgumentException(code); }
}
