package cn.superhuang.datascalpel.taskengine.canvas;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;

/** Distributed EOM extraction and diagnostic assignment from the condensed tree. The only
 * Driver results are scalar convergence counts and the maximum density. Cluster identities,
 * relationships, departures and memberships stay in checkpoint-bound Spark tables. This uses
 * the explicitly documented prototype normalization, not a claim of Esri numeric equivalence.
 */
final class HdbscanDiagnostics {
    private HdbscanDiagnostics() { }

    /** id,cluster(long nullable),probability,outlier,exemplar,stability(nullable). */
    static Dataset<Row> run(HdbscanCondensedTree.Result tree) {
        try (var scope = new HdbscanCheckpoint.Scope()) {
            return scope.keep(run(tree, scope));
        }
    }

    static Dataset<Row> run(HdbscanCondensedTree.Result tree, HdbscanCheckpoint.Scope scope) {
        var stats = tree.departures().join(tree.clusters().select("cluster", "birth"), new String[]{"cluster"}, "inner")
                .groupBy("cluster").agg(functions.sum(functions.col("lambda").minus(functions.col("birth"))
                        .multiply(functions.col("size"))).alias("mass"), functions.max("lambda").alias("death"));
        var nodes = tree.clusters().join(stats, new String[]{"cluster"}, "inner").transform(scope::checkpoint);
        double globalDeath = nodes.agg(functions.max("death")).first().getDouble(0);
        var complete = eomDecisions(nodes, scope);

        // Inherit a selected ancestor before considering a descendant's own EOM decision.
        // EOM candidates below an already selected ancestor must not become extra clusters.
        var frontier = complete.filter(functions.col("cluster").equalTo(HdbscanCondensedTree.ROOT))
                .select(functions.col("cluster"), functions.lit(null).cast("string").alias("selected")).transform(scope::checkpoint);
        var ancestorHistory = new HdbscanRowHistory(frontier, scope);
        while (!frontier.isEmpty()) {
            ancestorHistory.append(frontier);
            frontier = scope.bind(complete.join(frontier.select(functions.col("cluster").alias("parent"),
                            functions.col("selected").alias("inherited")), new String[]{"parent"}, "inner")
                    .select(functions.col("cluster"), functions.when(functions.col("inherited").isNotNull(), functions.col("inherited"))
                            .when(functions.col("choose"), functions.col("cluster"))
                            .otherwise(functions.lit(null).cast("string")).alias("selected")));
        }
        var ancestors = ancestorHistory.materialize();
        if (ancestors.count() != nodes.count()) throw failure();

        var exits = tree.observations().join(ancestors, new String[]{"cluster"}, "inner");
        var labels = exits.filter(functions.col("selected").isNotNull()).groupBy("selected")
                .agg(functions.min("id").alias("label"), functions.sum(functions.when(functions.col("lambda")
                        .equalTo(Double.POSITIVE_INFINITY), functions.lit(1L)).otherwise(functions.lit(0L))).alias("infiniteMembers"));
        var selectedStats = nodes.select(functions.col("cluster").alias("selected"), functions.col("mass").alias("selectedMass"),
                functions.col("size").alias("selectedSize"), functions.col("death").alias("selectedDeath"));
        var children = tree.clusters().groupBy("parent").count().withColumnRenamed("parent", "cluster")
                .withColumnRenamed("count", "children");
        var assigned = exits.join(labels, new String[]{"selected"}, "left")
                .join(selectedStats, new String[]{"selected"}, "left")
                .join(complete.select("cluster", "descendantDeath"), new String[]{"cluster"}, "inner")
                .join(nodes.select("cluster", "death"), new String[]{"cluster"}, "inner")
                .join(children, new String[]{"cluster"}, "left");
        Column selected = functions.col("selected").isNotNull();
        Column persistence = Double.isInfinite(globalDeath)
                ? functions.col("infiniteMembers").cast("double").divide(functions.col("selectedSize"))
                : functions.col("selectedMass").divide(functions.col("selectedSize")).divide(functions.lit(globalDeath));
        return assigned.select(functions.col("id"), functions.col("label").alias("cluster"),
                functions.when(selected, membership(functions.col("lambda"), functions.col("selectedDeath")))
                        .otherwise(functions.lit(0d)).alias("probability"),
                functions.lit(1d).minus(membership(functions.col("lambda"), functions.col("descendantDeath"))).alias("outlier"),
                selected.and(functions.coalesce(functions.col("children"), functions.lit(0L)).equalTo(0))
                        .and(functions.col("lambda").equalTo(functions.col("death"))).alias("exemplar"),
                functions.when(selected, bounded(persistence)).otherwise(functions.lit(null).cast("double")).alias("stability")).transform(scope::checkpoint);
    }

