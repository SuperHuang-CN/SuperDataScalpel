package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod;
import cn.superhuang.data.scalpel.contract.task.SpatialPointClusterConfiguration;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import java.util.ArrayList;

/** Node-facing HDBSCAN projection and preparation. Compiler never invokes runtime clustering. */
final class PointHdbscanSupport {
    private PointHdbscanSupport() { }

    static Dataset<Row> schemaPlan(Dataset<Row> source, SpatialPointClusterConfiguration c) {
        var members = functions.collect_list(functions.struct(
                source.col(CanvasNodeSupport.quoteIdentifier(c.pointGeometryColumnName())),
                source.col(CanvasNodeSupport.quoteIdentifier(c.featureIdColumnName())))).over(Window.partitionBy());
        var schema = new StructType().add("cluster", DataTypes.LongType, true)
                .add("probability", DataTypes.DoubleType, false).add("outlier", DataTypes.DoubleType, false)
                .add("exemplar", DataTypes.BooleanType, false).add("stability", DataTypes.DoubleType, true);
        Column diagnostic = functions.udf((UDF1<scala.collection.Seq<Row>, Row>) ignored -> {
            throw new IllegalArgumentException("SPATIAL_CLUSTER_PREVIEW_NOT_EXECUTABLE");
        }, schema).apply(members);
        var output = new ArrayList<Column>();
        for (String name : source.columns()) output.add(source.col(CanvasNodeSupport.quoteIdentifier(name)));
        append(output, diagnostic.getField("cluster"), diagnostic.getField("probability"), diagnostic.getField("outlier"),
                diagnostic.getField("exemplar"), diagnostic.getField("stability"), c);
        return source.select(output.toArray(Column[]::new));
    }

    static Dataset<Row> run(Dataset<Row> source, SpatialPointClusterConfiguration c) {
        try (var scope = new HdbscanCheckpoint.Scope()) {
            return scope.keep(run(source, c, scope));
        }
    }

    private static Dataset<Row> run(Dataset<Row> source, SpatialPointClusterConfiguration c, HdbscanCheckpoint.Scope scope) {
        var original = source.columns();
        var projection = new ArrayList<Column>();
        for (int i = 0; i < original.length; i++) projection.add(source.col(CanvasNodeSupport.quoteIdentifier(original[i])).alias("value_" + i));
        projection.add(source.col(CanvasNodeSupport.quoteIdentifier(c.pointGeometryColumnName())).alias("geometry"));
        projection.add(source.col(CanvasNodeSupport.quoteIdentifier(c.featureIdColumnName())).alias("feature_id"));
        var data = source.select(projection.toArray(Column[]::new));
        boolean geodesic = c.distanceMethod() == SpatialDistanceMethod.GEODESIC;
        Column validPoint = functions.udf((UDF1<Geometry, Boolean>) geometry -> {
            if (geometry == null || geometry.isEmpty()) return false;
            if (!(geometry instanceof Point p) || !geometry.isValid() || !Double.isFinite(p.getX()) || !Double.isFinite(p.getY())
                    || geodesic && (Math.abs(p.getX()) > 180 || Math.abs(p.getY()) > 90))
                throw new IllegalArgumentException("SPATIAL_CLUSTER_POINT_INVALID");
            return true;
        }, DataTypes.BooleanType).apply(data.col("geometry"));
        data = data.filter(validPoint);
        Column duplicate = functions.count(functions.lit(1)).over(Window.partitionBy("feature_id"));
        data = data.withColumn("identity_valid", functions.when(data.col("feature_id").isNull().or(duplicate.notEqual(1)),
                        functions.raise_error(functions.lit("SPATIAL_CLUSTER_FEATURE_ID_INVALID")).cast("boolean")).otherwise(true))
                .filter("identity_valid").drop("identity_valid")
                .withColumn("id", functions.monotonically_increasing_id()).transform(scope::checkpoint);
        var mst = HdbscanSpanningTree.run(data.select("id", "geometry"), c.parameters().minimumFeatures(), geodesic, scope);
        var diagnostics = HdbscanDiagnostics.run(HdbscanCondensedTree.run(mst, c.parameters().minimumFeatures(), scope), scope);
        var joined = data.join(diagnostics, new String[]{"id"}, "inner");
        var output = new ArrayList<Column>();
        for (int i = 0; i < original.length; i++) output.add(joined.col("value_" + i).alias(original[i]));
        append(output, joined.col("cluster"), joined.col("probability"), joined.col("outlier"),
                joined.col("exemplar"), joined.col("stability"), c);
        return joined.select(output.toArray(Column[]::new)).transform(scope::checkpoint);
    }

    private static void append(ArrayList<Column> output, Column cluster, Column probability, Column outlier,
            Column exemplar, Column stability, SpatialPointClusterConfiguration c) {
        output.add(cluster.alias(c.clusterIdColumnName()));
        output.add(cluster.isNull().alias(c.noiseColumnName()));
        output.add(probability.alias(c.hdbscan().probabilityColumnName()));
        output.add(outlier.alias(c.hdbscan().outlierColumnName()));
        output.add(exemplar.alias(c.hdbscan().exemplarColumnName()));
        output.add(stability.alias(c.hdbscan().stabilityColumnName()));
    }
}
