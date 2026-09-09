package cn.superhuang.datascalpel.taskengine.canvas;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;
import org.graphframes.GraphFrame;

/** Distributed top-down condensation of an exact mutual-reachability MST. Internal runtime
 * stage only: no point, edge or cluster list is collected at the Driver. Removing every tied
 * maximum edge in a component is one simultaneous single-linkage split. Sub-minimum children
 * leave their current condensed cluster; one surviving child retains its parent's identity.
 * The worst-case number of shuffle rounds is the tree depth, not a scalability guarantee.
 */
final class HdbscanCondensedTree {
    static final String ROOT = "root";

    private HdbscanCondensedTree() { }

    /** clusters: cluster,parent,birth,size; departures: cluster,lambda,size;
     * observations: id,cluster,lambda (the immediate condensed parent at final exit).
     * All tables are checkpoint-bound, independent of the temporary GraphFrames cache.
     */
    record Result(Dataset<Row> clusters, Dataset<Row> departures, Dataset<Row> observations) { }

    static Result run(HdbscanSpanningTree.Tree tree, int minimum) {
        try (var scope = new HdbscanCheckpoint.Scope()) {
            var result = run(tree, minimum, scope);
            scope.keep(result.clusters()); scope.keep(result.departures()); scope.keep(result.observations());
            return result;
        }
    }

