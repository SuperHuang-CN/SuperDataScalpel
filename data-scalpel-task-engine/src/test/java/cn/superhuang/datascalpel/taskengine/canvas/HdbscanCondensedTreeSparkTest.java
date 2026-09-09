package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HdbscanCondensedTreeSparkTest {
    private SparkSession spark;

    @BeforeAll void start() throws Exception {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder().master("local[2]").appName("hdbscan-condensation")
                .config("spark.ui.enabled", false).config("spark.driver.host", "127.0.0.1")
                .config("spark.driver.bindAddress", "127.0.0.1").config("spark.sql.shuffle.partitions", 2).getOrCreate());
        spark.sparkContext().setCheckpointDir(Files.createTempDirectory("datascalpel-hdbscan-condensed-").toUri().toString());
    }
    @AfterAll void stop() { if (spark != null) spark.stop(); }

    @Test void continuationAndForkRecordExactImmediateParentsAndExitDensities() {
        double[][] edges = {{0,1,1},{1,2,2},{3,4,1},{4,5,2},{2,3,10},{5,6,20}};
        var result = HdbscanCondensedTree.run(tree(7, 2, edges), 2);
        var clusters = result.clusters().collectAsList();
        assertEquals(3, clusters.size());
        var parents = byId(result);
        String left = parents.get(0L).getAs("cluster"), right = parents.get(3L).getAs("cluster");
        assertNotEquals(left, right); assertNotEquals(HdbscanCondensedTree.ROOT, left);
        assertEquals(left, parents.get(1L).getAs("cluster")); assertEquals(left, parents.get(2L).getAs("cluster"));
        assertEquals(right, parents.get(4L).getAs("cluster")); assertEquals(right, parents.get(5L).getAs("cluster"));
        assertEquals(HdbscanCondensedTree.ROOT, parents.get(6L).getAs("cluster"));
        for (long id : List.of(0L,1L,3L,4L)) assertEquals(1d, parents.get(id).<Double>getAs("lambda"));
        for (long id : List.of(2L,5L)) assertEquals(.5d, parents.get(id).<Double>getAs("lambda"));
        assertEquals(.05d, parents.get(6L).<Double>getAs("lambda"));
        clusters.stream().filter(row -> !HdbscanCondensedTree.ROOT.equals(row.getAs("cluster"))).forEach(row -> {
            assertEquals(HdbscanCondensedTree.ROOT, row.getAs("parent"));
            assertEquals(.1d, row.<Double>getAs("birth")); assertEquals(3L, row.<Long>getAs("size"));
        });
        assertConservation(result, 7);
        // Two children of size 3 depart root at .1; one noise observation departed at .05.
        double rootStability = result.departures().collectAsList().stream()
                .filter(row -> HdbscanCondensedTree.ROOT.equals(row.getAs("cluster")))
                .mapToDouble(row -> row.<Double>getAs("lambda") * row.<Long>getAs("size")).sum();
        assertEquals(.65d, rootStability, 1e-12);
        assertDiagnostics(result, 7, edges, 2);
    }

    @Test void equalLevelsSplitSimultaneouslyAndZeroDistanceKeepsEveryIdentity() {
        for (double distance : List.of(1d, 0d)) {
            double[][] edges = {{0,1,distance},{1,2,distance},{2,3,distance}};
            var result = HdbscanCondensedTree.run(tree(4, 2, edges), 2);
            assertEquals(1, result.clusters().count());
            assertEquals(4, result.observations().count());
            for (Row row : result.observations().collectAsList()) {
                assertEquals(HdbscanCondensedTree.ROOT, row.getAs("cluster"));
                assertEquals(distance == 0 ? Double.POSITIVE_INFINITY : 1d, row.<Double>getAs("lambda"));
            }
            assertConservation(result, 4);
            assertDiagnostics(result, 4, edges, 2);
        }
    }

    @Test void independentNestedBranchesPreserveBirthSizeAndParentLinks() {
        // Root splits 0..3 from 4..7; each then splits into two pairs at its own level.
        double[][] edges = {{0,1,1},{2,3,1},{1,2,4},{4,5,1},{6,7,1},{5,6,5},{3,4,10}};
        var result = HdbscanCondensedTree.run(tree(8, 2, edges), 2);
        var clusters = result.clusters().collectAsList();
        assertEquals(7, clusters.size());
        var index = clusters.stream().collect(Collectors.toMap(row -> row.<String>getAs("cluster"), Function.identity()));
        assertEquals(2, clusters.stream().filter(row -> HdbscanCondensedTree.ROOT.equals(row.getAs("parent"))).count());
        for (Row row : clusters) {
            String parent = row.getAs("parent");
            if (parent == null) continue;
            assertTrue(index.containsKey(parent));
            assertTrue(row.<Double>getAs("birth") > index.get(parent).<Double>getAs("birth"));
        }
        assertConservation(result, 8);
        assertEquals(4, result.observations().select("cluster").distinct().count());
        assertDiagnostics(result, 8, edges, 2);
    }

    @Test void eomParentSelectionSuppressesDescendantsAndDuplicateDiagnosticsStayFinite() {
        double[][] parentEdges = {{0,1,9},{2,3,9},{1,2,10},{4,5,9},{6,7,9},{5,6,10},{3,4,100},{1,8,11}};
        var parentResult = HdbscanCondensedTree.run(tree(9, 2, parentEdges), 2);
        var parentRows = assertDiagnostics(parentResult, 9, parentEdges, 2);
        assertEquals(2, parentRows.values().stream().map(row -> row.<Long>getAs("cluster")).distinct().count());
        // Vertex 8 leaves before the selected parent splits. GLOSH uses its deeper descendants,
        // unlike membership strength, which uses the selected parent's own death density.
        assertEquals(10d / 11, parentRows.get(8L).<Double>getAs("probability"), 1e-12);
        assertEquals(2d / 11, parentRows.get(8L).<Double>getAs("outlier"), 1e-12);
        double[][] duplicateEdges = {{0,1,0},{1,2,2},{2,3,20},{3,4,1},{4,5,2}};
        assertDiagnostics(HdbscanCondensedTree.run(tree(6, 2, duplicateEdges), 2), 6, duplicateEdges, 2);
    }

    @Test void insufficientAndEmptyInputsKeepDefinedRootWithoutRunningConnectedComponents() {
        for (int count : List.of(0, 1, 2)) {
            var result = HdbscanCondensedTree.run(tree(count, 3, new double[0][]), 3);
            assertEquals(count, result.observations().count()); assertEquals(1, result.clusters().count());
            result.observations().collectAsList().forEach(row -> {
                assertEquals(HdbscanCondensedTree.ROOT, row.getAs("cluster"));
                assertEquals(0d, row.<Double>getAs("lambda"));
            });
            assertConservation(result, count);
            var diagnostics = HdbscanDiagnostics.run(result).collectAsList();
            assertEquals(count, diagnostics.size());
            diagnostics.forEach(row -> {
                assertNull(row.getAs("cluster")); assertNull(row.getAs("stability"));
                assertEquals(0d, row.<Double>getAs("probability")); assertEquals(0d, row.<Double>getAs("outlier"));
                assertFalse(row.<Boolean>getAs("exemplar"));
            });
        }
    }

    @Test void binaryCheckpointHistoryBindsEachBatchOnceAndKeepsSnapshotsIndependent() {
        try (var scope = new HdbscanCheckpoint.Scope()) {
        var evaluated = spark.sparkContext().longAccumulator("history-source-evaluations");
        var value = org.apache.spark.sql.functions.udf((org.apache.spark.sql.api.java.UDF1<Long, Long>) id -> {
            evaluated.add(1); return id;
        }, DataTypes.LongType).asNondeterministic();
        var history = new HdbscanRowHistory(spark.range(0).toDF("id"), scope);
        assertEquals(0, history.materialize().count());
        org.apache.spark.sql.Dataset<Row> earlier = null;
        for (int batch = 0; batch < 17; batch++) {
            var data = spark.range(batch * 3L, (batch + 1) * 3L).repartition(3).toDF("id")
                    .withColumn("id", value.apply(org.apache.spark.sql.functions.col("id")));
            history.append(data);
            assertEquals(Integer.bitCount(batch + 1), history.retainedBatchCount());
            if (batch == 6) earlier = history.materialize();
        }
        assertNotNull(earlier); assertEquals(21, earlier.count());
        var result = history.materialize();
        assertTrue(result.queryExecution().optimizedPlan().stats().sizeInBytes().bitLength() <= 64);
        assertEquals(java.util.stream.LongStream.range(0, 51).boxed().toList(),
                result.orderBy("id").collectAsList().stream().map(row -> row.getLong(0)).toList());
        assertEquals(51, evaluated.value());
        assertTrue(result.rdd().getNumPartitions() <= 2);
        assertEquals(51, history.materialize().count());
        assertEquals(51, evaluated.value());
        }
    }

    @Test void manySingleChildContinuationsPreserveEveryExitAndDiagnostic() {
        int size = 34;
        double[][] edges = new double[size - 1][3];
        for (int i = 0; i < edges.length; i++) edges[i] = new double[]{i, i + 1, i + 1};
        var result = HdbscanCondensedTree.run(tree(size, 2, edges), 2);
        for (var output : List.of(result.clusters(), result.departures(), result.observations()))
            assertTrue(output.queryExecution().optimizedPlan().stats().sizeInBytes().bitLength() <= 64);
        assertEquals(1, result.clusters().count());
        // The last tied split emits two singleton departures, not one aggregate row.
        assertEquals(size, result.departures().count());
        var observations = byId(result);
        for (long id = 0; id < size; id++) {
            assertEquals(HdbscanCondensedTree.ROOT, observations.get(id).getAs("cluster"));
            assertEquals(1d / Math.max(1, id), observations.get(id).<Double>getAs("lambda"));
        }
        assertConservation(result, size);
        assertDiagnostics(result, size, edges, 2);
    }

    private Map<Long, Row> byId(HdbscanCondensedTree.Result result) {
        return result.observations().collectAsList().stream().collect(Collectors.toMap(row -> row.getAs("id"), Function.identity()));
    }

    private Map<Long, Row> assertDiagnostics(HdbscanCondensedTree.Result result, int size, double[][] edges, int minimum) {
        var expected = HdbscanHierarchy.extract(java.util.stream.LongStream.range(0, size).boxed().toList(),
                java.util.Arrays.stream(edges).map(e -> new HdbscanHierarchy.Edge((long)e[0], (long)e[1], e[2])).toList(), minimum);
        var actual = HdbscanDiagnostics.run(result).collectAsList().stream()
                .collect(Collectors.toMap(row -> row.<Long>getAs("id"), Function.identity()));
        assertEquals(size, actual.size());
        for (var assignment : expected.assignments()) {
            Row row = actual.get(assignment.vertex());
            assertNotNull(row); assertEquals(assignment.cluster(), row.getAs("cluster"));
            assertEquals(assignment.probability(), row.<Double>getAs("probability"), 1e-12);
            assertEquals(assignment.outlier(), row.<Double>getAs("outlier"), 1e-12);
            assertEquals(assignment.exemplar(), row.<Boolean>getAs("exemplar"));
            if (assignment.stability() == null) assertNull(row.getAs("stability"));
            else assertEquals(assignment.stability(), row.<Double>getAs("stability"), 1e-12);
        }
        return actual;
    }

    private void assertConservation(HdbscanCondensedTree.Result result, long vertices) {
        assertEquals(vertices, result.observations().select("id").distinct().count());
        var exited = result.departures().groupBy("cluster").sum("size").collectAsList().stream()
                .collect(Collectors.toMap(row -> row.<String>getAs("cluster"), row -> row.<Long>getAs("sum(size)")));
        result.clusters().collectAsList().forEach(row -> assertEquals(row.<Long>getAs("size"), exited.get(row.<String>getAs("cluster"))));
    }

    private HdbscanSpanningTree.Tree tree(long size, int minimum, double[][] edges) {
        var schema = new StructType().add("src", DataTypes.LongType, false).add("dst", DataTypes.LongType, false)
                .add("weight", DataTypes.DoubleType, false);
        var rows = java.util.Arrays.stream(edges).map(e -> RowFactory.create((long)e[0], (long)e[1], e[2])).toList();
        return new HdbscanSpanningTree.Tree(spark.range(size).toDF("id").repartition(2).checkpoint(),
                spark.createDataFrame(rows, schema).repartition(2).checkpoint(), size, size >= minimum);
    }
}
