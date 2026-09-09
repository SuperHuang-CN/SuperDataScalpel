package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Arc;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.ArcBox;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Budget;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Position;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.strtree.STRtree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Continuous boundary relation, keeping unverified cases distinct from proven separation. */
final class Wgs84BoundaryIntersection {
    enum Relation { INTERSECTING, DISJOINT, UNRESOLVED }
    private Wgs84BoundaryIntersection() { }

    /** Caller must validate both complete arc collections before any positive shortcut. */
    static Relation relate(List<Arc> first,List<Arc> second,Budget budget) {
        var endpoints=new HashSet<Position>();
        for (Arc arc : first) {
            budget.consume(); endpoints.add(canonical(arc.start())); endpoints.add(canonical(arc.end()));
        }
        for (Arc arc : second) {
            budget.consume();
            if (endpoints.contains(canonical(arc.start()))||endpoints.contains(canonical(arc.end()))) return Relation.INTERSECTING;
        }
        var entries=new ArrayList<Entry>();
        double[] min={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
        double[] max={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for (Arc arc : second) {
            budget.consume();
            ArcBox box=Wgs84SegmentDistance.arcBox(arc);
            entries.add(new Entry(arc,box));
            for (int axis=0;axis<3;axis++) { min[axis]=Math.min(min[axis],box.min(axis)); max[axis]=Math.max(max[axis],box.max(axis)); }
        }
        int dropped=0;
        for (int axis=1;axis<3;axis++) if (max[axis]-min[axis]<max[dropped]-min[dropped]) dropped=axis;
        int x=(dropped+1)%3,y=(dropped+2)%3;
        var index=new STRtree();
        for (Entry entry : entries) index.insert(envelope(entry.box,x,y),entry);
        boolean unresolved=false;
        for (Arc arc : first) {
            budget.consume();
            ArcBox box=Wgs84SegmentDistance.arcBox(arc);
            for (Object candidate : index.query(envelope(box,x,y))) {
                budget.consume();
                Entry entry=(Entry)candidate;
                if (!box.overlaps(entry.box)) continue;
                var relation=Wgs84ArcTopology.probeLocal(arc,entry.arc,budget);
                switch (relation.kind()) {
                    case CROSS, TOUCH, OVERLAP -> { return Relation.INTERSECTING; }
                    case UNRESOLVED -> unresolved=true;
                    case DISJOINT -> { }
                }
            }
        }
        return unresolved ? Relation.UNRESOLVED : Relation.DISJOINT;
    }

    private static Position canonical(Position point) {
        double longitude=Math.abs(point.latitude())==90 ? 0 : point.longitude()==180 ? -180 : point.longitude();
        return new Position(longitude==0 ? 0 : longitude,point.latitude()==0 ? 0 : point.latitude());
    }
    private static Envelope envelope(ArcBox box,int x,int y) { return new Envelope(box.min(x),box.max(x),box.min(y),box.max(y)); }
    private record Entry(Arc arc,ArcBox box) { }
}
