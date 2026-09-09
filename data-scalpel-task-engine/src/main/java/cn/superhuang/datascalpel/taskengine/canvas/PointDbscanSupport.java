package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
import org.apache.spark.sql.*;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.apache.spark.sql.types.DataTypes;
import org.graphframes.GraphFrame;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import java.util.ArrayList;

/** Density-connected DBSCAN. Reuses the GraphFrames already bundled by Sedona; no Driver collection. */
final class PointDbscanSupport {
    private PointDbscanSupport() { }

    static Dataset<Row> schemaPlan(Dataset<Row> source, SpatialPointClusterConfiguration c) {
        var dependencies = new ArrayList<Column>();
        dependencies.add(source.col(CanvasNodeSupport.quoteIdentifier(c.pointGeometryColumnName())));
        dependencies.add(source.col(CanvasNodeSupport.quoteIdentifier(c.featureIdColumnName())));
        if (c.dbscan() != null && c.dbscan().usesTime()) dependencies.add(source.col(CanvasNodeSupport.quoteIdentifier(c.dbscan().timeColumnName())));
        // Compiler has only zero-row inputs. Preserve collective field dependencies without running
        // checkpoints/connected components. This schema plan must never be used as a real writer plan.
        Column members = functions.collect_list(functions.struct(dependencies.toArray(Column[]::new)))
                .over(Window.partitionBy());
        Column cluster = functions.udf((UDF1<scala.collection.Seq<Row>, Long>) ignored -> {
            throw new IllegalArgumentException("SPATIAL_CLUSTER_PREVIEW_NOT_EXECUTABLE");
        }, DataTypes.LongType).apply(members);
        Dataset<Row> result = source.withColumn(c.clusterIdColumnName(), cluster);
        return result.withColumn(c.noiseColumnName(), result.col(CanvasNodeSupport.quoteIdentifier(c.clusterIdColumnName())).isNull());
    }

    static Dataset<Row> run(Dataset<Row> source, SpatialPointClusterConfiguration c, double epsilon, long durationMicros) {
        var original = source.columns();
        var projection = new ArrayList<Column>();
        for (int i = 0; i < original.length; i++) projection.add(source.col(CanvasNodeSupport.quoteIdentifier(original[i])).alias("value_" + i));
        projection.add(source.col(CanvasNodeSupport.quoteIdentifier(c.pointGeometryColumnName())).alias("geometry"));
        projection.add(source.col(CanvasNodeSupport.quoteIdentifier(c.featureIdColumnName())).alias("feature_id"));
        if (c.dbscan().usesTime()) projection.add(functions.unix_micros(source.col(CanvasNodeSupport.quoteIdentifier(c.dbscan().timeColumnName()))).alias("time"));
        Dataset<Row> data = source.select(projection.toArray(Column[]::new));
        boolean geodesic = c.distanceMethod() == SpatialDistanceMethod.GEODESIC;
        Column validPoint = functions.udf((UDF1<Geometry, Boolean>) geometry -> {
            if (geometry == null || geometry.isEmpty()) return false;
            if (!(geometry instanceof Point p) || !geometry.isValid() || !Double.isFinite(p.getX()) || !Double.isFinite(p.getY())
                    || geodesic && (Math.abs(p.getX()) > 180 || Math.abs(p.getY()) > 90))
                throw new IllegalArgumentException("SPATIAL_CLUSTER_POINT_INVALID");
            return true;
        }, DataTypes.BooleanType).apply(data.col("geometry"));
        data = data.filter(validPoint);
        if (c.dbscan().usesTime()) data = data.filter(data.col("time").isNotNull());
        Column duplicates = functions.count(functions.lit(1)).over(Window.partitionBy("feature_id"));
        data = data.withColumn("identity_valid", functions.when(data.col("feature_id").isNull().or(duplicates.notEqual(1)),
                        functions.raise_error(functions.lit("SPATIAL_CLUSTER_FEATURE_ID_INVALID")).cast("boolean")).otherwise(true))
                .filter("identity_valid").drop("identity_valid")
                .withColumn("id", functions.monotonically_increasing_id()).checkpoint();
        // Checkpoint binds the internal identity once; user fields (including id/src/dst) are isolated.
        var left = data.alias("l"); var right = data.alias("r");
        Column distance = geodesic ? st_functions.ST_DistanceSpheroid(functions.col("l.geometry"), functions.col("r.geometry"))
                : st_functions.ST_Distance(functions.col("l.geometry"), functions.col("r.geometry"));
        Column near = distance.leq(epsilon);
        if (c.dbscan().usesTime()) {
            // Decimal subtraction avoids overflowing Long for extreme, but valid Spark timestamps.
            Column delta = functions.col("l.time").cast("decimal(20,0)").minus(functions.col("r.time").cast("decimal(20,0)"));
            near = near.and(functions.abs(delta).leq(durationMicros));
        }
        Dataset<Row> neighbors = left.join(right, near).select(functions.col("l.id").alias("src"), functions.col("r.id").alias("dst")).checkpoint();
        int minimum = ((SpatialPointClusterParameters.Dbscan) c.parameters()).minimumFeatures();
        Dataset<Row> cores = neighbors.groupBy("src").count().filter(functions.col("count").geq(minimum))
                .select(functions.col("src").alias("id")); // Self is a neighbor; duplicate positions retain distinct IDs.
        Dataset<Row> coreEdges = neighbors.join(cores.select(functions.col("id").alias("src")), new String[]{"src"}, "inner")
                .join(cores.select(functions.col("id").alias("dst")), new String[]{"dst"}, "inner").select("src", "dst");
        Dataset<Row> components = GraphFrame.apply(cores, coreEdges).connectedComponents().run();
        try {
            // Only core-to-core edges form components. A border point must not merge two components.
            Dataset<Row> membership = neighbors.join(components.select(functions.col("id").alias("dst"), functions.col("component")),
                    new String[]{"dst"}, "inner").groupBy("src").agg(functions.min("component").alias("cluster"));
            Dataset<Row> joined = data.join(membership, data.col("id").equalTo(membership.col("src")), "left");
            var outputs = new ArrayList<Column>();
            for (int i = 0; i < original.length; i++) outputs.add(joined.col("value_" + i).alias(original[i]));
            outputs.add(joined.col("cluster").cast("long").alias(c.clusterIdColumnName()));
            outputs.add(joined.col("cluster").isNull().alias(c.noiseColumnName()));
            return joined.select(outputs.toArray(Column[]::new)).checkpoint();
        } finally {
            // GraphFrames returns a persisted result; output is checkpointed before releasing it.
            components.unpersist(false);
        }
    }
}