    static Result run(HdbscanSpanningTree.Tree tree, int minimum, HdbscanCheckpoint.Scope scope) {
        if (minimum < 2 || minimum > 100_000) throw failure("INVALID_SPATIAL_CLUSTER_MINIMUM_FEATURES");
        if (tree.vertexCount() > HdbscanHierarchy.MAX_VERTICES)
            throw failure("SPATIAL_HDBSCAN_HIERARCHY_LIMIT_EXCEEDED");
        if (tree.vertices().sparkSession().sparkContext().getCheckpointDir().isEmpty())
            throw failure("SPATIAL_CLUSTER_CHECKPOINT_NOT_CONFIGURED");

        var spark = tree.vertices().sparkSession();
        var clusters = spark.range(1).select(functions.lit(ROOT).alias("cluster"),
                functions.lit(null).cast("string").alias("parent"), functions.lit(0d).alias("birth"),
                functions.lit(tree.vertexCount()).alias("size")).transform(scope::checkpoint);
        var departures = clusters.limit(0).select("cluster", "birth", "size").withColumnRenamed("birth", "lambda");
        var observations = tree.vertices().limit(0).select(functions.col("id"), functions.lit(ROOT).alias("cluster"),
                functions.lit(0d).alias("lambda"));
        if (tree.vertexCount() < minimum) {
            return new Result(clusters, clusters.select(functions.col("cluster"), functions.col("birth").alias("lambda"),
                    functions.col("size")).transform(scope::checkpoint), tree.vertices().select(functions.col("id"),
                    functions.lit(ROOT).alias("cluster"), functions.lit(0d).alias("lambda")).transform(scope::checkpoint));
        }
        if (!tree.enoughObservations()) throw failure("SPATIAL_HDBSCAN_TREE_INVALID");

        var clusterHistory = new HdbscanRowHistory(clusters, scope);
        clusterHistory.append(clusters);
        var departureHistory = new HdbscanRowHistory(departures, scope);
        var observationHistory = new HdbscanRowHistory(observations, scope);

        // Scalar normalization only. The graph itself stays distributed. Zero edges represent
        // infinite density; normalizing finite lambdas avoids reciprocal overflow.
        Row scaleRow = tree.edges().agg(functions.min(functions.when(functions.col("weight").gt(0),
                functions.col("weight"))).alias("scale")).first();
        double scale = scaleRow.isNullAt(0) ? 1d : scaleRow.getDouble(0);
        var active = tree.vertices().select(functions.col("id"), functions.lit(ROOT).alias("cluster")).transform(scope::checkpoint);
        var edges = tree.edges();
        long remaining = tree.vertexCount();

        for (long round = 1; remaining > 0; round++) {
            // Every split removes at least one original tree edge, including a zero edge.
            if (round > tree.vertexCount()) throw failure("SPATIAL_HDBSCAN_TREE_INVALID");
            var scoped = edges.join(active.select(functions.col("id").alias("src"), functions.col("cluster")),
                    new String[]{"src"}, "inner");
            var levels = scoped.groupBy("cluster").agg(functions.max("weight").alias("cut"));
            Column normalized = functions.lit(scale).divide(functions.col("cut"));
            Column invalid = normalized.leq(0).or(functions.isnan(normalized)).or(normalized.gt(Double.MAX_VALUE));
            levels = levels.withColumn("lambda", functions.when(functions.col("cut").equalTo(0), functions.lit(Double.POSITIVE_INFINITY))
                    .when(invalid, functions.raise_error(functions.lit("SPATIAL_HDBSCAN_NUMERIC_RANGE_INVALID")).cast("double"))
                    .otherwise(normalized));
            var retained = scope.bind(scoped.join(levels, new String[]{"cluster"}, "inner")
                    .filter(functions.col("weight").lt(functions.col("cut"))).select("src", "dst", "weight"));
            Dataset<Row> graph = GraphFrame.apply(active.select("id"), retained.select("src", "dst"))
                    .connectedComponents().run();
            try {
                scope.trackGraphResult(graph, active, retained);
                var children = scope.bind(graph.select("id", "component").join(active, new String[]{"id"}, "inner"));
                var sizes = children.groupBy("cluster", "component").agg(functions.count("id").alias("size"),
                        functions.min("id").alias("seed"));
                var survivors = sizes.groupBy("cluster").agg(functions.sum(functions.when(functions.col("size").geq(minimum),
                        functions.lit(1L)).otherwise(functions.lit(0L))).alias("survivors"));
                var decisions = scope.bind(sizes.join(survivors, new String[]{"cluster"}, "inner")
                        .join(levels.select("cluster", "lambda"), new String[]{"cluster"}, "inner")
                        .withColumn("nextCluster", functions.when(functions.col("survivors").geq(2),
                                functions.concat(functions.lit(round + ":"), functions.col("seed").cast("string")))
                                .otherwise(functions.col("cluster"))));
                Column survives = functions.col("size").geq(minimum);
                Column fork = functions.col("survivors").geq(2);
                // Control scalars come from the already computed child sizes, avoiding a
                // second scan of all active observations and a separate fork-existence job.
                Row progress = decisions.agg(
                        functions.coalesce(functions.sum(functions.when(survives, functions.col("size")).otherwise(functions.lit(0L))), functions.lit(0L)),
                        functions.coalesce(functions.sum(functions.when(survives.and(fork), functions.lit(1L)).otherwise(functions.lit(0L))), functions.lit(0L)),
                        functions.coalesce(functions.sum(functions.when(functions.not(survives), functions.col("size")).otherwise(functions.lit(0L))), functions.lit(0L)))
                        .first();
                long nextCount = progress.getLong(0), newClusters = progress.getLong(1), departed = progress.getLong(2);
                if (nextCount + departed != remaining || (nextCount == remaining && newClusters == 0))
                    throw failure("SPATIAL_HDBSCAN_TREE_INVALID");
                // A child that starts a new cluster departs the parent with all its members;
                // a sole surviving child is continuation, not a new stability contribution.
                var exits = decisions.filter(functions.not(survives).or(fork)).select("cluster", "lambda", "size");
                departureHistory.append(exits);
                if (newClusters > 0) clusterHistory.append(decisions.filter(survives.and(fork)).select(
                        functions.col("nextCluster").alias("cluster"), functions.col("cluster").alias("parent"),
                        functions.col("lambda").alias("birth"), functions.col("size")));
                var membership = children.join(decisions, new String[]{"cluster", "component"}, "inner");
                if (departed > 0) observationHistory.append(membership.filter(functions.not(survives)).select("id", "cluster", "lambda"));
                remaining = nextCount;
                if (remaining > 0) {
                    active = scope.bind(membership.filter(survives).select(functions.col("id"), functions.col("nextCluster").alias("cluster")));
                    edges = scope.bind(retained.join(active.select(functions.col("id").alias("src")), new String[]{"src"}, "inner")
                            .join(active.select(functions.col("id").alias("dst")), new String[]{"dst"}, "inner"));
                }
            } finally {
                graph.unpersist(false);
            }
        }
        observations = observationHistory.materialize();
        if (observations.count() != tree.vertexCount()) throw failure("SPATIAL_HDBSCAN_TREE_INVALID");
        return new Result(clusterHistory.materialize(), departureHistory.materialize(), observations);
    }

    private static IllegalArgumentException failure(String code) { return new IllegalArgumentException(code); }
}
