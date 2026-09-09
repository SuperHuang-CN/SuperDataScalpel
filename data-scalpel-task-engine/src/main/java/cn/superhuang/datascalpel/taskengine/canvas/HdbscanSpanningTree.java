package cn.superhuang.datascalpel.taskengine.canvas;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.graphframes.GraphFrame;

/** Exact mutual-reachability reference plan and distributed Boruvka MST used by HDBSCAN.
 * Input is the validated/checkpointed private {id:long, geometry:Point} projection, never user
 * field aliases. Production recovers exact cut candidates from feasible upper bounds each
 * round; the complete graph plan remains a reference, not a runtime materialization.
 */
final class HdbscanSpanningTree {
    private HdbscanSpanningTree() { }

    record Plan(Dataset<Row> coreDistances, Dataset<Row> edges) { }
    record Tree(Dataset<Row> vertices, Dataset<Row> edges, long vertexCount, boolean enoughObservations) { }

    /** Analyzer-only construction. The minimum includes self, so rank k-1 among other IDs. */
    static Plan plan(Dataset<Row> points, int minimum, boolean geodesic) {
        if (minimum < 2 || minimum > 100_000) throw failure("INVALID_SPATIAL_CLUSTER_MINIMUM_FEATURES");
        var left = points.alias("a"); var right = points.alias("b");
        Column distance = geodesic ? st_functions.ST_DistanceSpheroid(functions.col("a.geometry"), functions.col("b.geometry"))
                : st_functions.ST_Distance(functions.col("a.geometry"), functions.col("b.geometry"));
        var distances = left.join(right, functions.col("a.id").lt(functions.col("b.id")))
                .select(functions.col("a.id").alias("src"), functions.col("b.id").alias("dst"), distance.alias("distance"));
        Column d = distances.col("distance");
        distances = distances.withColumn("distance", functions.when(d.isNull().or(functions.isnan(d)).or(d.lt(0)).or(d.gt(Double.MAX_VALUE)),
                        functions.raise_error(functions.lit("SPATIAL_HDBSCAN_NUMERIC_RANGE_INVALID")).cast("double"))
                .otherwise(d));
        var directed = distances.unionByName(distances.select(functions.col("dst").alias("src"),functions.col("src").alias("dst"),functions.col("distance")));
        var ranked = directed.withColumn("rank",functions.row_number().over(Window.partitionBy("src").orderBy("distance","dst")))
                .filter(functions.col("rank").leq(minimum - 1));
        var cores = ranked.groupBy("src").agg(functions.max("distance").alias("core"),functions.count("dst").alias("neighbors"))
                .filter(functions.col("neighbors").equalTo(minimum - 1)).select(functions.col("src").alias("id"),functions.col("core"));
        return new Plan(cores, weightedEdges(distances, cores));
    }

    private static Dataset<Row> weightedEdges(Dataset<Row> distances, Dataset<Row> cores) {
        return distances.join(cores.select(functions.col("id").alias("src"),functions.col("core").alias("core_src")),new String[]{"src"},"inner")
                .join(cores.select(functions.col("id").alias("dst"),functions.col("core").alias("core_dst")),new String[]{"dst"},"inner")
                .select(functions.col("src"),functions.col("dst"),functions.greatest(functions.col("distance"),functions.col("core_src"),functions.col("core_dst")).alias("weight"));
    }

    /** Actual execution, with only scalar cardinalities returned to the Driver. */
    static Tree run(Dataset<Row> points, int minimum, boolean geodesic) {
        try (var scope = new HdbscanCheckpoint.Scope()) {
            var tree = run(points, minimum, geodesic, scope);
            scope.keep(tree.vertices()); scope.keep(tree.edges());
            return tree;
        }
    }

