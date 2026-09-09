package cn.superhuang.datascalpel.taskengine.canvas;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class HdbscanHierarchyTest {
    @Test void condensesSmallBranchesAndProducesAllFourDiagnostics() {
        var result = HdbscanHierarchy.extract(ids(7), List.of(edge(0,1,1),edge(1,2,2),edge(3,4,1),
                edge(4,5,2),edge(2,3,10),edge(5,6,20)), 2);
        assertEquals(2,result.selectedClusters());
        var rows = rows(result);
        for (long i : List.of(0L,1L,2L)) assertEquals(0L,rows.get(i).cluster());
        for (long i : List.of(3L,4L,5L)) assertEquals(3L,rows.get(i).cluster());
        assertNull(rows.get(6L).cluster());assertEquals(0,rows.get(6L).probability());
        assertNull(rows.get(6L).stability());assertFalse(rows.get(6L).exemplar());
        assertEquals(.95,rows.get(6L).outlier(),1e-12);
        assertEquals(1,rows.get(0L).probability());assertEquals(0,rows.get(0L).outlier());assertTrue(rows.get(0L).exemplar());
        assertEquals(.5,rows.get(2L).probability(),1e-12);assertEquals(.5,rows.get(2L).outlier(),1e-12);
        assertFalse(rows.get(2L).exemplar());assertEquals(2.2/3,rows.get(2L).stability(),1e-12);
    }

    @Test void eomChoosesParentOrDescendantsByPersistenceNotByDensityOrLeafCount() {
        for (double separation : List.of(1.1,2d)) {
            var mst = new ArrayList<>(List.of(edge(0,1,1),edge(1,2,1),edge(3,4,1),edge(4,5,1),
                    edge(6,7,1),edge(7,8,1),edge(2,3,separation),edge(5,6,10)));
            var result=HdbscanHierarchy.extract(ids(9),mst,2);
            assertEquals(separation==1.1 ? 2:3,result.selectedClusters());
            var rows=rows(result);
            assertEquals(separation==1.1,rows.get(0L).cluster().equals(rows.get(3L).cluster()));
            for(var row:result.assignments()) {
                assertEquals(1,row.probability());assertTrue(row.exemplar());assertEquals(0,row.outlier());
            }
        }
    }

    @Test void equalWeightsAreSimultaneousAndCannotCreateArbitraryZeroLifetimeClusters() {
        for(var edges:List.of(List.of(edge(0,1,1),edge(1,2,1),edge(2,3,1)),
                List.of(edge(0,1,1),edge(0,2,1),edge(0,3,1)))) {
            var result=HdbscanHierarchy.extract(ids(4),edges,2);
            assertEquals(0,result.selectedClusters());
            result.assignments().forEach(row->{assertNull(row.cluster());assertEquals(0,row.outlier());});
        }
    }

    @Test void vertexOrderEdgeDirectionAndTiedEdgeOrderDoNotChangeMembershipOrDiagnostics() {
        var edges=new ArrayList<>(List.of(edge(0,1,1),edge(1,2,2),edge(3,4,1),edge(4,5,2),edge(2,3,10),edge(5,6,20)));
        var expected=HdbscanHierarchy.extract(ids(7),edges,2);
        var vertices=new ArrayList<>(ids(7));
        for(int seed=0;seed<20;seed++) {
            Collections.shuffle(vertices,new Random(seed));Collections.shuffle(edges,new Random(seed+31));
            var reversed=edges.stream().map(e->edge(e.second(),e.first(),e.distance())).toList();
            assertEquals(expected,HdbscanHierarchy.extract(vertices,reversed,2));
        }
        assertThrows(UnsupportedOperationException.class,()->expected.assignments().clear());
    }

    @Test void duplicateLocationsRetainSeparateIdentitiesAndHaveFiniteLimitDiagnostics() {
        var result=HdbscanHierarchy.extract(ids(5),List.of(edge(0,1,0),edge(2,3,1),edge(1,2,10),edge(3,4,20)),2);
        assertEquals(2,result.selectedClusters());
        var rows=rows(result);
        for(long id:List.of(0L,1L)) {
            assertEquals(0L,rows.get(id).cluster());assertEquals(1,rows.get(id).probability());
            assertEquals(0,rows.get(id).outlier());assertEquals(1,rows.get(id).stability());assertTrue(rows.get(id).exemplar());
        }
        assertEquals(0,rows.get(2L).stability(),"finite persistence over an infinite global density range has zero normalized limit");
        assertNull(rows.get(4L).cluster());assertEquals(1,rows.get(4L).outlier());
        var coincident=HdbscanHierarchy.extract(ids(4),List.of(edge(0,1,0),edge(1,2,-0d),edge(2,3,0)),2);
        assertEquals(0,coincident.selectedClusters(),"do not implicitly select the root cluster");
        coincident.assignments().forEach(row->{assertEquals(0,row.outlier());assertNull(row.stability());});
    }

    @Test void multiplyingAllDistancesDoesNotChangeNormalizedDiagnostics() {
        var edges=List.of(edge(0,1,1),edge(1,2,2),edge(3,4,1),edge(4,5,2),edge(2,3,10),edge(5,6,20));
        var expected=HdbscanHierarchy.extract(ids(7),edges,2);
        for(double scale:List.of(1e-200,1e200)) {
            var result=HdbscanHierarchy.extract(ids(7),edges.stream().map(e->edge(e.first(),e.second(),e.distance()*scale)).toList(),2);
            for(int i=0;i<7;i++) {
                var a=expected.assignments().get(i);var b=result.assignments().get(i);
                assertEquals(a.cluster(),b.cluster());assertEquals(a.probability(),b.probability(),1e-12);
                assertEquals(a.outlier(),b.outlier(),1e-12);assertEquals(a.exemplar(),b.exemplar());
                if(a.stability()!=null)assertEquals(a.stability(),b.stability(),1e-12);
            }
        }
    }

    @Test void validatesCompleteTreeEvenWhenThereAreTooFewPointsToFormClusters() {
        for(var edges:List.of(List.of(edge(0,1,1)),List.of(edge(0,1,1),edge(1,0,1)),
                List.of(edge(0,9,1),edge(1,2,1)),List.of(edge(0,0,1),edge(1,2,1)),
                List.of(edge(0,1,Double.NaN),edge(1,2,1)),List.of(edge(0,1,-1),edge(1,2,1))))
            assertCode("SPATIAL_HDBSCAN_TREE_INVALID",()->HdbscanHierarchy.extract(ids(3),edges,10));
        assertCode("SPATIAL_HDBSCAN_TREE_INVALID",()->HdbscanHierarchy.extract(List.of(0L,0L),List.of(edge(0,0,1)),2));
        assertCode("INVALID_SPATIAL_CLUSTER_MINIMUM_FEATURES",()->HdbscanHierarchy.extract(ids(3),List.of(),1));
        assertCode("SPATIAL_HDBSCAN_HIERARCHY_LIMIT_EXCEEDED",()->HdbscanHierarchy.extract(
                Collections.nCopies(HdbscanHierarchy.MAX_VERTICES+1,0L),List.of(),2));
        assertCode("SPATIAL_HDBSCAN_NUMERIC_RANGE_INVALID",()->HdbscanHierarchy.extract(ids(3),
                List.of(edge(0,1,Double.MIN_VALUE),edge(1,2,Double.MAX_VALUE)),2));
        assertTrue(HdbscanHierarchy.extract(List.of(),List.of(),2).assignments().isEmpty());
        assertNull(HdbscanHierarchy.extract(List.of(Long.MAX_VALUE),List.of(),2).assignments().getFirst().cluster());
        assertEquals(0,HdbscanHierarchy.extract(ids(3),List.of(edge(0,1,1),edge(1,2,2)),4).selectedClusters());
    }

    @Test void deepUnbalancedHierarchyDoesNotUseRecursiveTraversalOrRepeatedLeafCollections() {
        int count=30_000;
        var edges=new ArrayList<HdbscanHierarchy.Edge>();
        for(int i=1;i<count;i++) edges.add(edge(i-1,i,i));
        var result=HdbscanHierarchy.extract(ids(count),edges,2);
        assertEquals(count,result.assignments().size());assertEquals(0,result.selectedClusters());
        assertEquals(1-1d/(count-1),result.assignments().getLast().outlier(),1e-12);
    }

    private static List<Long> ids(int count) { return LongStream.range(0,count).boxed().toList(); }
    private static HdbscanHierarchy.Edge edge(long a,long b,double d) { return new HdbscanHierarchy.Edge(a,b,d); }
    private static Map<Long,HdbscanHierarchy.Assignment> rows(HdbscanHierarchy.Result result) {
        return result.assignments().stream().collect(Collectors.toMap(HdbscanHierarchy.Assignment::vertex,row->row));
    }
    private static void assertCode(String code,Runnable run) {
        assertEquals(code,assertThrows(IllegalArgumentException.class,run::run).getMessage());
    }
}
