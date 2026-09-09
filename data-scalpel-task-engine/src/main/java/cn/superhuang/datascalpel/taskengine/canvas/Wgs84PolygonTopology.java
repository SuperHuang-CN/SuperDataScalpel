package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.TrackGeodesicAreaBoundary.Vertex;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84PolygonRegion.Location;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Budget;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Position;
import net.sf.geographiclib.Geodesic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Region topology on already-simple, contact-noded WGS84 rings in one verified local domain. */
final class Wgs84PolygonTopology {
    private final List<List<Vertex>> rings;
    private final List<List<Position>> positions;
    private final List<Part> parts;
    private final Budget budget;
    private final Map<Long,List<Integer>> contactStarts=new HashMap<>();
    private final Map<Long,Boolean> containment=new HashMap<>();

    private Wgs84PolygonTopology(List<List<Vertex>> rings,List<Integer> holeCounts,Budget budget) {
        this.rings=rings; this.budget=budget;
        positions=rings.stream().map(ring->ring.stream().map(Wgs84PolygonTopology::position).toList()).toList();
        parts=new ArrayList<>();
        int offset=0;
        for (int holes : holeCounts) { parts.add(new Part(offset,offset+holes+1)); offset+=holes+1; }
        if (offset!=rings.size()) throw invalid();
    }

    static void validate(List<List<Vertex>> rings,List<Integer> holeCounts,Budget budget) {
        new Wgs84PolygonTopology(rings,holeCounts,budget).validate();
    }

    private void validate() {
        var vertices=new LinkedHashMap<Vertex,List<Occurrence>>();
        for (int ring=0;ring<rings.size();ring++) for (int vertex=0;vertex<rings.get(ring).size()-1;vertex++) {
            budget.consume();
            vertices.computeIfAbsent(rings.get(ring).get(vertex),ignored->new ArrayList<>()).add(new Occurrence(ring,vertex));
        }
        var contacts=new ArrayList<List<Occurrence>>();
        for (var occurrences : vertices.values()) if (occurrences.size()>1) {
            contacts.add(occurrences);
            for (Occurrence first : occurrences) for (Occurrence second : occurrences) if (first.ring!=second.ring) {
                budget.consume();
                contactStarts.computeIfAbsent(pair(first.ring,second.ring),ignored->new ArrayList<>()).add(first.vertex);
            }
        }
        // A boundary can cross another ring through a shared vertex without any proper
        // edge-edge crossing. Locate each open arc between contacts, not just one vertex.
        for (long pair : contactStarts.keySet()) inside((int)(pair>>>32),(int)pair);
        for (Part part : parts) {
            for (int hole=part.shell+1;hole<part.end;hole++) {
                if (!inside(hole,part.shell)) throw invalid();
                for (int other=part.shell+1;other<hole;other++)
                    if (inside(hole,other)||inside(other,hole)) throw invalid();
            }
            requireConnectedInterior(part,contacts);
        }
        for (int first=0;first<parts.size();first++) for (int second=first+1;second<parts.size();second++) {
            requireOutsideFilledRegion(parts.get(first),parts.get(second));
            requireOutsideFilledRegion(parts.get(second),parts.get(first));
        }
    }

    private void requireOutsideFilledRegion(Part candidate,Part container) {
        if (!inside(candidate.shell,container.shell)) return;
        // A component may be an island inside another component's hole, but not inside
        // its filled region. Boundary crossings (including at contact vertices) are checked first.
        for (int hole=container.shell+1;hole<container.end;hole++) if (inside(candidate.shell,hole)) return;
        throw invalid();
    }

    private boolean inside(int candidate,int container) {
        long key=pair(candidate,container);
        Boolean known=containment.get(key);
        if (known!=null) return known;
        List<Integer> starts=contactStarts.get(key);
        Boolean result=null;
        if (starts==null) {
            // Disjoint simple Jordan boundaries have constant containment. A single
            // resolved original vertex suffices; do not guess when a bearing is uncertain.
            for (int index=0;index<positions.get(candidate).size()-1 && result==null;index++) {
                Location location=Wgs84PolygonRegion.locateRing(positions.get(candidate).get(index),positions.get(container),budget);
                if (location==Location.INSIDE||location==Location.OUTSIDE) result=location==Location.INSIDE;
            }
        } else {
            for (int start : starts) {
                List<Position> ring=positions.get(candidate);
                Position a=ring.get(start), b=ring.get(start+1);
                budget.consume();
                var edge=Geodesic.WGS84.InverseLine(a.latitude(),a.longitude(),b.latitude(),b.longitude());
                Boolean side=null;
                for (double fraction : new double[]{0.5,0.25,0.75}) {
                    budget.consume();
                    var point=edge.Position(edge.Distance()*fraction);
                    Location location=Wgs84PolygonRegion.locateRing(new Position(point.lon2,point.lat2),positions.get(container),budget);
                    if (location==Location.INSIDE||location==Location.OUTSIDE) { side=location==Location.INSIDE; break; }
                }
                if (side==null) throw uncertain();
                if (result!=null && !result.equals(side)) throw invalid();
                result=side;
            }
        }
        if (result==null) throw uncertain();
        containment.put(key,result); return result;
    }

    private void requireConnectedInterior(Part part,List<List<Occurrence>> contacts) {
        // Bipartite ring/contact graph: three rings at one point form a star, not a
        // false ring-pair cycle. A genuine cycle cuts off a piece of polygon interior.
        int ringCount=part.end-part.shell;
        int[] parent=new int[ringCount+contacts.size()];
        for (int index=0;index<parent.length;index++) parent[index]=index;
        for (int contact=0;contact<contacts.size();contact++) {
            budget.consume();
            int joint=ringCount+contact;
            for (Occurrence occurrence : contacts.get(contact)) if (occurrence.ring>=part.shell && occurrence.ring<part.end) {
                budget.consume();
                int ringRoot=root(parent,occurrence.ring-part.shell), contactRoot=root(parent,joint);
                if (ringRoot==contactRoot) throw invalid();
                parent[ringRoot]=contactRoot;
            }
        }
    }

    private static int root(int[] parent,int index) {
        while (parent[index]!=index) { parent[index]=parent[parent[index]]; index=parent[index]; }
        return index;
    }
    private static long pair(int first,int second) { return ((long)first<<32)|(second&0xffffffffL); }
    private static Position position(Vertex vertex) { return new Position(vertex.longitude(),vertex.latitude()); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("TRACK_AREA_GEOMETRY_INVALID"); }
    private static IllegalArgumentException uncertain() { return new IllegalArgumentException("GEODESIC_TOPOLOGY_PRECISION_NOT_REACHED"); }
    private record Occurrence(int ring,int vertex) { }
    private record Part(int shell,int end) { }
}