    static Tree run(Dataset<Row> points, int minimum, boolean geodesic, HdbscanCheckpoint.Scope scope) {
        // Validate the configuration before starting a job. Bind identity AND geometry once:
        // checkpointing only the ID column permits the edge plan to re-evaluate volatile IDs.
        if (minimum < 2 || minimum > 100_000) throw failure("INVALID_SPATIAL_CLUSTER_MINIMUM_FEATURES");
        if (points.sparkSession().sparkContext().getCheckpointDir().isEmpty())
            throw failure("SPATIAL_CLUSTER_CHECKPOINT_NOT_CONFIGURED");
        points = points.select("id", "geometry").transform(scope::checkpoint);
        Dataset<Row> vertices = points.select("id").transform(scope::checkpoint);
        long count = vertices.count();
        if (count > HdbscanHierarchy.MAX_VERTICES) throw failure("SPATIAL_HDBSCAN_HIERARCHY_LIMIT_EXCEEDED");
        Dataset<Row> forest = vertices.limit(0).select(functions.col("id").alias("src"), functions.col("id").alias("dst"),
                functions.lit(0d).alias("weight")).transform(scope::checkpoint);
        if (count < minimum) return new Tree(vertices,forest,count,false);
        Dataset<Row> cores = HdbscanCoreDistances.run(points, minimum, geodesic, scope);
        var weightedPoints = points.join(cores, new String[]{"id"}, "inner").transform(scope::checkpoint);
        Dataset<Row> components = vertices.select(functions.col("id"),functions.col("id").alias("component")).transform(scope::checkpoint);
        long remaining = count;
        for (int round = 0; remaining > 1; round++) {
            if (round >= 64) throw failure("SPATIAL_HDBSCAN_TREE_INVALID");
            var prepared = weightedPoints.join(components, new String[]{"id"}, "inner").transform(scope::checkpoint);
            var bounds = HdbscanCutCandidates.runBounds(prepared, geodesic, scope);
            var edges = contractEdges(HdbscanCutCandidates.fromBounds(prepared, bounds, geodesic), components).transform(scope::checkpoint);
            var candidates = withComponents(edges, components);
            // A single total edge order across all components prevents tied minimum edges
            // forming cycles. The same undirected edge selected from both ends is retained once.
            var chosen = leastOutgoingEdges(candidates);
            forest = forest.unionByName(chosen).transform(scope::checkpoint);
            Dataset<Row> next = GraphFrame.apply(vertices,forest.select("src","dst")).connectedComponents().run();
            try {
                scope.trackGraphResult(next, vertices, forest);
                components = next.select("id","component").transform(scope::checkpoint);
            } finally { next.unpersist(false); }
            long current = components.select("component").distinct().count();
            if (current >= remaining) throw failure("SPATIAL_HDBSCAN_TREE_INVALID");
            remaining = current;
        }
        if (forest.count() != count - 1) throw failure("SPATIAL_HDBSCAN_TREE_INVALID");
        return new Tree(vertices,forest,count,true);
    }

    /** Lazy exact quotient; at most c*(c-1)/2 edges for c current components. */
    static Dataset<Row> contractEdges(Dataset<Row> edges, Dataset<Row> components) {
        var paired = withComponents(edges, components)
                .withColumn("component_low", functions.least(functions.col("left_component"), functions.col("right_component")))
                .withColumn("component_high", functions.greatest(functions.col("left_component"), functions.col("right_component")));
        return paired.groupBy("component_low", "component_high").agg(functions.min(edgeOrder()).alias("edge"))
                .select(functions.col("edge.src").alias("src"), functions.col("edge.dst").alias("dst"), functions.col("edge.weight").alias("weight"));
    }

    /** Associative minimum permits partial aggregation instead of sorting every outgoing edge. */
    static Dataset<Row> leastOutgoingEdges(Dataset<Row> candidates) {
        var directed = candidates.select(functions.col("left_component").alias("component"), edgeOrder().alias("edge"))
                .unionByName(candidates.select(functions.col("right_component").alias("component"), edgeOrder().alias("edge")));
        return directed.groupBy("component").agg(functions.min("edge").alias("edge"))
                .select(functions.col("edge.src").alias("src"), functions.col("edge.dst").alias("dst"), functions.col("edge.weight").alias("weight"))
                .dropDuplicates("src", "dst");
    }

    static Dataset<Row> withComponents(Dataset<Row> edges, Dataset<Row> components) {
        return edges.join(components.select(functions.col("id").alias("src"), functions.col("component").alias("left_component")), new String[]{"src"}, "inner")
                .join(components.select(functions.col("id").alias("dst"), functions.col("component").alias("right_component")), new String[]{"dst"}, "inner")
                .filter(functions.col("left_component").notEqual(functions.col("right_component")));
    }

    private static Column edgeOrder() {
        return functions.struct(functions.col("weight"), functions.col("src"), functions.col("dst"));
    }

    private static IllegalArgumentException failure(String code) { return new IllegalArgumentException(code); }
}
