package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import org.apache.spark.sql.Dataset;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HdbscanEomSparkTest {
    private static final String ROOT = HdbscanCondensedTree.ROOT;
    private static final StructType NODES = new StructType()
            .add("cluster", DataTypes.StringType, false).add("parent", DataTypes.StringType, true)
            .add("mass", DataTypes.DoubleType, false).add("death", DataTypes.DoubleType, false);
    private static final StructType CONTRIBUTIONS = new StructType()
            .add("cluster", DataTypes.StringType, false).add("parent", DataTypes.StringType, true)
            .add("best", DataTypes.DoubleType, false).add("descendantDeath", DataTypes.DoubleType, false);
    private SparkSession spark;

    @BeforeAll void start() throws Exception {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder().master("local[2]").appName("hdbscan-eom")
                .config("spark.ui.enabled", false).config("spark.driver.host", "127.0.0.1")
                .config("spark.driver.bindAddress", "127.0.0.1").config("spark.sql.shuffle.partitions", 2).getOrCreate());
        spark.sparkContext().setCheckpointDir(Files.createTempDirectory("datascalpel-hdbscan-eom-").toUri().toString());
    }

    @AfterAll void stop() { if (spark != null) spark.stop(); }

    @Test void unbalancedBranchesWaitForAllSiblingsAndMatchIndependentRecursiveEom() {
        var nodes = new ArrayList<Cluster>();
        double best = 0;
        // Every side leaf completes in the first round, while its spine sibling can take
        // twenty rounds. Exact integer masses exercise parent ties without rounding noise.
        for (int level = 19; level >= 0; level--) {
            String cluster = "spine-" + level;
            nodes.add(new Cluster("leaf-" + level, cluster, 1, 100 + level));
            double children = best + 1;
            double mass = switch (level % 3) { case 0 -> children; case 1 -> children + 2; default -> 0; };
            nodes.add(new Cluster(cluster, level == 0 ? ROOT : "spine-" + (level - 1), mass, level + 1));
            best = Math.max(mass, children);
        }
        nodes.add(new Cluster(ROOT, null, 1_000_000, 0));
        var actual = assertOracle(nodes);
        assertFalse(actual.get(ROOT).<Boolean>getAs("choose"));
        assertEquals(best, actual.get(ROOT).<Double>getAs("best"));
        assertEquals(119d, actual.get(ROOT).<Double>getAs("descendantDeath"));
        assertTrue(actual.get("spine-0").<Boolean>getAs("choose"));
        assertFalse(actual.get("spine-2").<Boolean>getAs("choose"));
    }

    @Test void rootExclusionInfiniteTiesAndDescendantDeathsRemainDefined() {
        var actual = assertOracle(List.of(new Cluster(ROOT, null, Double.POSITIVE_INFINITY, 0),
                new Cluster("parent", ROOT, Double.POSITIVE_INFINITY, 2),
                new Cluster("infinite", "parent", Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY),
                new Cluster("finite", "parent", 1, 4), new Cluster("other", ROOT, 3, 5)));
        assertTrue(actual.get("parent").<Boolean>getAs("choose"));
        assertFalse(actual.get(ROOT).<Boolean>getAs("choose"));
        assertEquals(Double.POSITIVE_INFINITY, actual.get(ROOT).<Double>getAs("best"));
        assertEquals(Double.POSITIVE_INFINITY, actual.get("parent").<Double>getAs("descendantDeath"));
    }

    @Test void waitingSetDropsConsumedChildrenAndRetainsOriginalContributionsExactlyOnce() {
        var waiting = spark.createDataFrame(List.of(RowFactory.create("consumed", "resolved", 7d, 9d),
                RowFactory.create("early", "pending", 0.125d, 15d)), CONTRIBUTIONS);
        var decisions = spark.createDataFrame(List.of(RowFactory.create("resolved", "pending", 11d, 12d),
                RowFactory.create("other", ROOT, 1d, 3d)), CONTRIBUTIONS);
        var pending = nodes(List.of(new Cluster("pending", ROOT, 0, 0), new Cluster(ROOT, null, 0, 0)));
        var next = HdbscanDiagnostics.waitingChildren(waiting, decisions, pending);
        var rows = index(next);
        assertEquals(3, rows.size());
        assertEquals(3, next.count());
        assertEquals(java.util.Set.of("early", "resolved", "other"), rows.keySet());
        assertEquals(0.125d, rows.get("early").<Double>getAs("best"));
        assertEquals(15d, rows.get("early").<Double>getAs("descendantDeath"));
        assertEquals(11d, rows.get("resolved").<Double>getAs("best"));
        assertEquals("pending", rows.get("resolved").getAs("parent"));

        var parentDecision = spark.createDataFrame(List.of(RowFactory.create("pending", ROOT, 11.125d, 15d)), CONTRIBUTIONS);
        var last = index(HdbscanDiagnostics.waitingChildren(next, parentDecision,
                nodes(List.of(new Cluster(ROOT, null, 0, 0)))));
        assertEquals(java.util.Set.of("pending", "other"), last.keySet());
        assertEquals(11.125d, last.get("pending").<Double>getAs("best"));
        // Previously bound snapshots are not mutated by consuming their contributions.
        assertEquals(3, next.count());
        assertEquals(2, waiting.count());
    }

    @Test void cyclicRemainderFailsInsteadOfReturningCompletedPartialHistory() {
        var input = nodes(List.of(new Cluster(ROOT, null, 0, 0), new Cluster("leaf", ROOT, 1, 1),
                new Cluster("cycle-a", "cycle-b", 1, 1), new Cluster("cycle-b", "cycle-a", 1, 1)));
        var failure = assertThrows(IllegalArgumentException.class, () -> HdbscanDiagnostics.eomDecisions(input));
        assertEquals("SPATIAL_HDBSCAN_TREE_INVALID", failure.getMessage());
        assertEquals(0, HdbscanDiagnostics.eomDecisions(nodes(List.of())).count());
    }

    private Map<String, Row> assertOracle(List<Cluster> input) {
        var expected = new HashMap<String, Decision>();
        evaluate(input.stream().filter(node -> ROOT.equals(node.id())).findFirst().orElseThrow(), input, expected);
        var output = HdbscanDiagnostics.eomDecisions(nodes(input));
        var actual = index(output);
        assertEquals(input.size(), output.count());
        assertEquals(expected.keySet(), actual.keySet());
        assertTrue(output.queryExecution().optimizedPlan().stats().sizeInBytes().bitLength() <= 64);
        for (var node : input) {
            var decision = expected.get(node.id());
            var row = actual.get(node.id());
            assertEquals(node.parent(), row.getAs("parent"), node.id());
            assertEquals(decision.choose(), row.<Boolean>getAs("choose"), node.id());
            assertEquals(decision.best(), row.<Double>getAs("best"), node.id());
            assertEquals(decision.death(), row.<Double>getAs("descendantDeath"), node.id());
        }
        return actual;
    }

    // Independent test-only recursion; production identities/edges remain in Spark tables.
    private Decision evaluate(Cluster node, List<Cluster> nodes, Map<String, Decision> result) {
        double childMass = 0, death = node.death();
        for (var child : nodes) {
            if (!Objects.equals(node.id(), child.parent())) continue;
            var decision = evaluate(child, nodes, result);
            childMass += decision.best();
            death = Math.max(death, decision.death());
        }
        boolean choose = !ROOT.equals(node.id()) && node.mass() >= childMass;
        var decision = new Decision(choose, choose ? node.mass() : childMass, death);
        result.put(node.id(), decision);
        return decision;
    }

    private Dataset<Row> nodes(List<Cluster> nodes) {
        return spark.createDataFrame(nodes.stream().map(node -> RowFactory.create(node.id(), node.parent(), node.mass(), node.death()))
                .toList(), NODES).repartition(2);
    }

    private Map<String, Row> index(Dataset<Row> rows) {
        return rows.collectAsList().stream().collect(Collectors.toMap(row -> row.getAs("cluster"), Function.identity()));
    }

    private record Cluster(String id, String parent, double mass, double death) { }
    private record Decision(boolean choose, double best, double death) { }
}
