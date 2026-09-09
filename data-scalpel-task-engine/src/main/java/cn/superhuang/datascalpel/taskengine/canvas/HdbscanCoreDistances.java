package cn.superhuang.datascalpel.taskengine.canvas;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.apache.spark.sql.sedona_sql.expressions.st_predicates;
import org.apache.spark.sql.types.DataTypes;

/** Exact self-inclusive core distances. KNN is only a seed, never the final neighbor set.
 * Private input IDs and Point geometries must already be bound by the caller's checkpoint.
 */
final class HdbscanCoreDistances {
    private HdbscanCoreDistances() { }

    static Dataset<Row> run(Dataset<Row> points, int minimum, boolean geodesic) {
        try (var scope = new HdbscanCheckpoint.Scope()) {
            return scope.keep(run(points, minimum, geodesic, scope));
        }
    }

    static Dataset<Row> run(Dataset<Row> points, int minimum, boolean geodesic, HdbscanCheckpoint.Scope scope) {
        // Bind the seed result once, including the absence of a usable upper bound.
        var radii = seedRadii(points, minimum, geodesic).transform(scope::checkpoint);
        return fromRadii(points, radii, minimum, geodesic).transform(scope::checkpoint);
    }

    static Dataset<Row> seedRadii(Dataset<Row> points, int minimum, boolean geodesic) {
        validateMinimum(minimum);
        var left = points.select(functions.col("id").alias("src"), functions.col("geometry").alias("source_geometry"));
        var right = points.select(functions.col("id").alias("dst"), functions.col("geometry").alias("target_geometry"));
        // Request k, because the same observation may be among the returned identities.
        // Even an imperfect KNN ordering is safe: k-1 distinct others give an upper bound.
        var seeds = left.join(right, st_predicates.ST_KNN(functions.col("source_geometry"), functions.col("target_geometry"),
                        functions.lit(minimum), functions.lit(geodesic)), "inner")
                .filter(functions.col("src").notEqual(functions.col("dst")))
                .select(functions.col("src"), functions.col("dst"),
                        pairDistance(geodesic).alias("distance"))
                .dropDuplicates("src", "dst");
        return seeds.groupBy("src").agg(functions.count("dst").alias("neighbors"), functions.max("distance").alias("radius"))
                .filter(functions.col("neighbors").geq(minimum - 1))
                .select(functions.col("src").alias("id"), functions.col("radius"));
    }

    /** Lazy radius recovery plus exact fallback for IDs without a sufficient seed set. */
    static Dataset<Row> fromRadii(Dataset<Row> points, Dataset<Row> radii, int minimum, boolean geodesic) {
        validateMinimum(minimum);
        // Radius recovery restores all ties and all distinct identities at repeated positions.
        var ranked = candidateDistances(points, radii, geodesic)
                .withColumn("rank", functions.row_number().over(Window.partitionBy("src").orderBy("distance", "dst")))
                .filter(functions.col("rank").leq(minimum - 1));
        return ranked.groupBy("src").agg(functions.max("distance").alias("core"), functions.count("dst").alias("neighbors"))
                .filter(functions.col("neighbors").equalTo(minimum - 1))
                .select(functions.col("src").alias("id"), functions.col("core"));
    }

    static Dataset<Row> candidateDistances(Dataset<Row> points, Dataset<Row> radii, boolean geodesic) {
        var left = points.select(functions.col("id").alias("src"), functions.col("geometry").alias("source_geometry"));
        var right = points.select(functions.col("id").alias("dst"), functions.col("geometry").alias("target_geometry"));
        var searches = left.join(radii, functions.col("src").equalTo(functions.col("id")), "inner");
        Column radius = searchRadius(functions.col("radius"), geodesic);
        var recovered = searches.join(right, st_predicates.ST_DWithin(functions.col("source_geometry"), functions.col("target_geometry"),
                        radius, functions.lit(geodesic)), "inner")
                .select("src", "dst", "source_geometry", "target_geometry");
        var missing = left.join(radii, functions.col("src").equalTo(functions.col("id")), "left_anti");
        var fallback = missing.crossJoin(right).select("src", "dst", "source_geometry", "target_geometry");
        return recovered.unionByName(fallback).filter(functions.col("src").notEqual(functions.col("dst")))
                .select(functions.col("src"), functions.col("dst"),
                        pairDistance(geodesic).alias("distance"));
    }

    static Column pairDistance(boolean geodesic) {
        Column ascending = functions.col("src").lt(functions.col("dst"));
        Column low = functions.when(ascending, functions.col("source_geometry")).otherwise(functions.col("target_geometry"));
        Column high = functions.when(ascending, functions.col("target_geometry")).otherwise(functions.col("source_geometry"));
        return distance(low, high, geodesic);
    }

    /** Candidate-only padding covers inverse-distance direction/rounding differences;
     * actual ranking/edge weights still use the unpadded canonical distance.
     */
    static Column searchRadius(Column bound, boolean geodesic) {
        return functions.udf((UDF1<Double, Double>) value -> {
            double padding = Math.max(geodesic ? 1e-6 : 0, value * 1e-12);
            return Math.min(Double.MAX_VALUE, Math.nextUp(value + padding));
        }, DataTypes.DoubleType).apply(bound);
    }

    static Column distance(Column left, Column right, boolean geodesic) {
        Column value = geodesic ? st_functions.ST_DistanceSpheroid(left, right) : st_functions.ST_Distance(left, right);
        return functions.when(value.isNull().or(functions.isnan(value)).or(value.lt(0)).or(value.gt(Double.MAX_VALUE)),
                functions.raise_error(functions.lit("SPATIAL_HDBSCAN_NUMERIC_RANGE_INVALID")).cast("double")).otherwise(value);
    }

    private static void validateMinimum(int minimum) {
        if (minimum < 2 || minimum > 100_000) throw new IllegalArgumentException("INVALID_SPATIAL_CLUSTER_MINIMUM_FEATURES");
    }
}
