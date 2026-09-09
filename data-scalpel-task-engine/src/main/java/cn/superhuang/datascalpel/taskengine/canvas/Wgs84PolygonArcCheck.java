package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.TrackGeodesicAreaBoundary.Vertex;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Arc;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.ArcBox;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Budget;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Position;
import net.sf.geographiclib.Geodesic;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.strtree.STRtree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reject continuous boundary crossings before any sampled-render topology is considered. */
final class Wgs84PolygonArcCheck {
    private Wgs84PolygonArcCheck() { }

    /** Returns independent rings noded only at confirmed contacts, never at approximate intersections. */
    static List<List<Vertex>> validate(List<List<Vertex>> rings,Vertex reference) {
        return validate(rings,reference,new Budget(Wgs84SegmentDistance.MAX_EVALUATIONS));
    }

    static List<List<Vertex>> validate(List<List<Vertex>> rings,Vertex reference,Budget budget) {
        var topology=new Wgs84ArcTopology.Domain(position(reference),budget);
        var entries=new ArrayList<Entry>();
        var contacts=new HashMap<Integer,Set<Position>>();
        double[] minimum={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
        double[] maximum={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for (int r=0;r<rings.size();r++) {
            List<Vertex> ring=rings.get(r);
            for (int i=0;i<ring.size()-1;i++) {
                budget.consume();
                var arc=new Arc(position(ring.get(i)),position(ring.get(i+1)));
                ArcBox box=Wgs84SegmentDistance.arcBox(arc);
                entries.add(new Entry(entries.size(),r,i,ring.size()-1,arc,box));
                for (int axis=0;axis<3;axis++) {
                    minimum[axis]=Math.min(minimum[axis],box.min(axis)); maximum[axis]=Math.max(maximum[axis],box.max(axis));
                }
            }
        }
        // Index the two largest ECEF extents; always check all three axes after querying.
        // This is a safe broad phase, not an alternate geodesic intersection predicate.
        int dropped=0;
        for (int axis=1;axis<3;axis++) if (maximum[axis]-minimum[axis]<maximum[dropped]-minimum[dropped]) dropped=axis;
        int x=(dropped+1)%3, y=(dropped+2)%3;
        var index=new STRtree();
        for (Entry entry : entries) index.insert(envelope(entry.box,x,y),entry);
        for (Entry entry : entries) for (Object value : index.query(envelope(entry.box,x,y))) {
            budget.consume();
            Entry other=(Entry)value;
            if (other.id<=entry.id || !entry.box.overlaps(other.box)) continue;
            var relation=topology.relate(entry.arc,other.arc);
            switch (relation.kind()) {
                case CROSS, OVERLAP -> throw new IllegalArgumentException("TRACK_AREA_GEOMETRY_INVALID");
                case UNRESOLVED -> throw new IllegalArgumentException("GEODESIC_TOPOLOGY_PRECISION_NOT_REACHED");
                case TOUCH -> {
                    int difference=Math.abs(entry.edge-other.edge);
                    boolean adjacent=entry.ring==other.ring && (difference==1 || difference==entry.edgeCount-1);
                    if (entry.ring==other.ring && !adjacent) throw new IllegalArgumentException("TRACK_AREA_GEOMETRY_INVALID");
                    // Contacts between distinct rings are left to region/connected-interior
                    // validation. A shared endpoint is not automatically a crossing.
                    if (entry.ring!=other.ring) {
                        addContact(entry,relation.contact(),contacts);
                        addContact(other,relation.contact(),contacts);
                    }
                }
                case DISJOINT -> { }
            }
        }
        // Preserve a real T-contact as a common vertex in both sampled boundaries. Without
        // this noding the chart's straight chord may move the contact outside its shell.
        var result=new ArrayList<List<Vertex>>(rings.size());
        int entryIndex=0;
        for (List<Vertex> ring : rings) {
            var noded=new ArrayList<Vertex>();
            for (int edge=0;edge<ring.size()-1;edge++) {
                Entry entry=entries.get(entryIndex++);
                noded.add(ring.get(edge));
                var ordered=new ArrayList<Contact>();
                for (Position contact : contacts.getOrDefault(entry.id,Set.of())) {
                    budget.consume();
                    double distance=Geodesic.WGS84.Inverse(entry.arc.start().latitude(),entry.arc.start().longitude(),
                            contact.latitude(),contact.longitude()).s12;
                    ordered.add(new Contact(contact,distance));
                }
                ordered.sort(Comparator.comparingDouble(Contact::distance));
                for (Contact contact : ordered) noded.add(new Vertex(contact.point.longitude(),contact.point.latitude()));
            }
            noded.add(noded.getFirst());
            result.add(List.copyOf(noded));
        }
        return List.copyOf(result);
    }

    private static void addContact(Entry entry,Position contact,Map<Integer,Set<Position>> contacts) {
        if (samePosition(contact,entry.arc.start()) || samePosition(contact,entry.arc.end())) return;
        contacts.computeIfAbsent(entry.id,ignored->new LinkedHashSet<>()).add(contact);
    }
    private static boolean samePosition(Position a,Position b) {
        return a.latitude()==b.latitude() && (Math.abs(a.latitude())==90
                || a.longitude()==b.longitude() || Math.abs(a.longitude()-b.longitude())==360);
    }
    private static Envelope envelope(ArcBox box,int x,int y) { return new Envelope(box.min(x),box.max(x),box.min(y),box.max(y)); }
    private static Position position(Vertex vertex) { return new Position(vertex.longitude(),vertex.latitude()); }
    private record Entry(int id,int ring,int edge,int edgeCount,Arc arc,ArcBox box) { }
    private record Contact(Position point,double distance) { }
}
