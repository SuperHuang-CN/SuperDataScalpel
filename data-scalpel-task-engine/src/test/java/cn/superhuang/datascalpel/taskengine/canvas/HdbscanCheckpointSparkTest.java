package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import cn.superhuang.data.scalpel.contract.task.*;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.net.URI;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HdbscanCheckpointSparkTest {
    private SparkSession spark;
    private Path root;

    @BeforeAll void start() throws Exception {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder().master("local[2]").appName("hdbscan-checkpoint-ownership")
                .config("spark.ui.enabled", false).config("spark.driver.host", "127.0.0.1")
                .config("spark.driver.bindAddress", "127.0.0.1").config("spark.sql.shuffle.partitions", 2).getOrCreate());
        spark.sparkContext().setCheckpointDir(Files.createTempDirectory("datascalpel-hdbscan-ownership-").toUri().toString());
        root = Path.of(URI.create(spark.sparkContext().getCheckpointDir().get()));
    }
    @AfterAll void stop() { if (spark != null) spark.stop(); }

    @Test void keepsOnlyIndependentFinalSnapshotWithoutDeletingForeignInput() throws Exception {
        var foreign = spark.range(8).toDF("id").checkpoint();
        Set<Path> before = paths();
        org.apache.spark.sql.Dataset<org.apache.spark.sql.Row> result;
        try (var scope = new HdbscanCheckpoint.Scope()) {
            var intermediate = scope.bind(foreign.withColumn("value", functions.col("id").plus(1)));
            result = scope.keep(scope.checkpoint(intermediate.filter("id >= 3")));
            assertTrue(paths().size() >= before.size() + 2);
        }
        assertEquals(before.size() + 1, paths().size()); assertTrue(paths().containsAll(before));
        assertEquals(8, foreign.count());
        assertEquals(5, result.count()); assertEquals(8L, result.orderBy(functions.col("id").desc()).first().getLong(1));
        assertEquals(5, result.repartition(3).count());
    }

    @Test void failureDeletesCompletedAndPartiallyAllocatedSnapshotsAndPreservesPrimaryError() throws Exception {
        Set<Path> before = paths();
        var failure = assertThrows(Exception.class, () -> {
            try (var scope = new HdbscanCheckpoint.Scope()) {
                scope.checkpoint(spark.range(3).toDF("id"));
                scope.checkpoint(spark.range(3).select(functions.raise_error(functions.lit("HDBSCAN_TEST_FAILURE")).alias("value")));
            }
        });
        assertTrue(failure.getMessage().contains("HDBSCAN_TEST_FAILURE"));
        assertEquals(before, paths());
    }

    @Test void scopesAreIsolatedAndCloseIsIdempotent() throws Exception {
        Set<Path> before = paths();
        var first = new HdbscanCheckpoint.Scope();
        var second = new HdbscanCheckpoint.Scope();
        try {
            first.checkpoint(spark.range(2).toDF("id"));
            var other = second.bind(spark.range(4).toDF("id"));
            first.close(); first.close();
            assertEquals(before.size() + 1, paths().size()); assertEquals(4, other.count());
            assertThrows(IllegalStateException.class, () -> first.keep(other));
            assertThrows(IllegalArgumentException.class, () -> second.keep(spark.range(1).toDF("id")));
        } finally { first.close(); second.close(); }
        assertEquals(before, paths());
    }

    @Test void completeNodeLeavesOnlyItsReadableFinalCheckpointAndPreservesForeignSource() throws Exception {
        Path foreignDirectory = Files.createDirectories(root.resolve("connected-components-123abc00"));
        Files.writeString(foreignDirectory.resolve("foreign-marker"), "foreign fixture");
        var source = spark.range(6).toDF("id").withColumn("shape", functions.expr(
                "ST_SetSRID(ST_Point(CASE WHEN id < 3 THEN CAST(id AS DOUBLE) ELSE CAST(id + 97 AS DOUBLE) END, 0D), 3857)"))
                .checkpoint();
        Set<Path> before = paths();
        var configuration = new SpatialPointClusterConfiguration("points", "shape", "id", SpatialDistanceMethod.PLANAR,
                new SpatialPointClusterParameters.Hdbscan(2), "result", "cluster", "noise", null,
                new SpatialHdbscanOptions("probability", "outlier", "exemplar", "stability"));
        var result = PointHdbscanSupport.run(source, configuration);
        assertTrue(paths().containsAll(before));
        assertEquals(before.size() + 1, paths().size(), "all node intermediates must be disposable after final materialization");
        assertEquals(6, source.count()); assertEquals(6, result.repartition(3).count());
        assertEquals(2, result.select("cluster").distinct().count());
        assertEquals(6, result.filter("probability >= 0 AND probability <= 1").count());
        assertEquals("foreign fixture", Files.readString(foreignDirectory.resolve("foreign-marker")));
    }

    private Set<Path> paths() throws Exception {
        try (var entries = Files.list(root)) { return entries.collect(Collectors.toSet()); }
    }

    @Test void failureAfterCompletedGraphStageCleansItsOwnedFilesWithoutChangingInput() throws Exception {
        var input = spark.range(5).toDF("id").withColumn("geometry", functions.expr("ST_Point(CAST(id AS DOUBLE), 0D)")).checkpoint();
        Set<Path> before = paths();
        var failure = assertThrows(IllegalStateException.class, () -> {
            try (var scope = new HdbscanCheckpoint.Scope()) {
                var tree = HdbscanSpanningTree.run(input, 2, false, scope);
                assertEquals(5, tree.vertexCount());
                throw new IllegalStateException("HDBSCAN_POST_TREE_FAILURE");
            }
        });
        assertEquals("HDBSCAN_POST_TREE_FAILURE", failure.getMessage());
        assertEquals(before, paths()); assertEquals(5, input.count());
    }

    @Test void aForeignParquetInputWithLibraryLikeDirectoryNameIsNeverAdopted() throws Exception {
        Path directory = root.resolve("connected-components-abcdef11");
        spark.range(3).write().parquet(directory.resolve("1").toUri().toString());
        var foreign = spark.read().parquet(directory.resolve("1").toUri().toString());
        Set<Path> before = paths();
        String group = "hdbscan-storage-metadata-" + java.util.UUID.randomUUID();
        spark.sparkContext().setJobGroup(group, "metadata-only ownership", false);
        try {
            try (var scope = new HdbscanCheckpoint.Scope()) {
                scope.trackGraphResult(foreign.withColumn("component", functions.col("id")), foreign, spark.range(0).toDF("id"));
            }
            assertEquals(0, spark.sparkContext().statusTracker().getJobIdsForGroup(group).length);
        } finally { spark.sparkContext().clearJobGroup(); }
        assertEquals(before, paths()); assertEquals(3, foreign.count());
    }
}
