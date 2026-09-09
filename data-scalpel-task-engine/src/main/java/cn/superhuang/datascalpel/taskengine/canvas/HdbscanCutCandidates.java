package cn.superhuang.datascalpel.taskengine.canvas;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_predicates;

/** Exact outgoing-cut candidates, not a KNN approximation of the mutual-reachability graph.
 * Prepared rows contain private id, geometry, core, component; components only merge.
 */
final class HdbscanCutCandidates {
    private HdbscanCutCandidates() { }

    static Dataset<Row> runBounds(Dataset<Row> prepared, boolean geodesic) {
        try (var scope = new HdbscanCheckpoint.Scope()) {
            return scope.keep(runBounds(prepared, geodesic, scope));
        }
    }

    static Dataset<Row> runBounds(Dataset<Row> prepared, boolean geodesic, HdbscanCheckpoint.Scope scope) {
        var representatives = representatives(prepared).transform(scope::checkpoint);
        // Freeze absence/presence as well as values before branching into the fallback.
        // Re-evaluating a tied KNN plan independently on both branches is not a snapshot.
        var seeds = nearbyBounds(prepared, representatives, geodesic).transform(scope::checkpoint);
        return completeBounds(representatives, seeds, geodesic).transform(scope::checkpoint);
    }

    static Dataset<Row> seedBounds(Dataset<Row> prepared, boolean geodesic) {
        return nearbyBounds(prepared, representatives(prepared), geodesic);
    }

    private static Dataset<Row> representatives(Dataset<Row> prepared) {
        var representativeIds = prepared.groupBy("component").agg(functions.min("id").alias("id")).select("id");
        return prepared.join(representativeIds, new String[]{"id"}, "inner");
    }

    private static Dataset<Row> nearbyBounds(Dataset<Row> prepared, Dataset<Row> representatives, boolean geodesic) {
        var left = left(prepared);
        var right = right(representatives);
        // At most one representative belongs to the query's component. Two candidates
        // normally provide an outgoing edge, even if the query's own representative wins.
        var seeds = left.join(right, st_predicates.ST_KNN(functions.col("source_geometry"), functions.col("target_geometry"),
                        functions.lit(2), functions.lit(geodesic)), "inner")
                .filter(functions.col("left_component").notEqual(functions.col("right_component")));
        return boundPerComponent(seeds, geodesic);
    }

    static Dataset<Row> completeBounds(Dataset<Row> representatives, Dataset<Row> bounds, boolean geodesic) {
        // A missing KNN result must not remove a cut. Two distinct global representatives
        // provide a feasible outside edge for every component. This is a two-row relation,
        // not a collected point set or a Cartesian product with all observations.
        var missing = left(representatives).join(bounds,
                functions.col("left_component").equalTo(functions.col("component")), "left_anti");
        var anchors = right(representatives.orderBy("id").limit(2));
        var fallback = missing.crossJoin(anchors)
                .filter(functions.col("left_component").notEqual(functions.col("right_component")));
        return bounds.unionByName(boundPerComponent(fallback, geodesic));
    }

    private static Dataset<Row> boundPerComponent(Dataset<Row> pairs, boolean geodesic) {
        return pairs.select(functions.col("left_component").alias("component"), weight(geodesic).alias("bound"))
                .groupBy("component").agg(functions.min("bound").alias("bound"));
    }

    /** Every exact cut minimum is <= its feasible bound. Restore all qualifying pairs,
     * including ties; normalize original endpoints only after the directed searches.
     */
    static Dataset<Row> fromBounds(Dataset<Row> prepared, Dataset<Row> bounds, boolean geodesic) {
        var searches = left(prepared).join(bounds, functions.col("left_component").equalTo(functions.col("component")), "inner")
                .filter(functions.col("source_core").leq(functions.col("bound")));
        Column radius = HdbscanCoreDistances.searchRadius(functions.col("bound"), geodesic);
        var pairs = searches.join(right(prepared), st_predicates.ST_DWithin(functions.col("source_geometry"), functions.col("target_geometry"),
                        radius, functions.lit(geodesic)), "inner")
                .filter(functions.col("left_component").notEqual(functions.col("right_component")))
                .filter(functions.col("target_core").leq(functions.col("bound")))
                .withColumn("weight", weight(geodesic)).filter(functions.col("weight").leq(functions.col("bound")));
        return pairs.select(functions.least(functions.col("src"), functions.col("dst")).alias("src"),
                        functions.greatest(functions.col("src"), functions.col("dst")).alias("dst"), functions.col("weight"))
                .dropDuplicates("src", "dst");
    }

    private static Column weight(boolean geodesic) {
        // Keep distance argument order identical to the dense src<dst oracle, including
        // floating-point geodesic implementations whose inverse solve may be asymmetric.
        return functions.greatest(HdbscanCoreDistances.pairDistance(geodesic),
                functions.col("source_core"), functions.col("target_core"));
    }

    private static Dataset<Row> left(Dataset<Row> prepared) {
        return prepared.select(functions.col("id").alias("src"), functions.col("geometry").alias("source_geometry"),
                functions.col("core").alias("source_core"), functions.col("component").alias("left_component"));
    }

    private static Dataset<Row> right(Dataset<Row> prepared) {
        return prepared.select(functions.col("id").alias("dst"), functions.col("geometry").alias("target_geometry"),
                functions.col("core").alias("target_core"), functions.col("component").alias("right_component"));
    }
}