    /** Completed decisions are append-only history. Only direct child contributions whose
     * parent is still unresolved remain in the working relation. Each ready parent sums the
     * original contributions once; no incremental floating-point subtotal changes the formula.
     */
    static Dataset<Row> eomDecisions(Dataset<Row> nodes) {
        try (var scope = new HdbscanCheckpoint.Scope()) {
            return scope.keep(eomDecisions(nodes, scope));
        }
    }

    static Dataset<Row> eomDecisions(Dataset<Row> nodes, HdbscanCheckpoint.Scope scope) {
        var empty = nodes.limit(0).select(functions.col("cluster"), functions.col("parent"),
                functions.lit(false).alias("choose"), functions.lit(0d).alias("best"), functions.lit(0d).alias("descendantDeath"));
        var history = new HdbscanRowHistory(empty, scope);
        var waiting = empty.select("cluster", "parent", "best", "descendantDeath");
        var pending = nodes;
        long remaining = pending.count();
        while (remaining > 0) {
            var unresolvedParents = pending.select(functions.col("parent").alias("cluster")).distinct();
            var ready = pending.join(unresolvedParents, new String[]{"cluster"}, "left_anti");
            var totals = waiting.join(ready.select(functions.col("cluster").alias("parent")), new String[]{"parent"}, "inner")
                    .groupBy("parent").agg(functions.sum("best").alias("childMass"),
                            functions.max("descendantDeath").alias("childDeath")).withColumnRenamed("parent", "cluster");
            var decisions = history.append(ready.join(totals, new String[]{"cluster"}, "left")
                    .withColumn("childMass", functions.coalesce(functions.col("childMass"), functions.lit(0d)))
                    .withColumn("choose", functions.col("cluster").notEqual(HdbscanCondensedTree.ROOT)
                            .and(functions.col("mass").geq(functions.col("childMass"))))
                    .select(functions.col("cluster"), functions.col("parent"), functions.col("choose"),
                            functions.when(functions.col("choose"), functions.col("mass")).otherwise(functions.col("childMass")).alias("best"),
                            functions.greatest(functions.col("death"), functions.col("childDeath")).alias("descendantDeath")));
            pending = scope.bind(pending.join(decisions.select("cluster"), new String[]{"cluster"}, "left_anti"));
            long next = pending.count();
            if (next >= remaining) throw failure();
            remaining = next;
            if (remaining > 0) {
                waiting = waitingChildren(waiting, decisions, pending, scope);
            }
        }
        return history.materialize();
    }

    static Dataset<Row> waitingChildren(Dataset<Row> waiting, Dataset<Row> decisions, Dataset<Row> pending) {
        try (var scope = new HdbscanCheckpoint.Scope()) {
            return scope.keep(waitingChildren(waiting, decisions, pending, scope));
        }
    }

    static Dataset<Row> waitingChildren(Dataset<Row> waiting, Dataset<Row> decisions, Dataset<Row> pending, HdbscanCheckpoint.Scope scope) {
        return scope.bind(waiting.unionByName(decisions.select("cluster", "parent", "best", "descendantDeath"))
                .join(pending.select(functions.col("cluster").alias("parent")), new String[]{"parent"}, "inner"));
    }

    private static Column membership(Column exit, Column death) {
        return functions.when(death.equalTo(0).or(exit.equalTo(Double.POSITIVE_INFINITY)), functions.lit(1d))
                .otherwise(bounded(functions.least(exit, death).divide(death)));
    }

    private static Column bounded(Column value) {
        return functions.when(value.isNull().or(functions.isnan(value)).or(value.lt(-1e-12)).or(value.gt(1 + 1e-12)),
                        functions.raise_error(functions.lit("SPATIAL_HDBSCAN_NUMERIC_RANGE_INVALID")).cast("double"))
                .otherwise(functions.greatest(functions.lit(0d), functions.least(functions.lit(1d), value)));
    }

    private static IllegalArgumentException failure() { return new IllegalArgumentException("SPATIAL_HDBSCAN_TREE_INVALID"); }
}
