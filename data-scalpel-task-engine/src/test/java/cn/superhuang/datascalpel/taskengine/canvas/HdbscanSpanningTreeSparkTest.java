package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import org.apache.spark.sql.*;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.util.*;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HdbscanSpanningTreeSparkTest {
    private SparkSession spark;
    @BeforeAll void start() throws Exception {
        spark=SedonaSparkSupport.initialize(SedonaSparkSupport.builder().master("local[2]").appName("hdbscan-mst")
                .config("spark.ui.enabled",false).config("spark.driver.host","127.0.0.1").config("spark.driver.bindAddress","127.0.0.1")
                .config("spark.sql.shuffle.partitions",2).getOrCreate());
        spark.sparkContext().setCheckpointDir(Files.createTempDirectory("datascalpel-hdbscan-test-").toUri().toString());
    }
    @AfterAll void stop() { if(spark!=null)spark.stop(); }

    @Test void densityIncludesSelfAndMutualReachabilityIsTheMaximumOfThreeDistances() {
        var points=points(new double[][]{{0,0},{1,0},{3,0}});
        String job=UUID.randomUUID().toString();spark.sparkContext().setJobGroup(job,"hdbscan analyzer",false);
        HdbscanSpanningTree.Plan plan;
        try {
            plan=HdbscanSpanningTree.plan(points,3,false);
            plan.edges().queryExecution().analyzed();plan.coreDistances().schema();
            assertEquals(0,spark.sparkContext().statusTracker().getJobIdsForGroup(job).length);
        } finally {spark.sparkContext().clearJobGroup();}
        var cores=plan.coreDistances().orderBy("id").collectAsList();
        assertEquals(List.of(3d,2d,3d),cores.stream().map(row->row.<Double>getAs("core")).toList());
        plan.edges().collectAsList().forEach(row->assertEquals(3d,row.<Double>getAs("weight")));
        var pairs=HdbscanSpanningTree.plan(points,2,false).coreDistances().orderBy("id").collectAsList();
        assertEquals(List.of(1d,1d,2d),pairs.stream().map(row->row.<Double>getAs("core")).toList());
    }

    @Test void seededCoreDistancesMatchCompletePairsWithCoincidentPointsAndTiedBoundaryNeighbors() {
        var data = points(new double[][]{{0,0},{0,0},{0,0},{1,0},{-1,0},{0,1},{0,-1},{10,0},{10,1},{100,0},{100,1},{100,2}})
                .repartition(2).checkpoint();
        for (int minimum : List.of(2, 5, 12)) {
            var expected = HdbscanSpanningTree.plan(data, minimum, false).coreDistances().orderBy("id").collectAsList();
            var actual = HdbscanCoreDistances.run(data, minimum, false).orderBy("id").collectAsList();
            assertEquals(expected, actual);
        }
    }

    @Test void seededGeodesicCoresRecoverDatelinePolarAndGlobalNeighborsUsingActualPointDistance() {
        var data = points(new double[][]{{179.99,0},{-179.99,0},{180,0},{0,89.999},{180,89.999},{0,-89.999},
                {180,-89.999},{0,0},{0,0},{90,0},{-90,0},{25,40}}).repartition(2).checkpoint();
        for (int minimum : List.of(2, 5, 12)) {
            var expected = HdbscanSpanningTree.plan(data, minimum, true).coreDistances().orderBy("id").collectAsList();
            var actual = HdbscanCoreDistances.run(data, minimum, true).orderBy("id").collectAsList();
            assertEquals(expected, actual);
        }
    }

    @Test void radiusRecoveryReducesCoreRankingCandidatesOnARegularPointGrid() {
        double[][] xy = new double[256][2];
        for (int i = 0; i < xy.length; i++) { xy[i][0] = i % 16; xy[i][1] = i / 16; }
        var data = points(xy).repartition(2).checkpoint();
        var radii = HdbscanCoreDistances.seedRadii(data, 5, false).checkpoint();
        assertEquals(256, radii.count());
        long candidates = HdbscanCoreDistances.candidateDistances(data, radii, false).count();
        assertTrue(candidates >= 256 * 4);
        assertTrue(candidates < 256L * 255 / 8, "The regular-grid core ranking must avoid the complete directed pair set");
        assertEquals(HdbscanSpanningTree.plan(data, 5, false).coreDistances().orderBy("id").collectAsList(),
                HdbscanCoreDistances.fromRadii(data, radii, 5, false).orderBy("id").collectAsList());
    }

    @Test void nonNearestUpperBoundsAndMissingSeedsRecoverExactCoresWithoutRunningDuringAnalysis() {
        var data = points(new double[][]{{0,0},{1,0},{-1,0},{10,0},{10,0},{100,0}});
        var radiusSchema = new StructType().add("id", DataTypes.LongType, false).add("radius", DataTypes.DoubleType, false);
        // Source 0 has an intentionally poor (not nearest) seed; 3 has a coincident neighbor.
        // All other identities exercise the complete-candidate fallback, not partial output.
        var radii = spark.createDataFrame(List.of(RowFactory.create(0L, 100d), RowFactory.create(3L, 0d)), radiusSchema);
        String job = UUID.randomUUID().toString(); spark.sparkContext().setJobGroup(job, "seed analysis", false);
        try {
            HdbscanCoreDistances.seedRadii(data, 2, false).queryExecution().analyzed();
            HdbscanCoreDistances.fromRadii(data, radii, 2, false).queryExecution().analyzed();
            assertEquals(0, spark.sparkContext().statusTracker().getJobIdsForGroup(job).length);
        } finally { spark.sparkContext().clearJobGroup(); }
        assertEquals(HdbscanSpanningTree.plan(data, 2, false).coreDistances().orderBy("id").collectAsList(),
                HdbscanCoreDistances.fromRadii(data, radii, 2, false).orderBy("id").collectAsList());
        assertEquals(0, HdbscanCoreDistances.fromRadii(data, radii.limit(0), 7, false).count());
        assertEquals(0, HdbscanCoreDistances.fromRadii(data.limit(0), radii.limit(0), 2, false).count());
    }

    @Test void feasibleCutBoundsRecoverEveryDenseMinimumAcrossNonRepresentativeMembersAndTies() {
        for (boolean geodesic : List.of(false, true)) {
            double[][] xy = geodesic
                    ? new double[][]{{179.9,0},{-179.9,0},{0,80},{90,80},{0,0},{0,0},{1,0},{1,0},{70,30},{70.1,30},{-50,10},{-50.1,10}}
                    : new double[][]{{0,0},{100,0},{1,0},{101,0},{10,0},{10,0},{11,0},{11,0},{1000,0},{1001,0},{1002,0},{1003,0}};
            var data = points(xy).repartition(2).checkpoint();
            var membership = components(new long[]{9,9,2,2,8,8,4,4,9,9,2,2});
            var dense = HdbscanSpanningTree.plan(data, 3, geodesic);
            var prepared = data.join(dense.coreDistances(), new String[]{"id"}).join(membership, new String[]{"id"}).checkpoint();
            String job = UUID.randomUUID().toString(); spark.sparkContext().setJobGroup(job, "cut analyzer", false);
            try {
                var lazyBounds = HdbscanCutCandidates.seedBounds(prepared, geodesic);
                HdbscanCutCandidates.fromBounds(prepared, lazyBounds, geodesic).queryExecution().analyzed();
                assertEquals(0, spark.sparkContext().statusTracker().getJobIdsForGroup(job).length);
            } finally { spark.sparkContext().clearJobGroup(); }
            var bounds = HdbscanCutCandidates.runBounds(prepared, geodesic);
            assertEquals(4, bounds.count());
            var candidates = HdbscanCutCandidates.fromBounds(prepared, bounds, geodesic);
            assertEquals(sortedEdges(HdbscanSpanningTree.leastOutgoingEdges(HdbscanSpanningTree.withComponents(dense.edges(), membership)).collectAsList()),
                    sortedEdges(HdbscanSpanningTree.leastOutgoingEdges(HdbscanSpanningTree.withComponents(candidates, membership)).collectAsList()));
        }
    }

    @Test void missingCutSeedsUseTwoDistinctComponentAnchorsAndDoNotLoseCoincidentIdentities() {
        var data = points(new double[][]{{0,0},{0,0},{0,0},{0,0},{10,0},{10,0}}).checkpoint();
        var membership = components(new long[]{7,1,9,2,4,8});
        var dense = HdbscanSpanningTree.plan(data, 2, false);
        var representatives = data.join(dense.coreDistances(), new String[]{"id"}).join(membership, new String[]{"id"}).checkpoint();
        var schema = new StructType().add("component", DataTypes.LongType, false).add("bound", DataTypes.DoubleType, false);
        var bounds = HdbscanCutCandidates.completeBounds(representatives, spark.createDataFrame(List.of(), schema), false).checkpoint();
        assertEquals(6, bounds.count());
        var candidates = HdbscanCutCandidates.fromBounds(representatives, bounds, false);
        assertEquals(sortedEdges(HdbscanSpanningTree.leastOutgoingEdges(HdbscanSpanningTree.withComponents(dense.edges(), membership)).collectAsList()),
                sortedEdges(HdbscanSpanningTree.leastOutgoingEdges(HdbscanSpanningTree.withComponents(candidates, membership)).collectAsList()));
    }

    @Test void cutSearchAvoidsTheCompleteGraphOnARegularGridAndKeepsItsExactMinimumEdges() {
        double[][] xy = new double[256][2]; long[] ids = new long[256];
        for (int i = 0; i < xy.length; i++) { xy[i][0] = i % 16; xy[i][1] = i / 16; ids[i] = i; }
        var data = points(xy).repartition(2).checkpoint();
        var membership = components(ids);
        var dense = HdbscanSpanningTree.plan(data, 5, false);
        var prepared = data.join(dense.coreDistances(), new String[]{"id"}).join(membership, new String[]{"id"}).checkpoint();
        var bounds = HdbscanCutCandidates.runBounds(prepared, false);
        assertEquals(256, bounds.count());
        var candidatePlan = HdbscanCutCandidates.fromBounds(prepared, bounds, false);
        String physical = candidatePlan.queryExecution().executedPlan().toString();
        assertTrue(physical.contains("DistanceJoin") || physical.contains("BroadcastIndexJoin"), physical);
        assertFalse(physical.contains("CartesianProduct"), physical);
        var candidates = candidatePlan.checkpoint();
        assertTrue(candidates.count() < 256L * 255 / 2 / 8);
        assertEquals(sortedEdges(HdbscanSpanningTree.leastOutgoingEdges(HdbscanSpanningTree.withComponents(dense.edges(), membership)).collectAsList()),
                sortedEdges(HdbscanSpanningTree.leastOutgoingEdges(HdbscanSpanningTree.withComponents(candidates, membership)).collectAsList()));
    }

    @Test void geodesicCandidatePaddingDoesNotChangeTheActualZeroWeightCut() {
        var data = points(new double[][]{{0,0},{0,0},{1e-12,0},{1e-12,0}}).checkpoint();
        var prepared = data.withColumn("core", functions.lit(0d)).withColumn("component", functions.col("id"));
        var bounds = prepared.select("component").withColumn("bound", functions.lit(0d));
        var edges = sortedEdges(HdbscanCutCandidates.fromBounds(prepared, bounds, true).collectAsList());
        assertEquals(List.of(RowFactory.create(0L,1L,0d), RowFactory.create(2L,3L,0d)), edges);
    }

    @Test void recoveredCutBoruvkaMatchesDenseKruskalOnGlobalPoints() {
        var random = new Random(74209);
        double[][] xy = new double[24][2];
        for (int i = 0; i < xy.length; i++) { xy[i][0] = random.nextDouble() * 360 - 180; xy[i][1] = random.nextDouble() * 178 - 89; }
        var data = points(xy).repartition(2).checkpoint();
        var dense = HdbscanSpanningTree.plan(data, 4, true).edges().collectAsList();
        assertEquals(kruskal(dense, xy.length), sortedEdges(HdbscanSpanningTree.run(data, 4, true).edges().collectAsList()));
    }

    @Test void distributedBoruvkaMatchesIndependentPrimOnTheCompleteMutualReachabilityGraph() {
        double[][] coordinates={{0,0},{.1,0},{.2,0},{10,0},{10.5,0},{11,0},{30,0}};
        for(int minimum:List.of(2,3)) {
            var result=HdbscanSpanningTree.run(points(coordinates).repartition(2),minimum,false);
            assertTrue(result.enoughObservations());assertEquals(7,result.vertexCount());
            var mst=result.edges().collectAsList();assertEquals(6,mst.size());
            double expected=primWeight(coordinates,minimum);
            assertEquals(expected,mst.stream().mapToDouble(row->row.<Double>getAs("weight")).sum(),1e-12);
            var hierarchy=HdbscanHierarchy.extract(LongStream.range(0,7).boxed().toList(),mst.stream()
                    .map(row->new HdbscanHierarchy.Edge(row.getAs("src"),row.getAs("dst"),row.getAs("weight"))).toList(),minimum);
            assertEquals(2,hierarchy.selectedClusters());assertNull(hierarchy.assignments().getLast().cluster());
        }
    }

    @Test void equalAndZeroDistancesProduceATreeWithoutMergingObservationIdentities() {
        var result=HdbscanSpanningTree.run(points(new double[][]{{0,0},{0,0},{10,0},{10,0}}).repartition(2),2,false);
        var edges=result.edges().collectAsList();assertEquals(3,edges.size());
        assertEquals(2,edges.stream().filter(row->row.<Double>getAs("weight")==0).count());
        var rows=HdbscanHierarchy.extract(LongStream.range(0,4).boxed().toList(),edges.stream()
                .map(row->new HdbscanHierarchy.Edge(row.getAs("src"),row.getAs("dst"),row.getAs("weight"))).toList(),2);
        assertEquals(2,rows.selectedClusters());assertEquals(4,rows.assignments().size());
    }

    @Test void actualPointPipelineBindsIdentityOnceAndKeepsAllFourDiagnosticsDistributed() {
        for (boolean geodesic : List.of(false, true)) {
            var evaluations = spark.sparkContext().longAccumulator("hdbscan-private-identity-evaluations");
            var identity = functions.udf((org.apache.spark.sql.api.java.UDF1<Long, Long>) id -> {
                evaluations.add(1); return id;
            }, DataTypes.LongType).asNondeterministic();
            double[][] coordinates = geodesic
                    ? new double[][]{{179.99,0},{-179.99,0},{0,0},{.01,0},{90,0}}
                    : new double[][]{{0,0},{.1,0},{10,0},{10.2,0},{30,0}};
            var source = points(coordinates).repartition(2).withColumn("id", identity.apply(functions.col("id")));
            var mst = HdbscanSpanningTree.run(source, 2, geodesic);
            var condensed = HdbscanCondensedTree.run(mst, 2);
            var actual = HdbscanDiagnostics.run(condensed).orderBy("id").collectAsList();
            assertEquals(coordinates.length, actual.size());
            assertEquals(coordinates.length, evaluations.value());
            // Only the test brings the tiny tree to the Driver for the independent hierarchy oracle.
            var expected = HdbscanHierarchy.extract(LongStream.range(0, coordinates.length).boxed().toList(),
                    mst.edges().collectAsList().stream().map(row -> new HdbscanHierarchy.Edge(row.getAs("src"),
                            row.getAs("dst"), row.getAs("weight"))).toList(), 2);
            assertEquals(2, expected.selectedClusters());
            for (int i = 0; i < actual.size(); i++) {
                var row = actual.get(i); var reference = expected.assignments().get(i);
                assertEquals(reference.vertex(), row.<Long>getAs("id")); assertEquals(reference.cluster(), row.getAs("cluster"));
                assertEquals(reference.probability(), row.<Double>getAs("probability"), 1e-12);
                assertEquals(reference.outlier(), row.<Double>getAs("outlier"), 1e-12);
                assertEquals(reference.exemplar(), row.<Boolean>getAs("exemplar"));
                if (reference.stability() == null) assertNull(row.getAs("stability"));
                else assertEquals(reference.stability(), row.<Double>getAs("stability"), 1e-12);
            }
        }
    }

    @Test void geodesicDensityUsesPointDistancesAcrossTheDatelineAndInsufficientInputsSkipGraphExecution() {
        var cores=HdbscanSpanningTree.plan(points(new double[][]{{179.99,0},{-179.99,0},{0,0}}),2,true)
                .coreDistances().orderBy("id").collectAsList();
        assertEquals(2226.38981586,cores.getFirst().<Double>getAs("core"),.001);
        assertEquals(cores.getFirst().<Double>getAs("core"),cores.get(1).<Double>getAs("core"));
        var empty=HdbscanSpanningTree.run(points(new double[0][]),2,false);
        assertEquals(0,empty.vertexCount());assertFalse(empty.enoughObservations());assertEquals(0,empty.edges().count());
        var singleton=HdbscanSpanningTree.run(points(new double[][]{{0,0}}),3,false);
        assertFalse(singleton.enoughObservations());assertEquals(1,singleton.vertexCount());assertEquals(0,singleton.edges().count());
    }

    @Test void exactQuotientRemovesInternalAndParallelEdgesWithoutChangingAnyLaterCutMinimum() {
        var edgeSchema = new StructType().add("src", DataTypes.LongType, false)
                .add("dst", DataTypes.LongType, false).add("weight", DataTypes.DoubleType, false);
        var rows = new ArrayList<Row>();
        for (long src = 0; src < 12; src++) for (long dst = src + 1; dst < 12; dst++)
            rows.add(RowFactory.create(src, dst, (double) ((src + dst) % 4)));
        var complete = spark.createDataFrame(rows, edgeSchema).repartition(2);
        var components = components(new long[]{99,99,99,99,7,7,7,7,40,40,40,40});
        String job = UUID.randomUUID().toString();
        spark.sparkContext().setJobGroup(job, "lazy quotient analysis", false);
        Dataset<Row> quotient;
        try {
            quotient = HdbscanSpanningTree.contractEdges(complete, components);
            HdbscanSpanningTree.leastOutgoingEdges(HdbscanSpanningTree.withComponents(quotient, components))
                    .queryExecution().analyzed();
            assertEquals(0, spark.sparkContext().statusTracker().getJobIdsForGroup(job).length);
        } finally { spark.sparkContext().clearJobGroup(); }
        assertEquals(66, rows.size());
        assertEquals(3, quotient.count());
        assertEquals(outgoingOracle(rows, new long[]{99,99,99,99,7,7,7,7,40,40,40,40}),
                sortedEdges(HdbscanSpanningTree.leastOutgoingEdges(HdbscanSpanningTree.withComponents(quotient, components)).collectAsList()));
        // IDs deliberately reverse component ordering. Contraction must retain original
        // src/dst and tie order, not relabel the MST edge with a component representative.
        long[] merged = {3,3,3,3,3,3,3,3,1,1,1,1};
        var nextComponents = components(merged);
        var twice = HdbscanSpanningTree.contractEdges(quotient, nextComponents);
        assertEquals(1, twice.count());
        assertEquals(outgoingOracle(rows, merged), sortedEdges(twice.collectAsList()));
        assertEquals(sortedEdges(HdbscanSpanningTree.contractEdges(complete, nextComponents).collectAsList()), sortedEdges(twice.collectAsList()));
        assertEquals(0, HdbscanSpanningTree.contractEdges(twice, components(new long[12])).count());
    }

    @Test void contractedBoruvkaPreservesTheCanonicalDenseTreeAcrossSeveralMergeRounds() {
        double[][] coordinates = {{0,0},{0,0},{1,0},{1,1},{10,0},{10,0},{11,0},{11,1},
                {100,0},{100,0},{101,0},{101,1},{1000,0},{1000,0},{1001,0},{1001,1}};
        var tree = HdbscanSpanningTree.run(points(coordinates).repartition(2), 3, false);
        var expected = canonicalDenseTree(coordinates, 3);
        assertEquals(expected, sortedEdges(tree.edges().collectAsList()));
    }

    private Dataset<Row> components(long[] membership) {
        var schema = new StructType().add("id", DataTypes.LongType, false).add("component", DataTypes.LongType, false);
        var rows = new ArrayList<Row>();
        for (int id = 0; id < membership.length; id++) rows.add(RowFactory.create((long) id, membership[id]));
        return spark.createDataFrame(rows, schema);
    }

    private static final Comparator<Row> EDGE_ORDER = Comparator.comparingDouble((Row row) -> row.getDouble(2))
            .thenComparingLong(row -> row.getLong(0)).thenComparingLong(row -> row.getLong(1));

    private List<Row> sortedEdges(List<Row> rows) { return rows.stream().sorted(EDGE_ORDER).toList(); }

    private List<Row> outgoingOracle(List<Row> rows, long[] membership) {
        Map<Long, Row> minima = new HashMap<>();
        for (Row edge : rows) {
            long left = membership[(int) edge.getLong(0)], right = membership[(int) edge.getLong(1)];
            if (left == right) continue;
            for (long component : new long[]{left, right}) minima.merge(component, edge,
                    (a, b) -> EDGE_ORDER.compare(a, b) <= 0 ? a : b);
        }
        return sortedEdges(minima.values().stream().distinct().toList());
    }

    /** Independent dense Kruskal with the same total tie order, not just a weight-sum check. */
    private List<Row> canonicalDenseTree(double[][] xy, int minimum) {
        int n = xy.length;
        double[][] distances = new double[n][n];
        double[] cores = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) distances[i][j] = Math.hypot(xy[i][0] - xy[j][0], xy[i][1] - xy[j][1]);
            double[] sorted = distances[i].clone(); Arrays.sort(sorted); cores[i] = sorted[minimum - 1];
        }
        var edges = new ArrayList<Row>();
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) edges.add(RowFactory.create((long) i, (long) j,
                Math.max(distances[i][j], Math.max(cores[i], cores[j]))));
        return kruskal(edges, n);
    }

    private List<Row> kruskal(List<Row> edges, int n) {
        int[] groups = new int[n]; for (int i = 0; i < n; i++) groups[i] = i;
        var tree = new ArrayList<Row>();
        for (Row edge : sortedEdges(edges)) {
            int left = groups[(int) edge.getLong(0)], right = groups[(int) edge.getLong(1)];
            if (left == right) continue;
            tree.add(edge);
            for (int i = 0; i < n; i++) if (groups[i] == right) groups[i] = left;
        }
        assertEquals(n - 1, tree.size());
        return sortedEdges(tree);
    }

    private Dataset<Row> points(double[][] xy) {
        var schema=new StructType().add("id",DataTypes.LongType,false).add("x",DataTypes.DoubleType,false).add("y",DataTypes.DoubleType,false);
        var rows=new ArrayList<Row>();for(int i=0;i<xy.length;i++)rows.add(RowFactory.create((long)i,xy[i][0],xy[i][1]));
        return spark.createDataFrame(rows,schema).selectExpr("id","ST_SetSRID(ST_Point(x,y),4326) AS geometry");
    }

    /** Independent dense Prim oracle used only on these tiny fixtures, never by production. */
    private double primWeight(double[][] points,int minimum) {
        int n=points.length;double[][] distances=new double[n][n];double[] core=new double[n];
        for(int i=0;i<n;i++) {
            for(int j=0;j<n;j++)distances[i][j]=Math.hypot(points[i][0]-points[j][0],points[i][1]-points[j][1]);
            double[] sorted=distances[i].clone();Arrays.sort(sorted);core[i]=sorted[minimum-1];
        }
        double[] best=new double[n];Arrays.fill(best,Double.POSITIVE_INFINITY);best[0]=0;
        boolean[] visited=new boolean[n];double sum=0;
        for(int step=0;step<n;step++) {
            int next=-1;for(int i=0;i<n;i++)if(!visited[i]&&(next<0||best[i]<best[next]))next=i;
            visited[next]=true;sum+=best[next];
            for(int i=0;i<n;i++)if(!visited[i])best[i]=Math.min(best[i],Math.max(distances[next][i],Math.max(core[i],core[next])));
        }
        return sum;
    }
}
