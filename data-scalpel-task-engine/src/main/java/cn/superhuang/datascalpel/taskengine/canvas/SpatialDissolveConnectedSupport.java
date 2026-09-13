package cn.superhuang.datascalpel.taskengine.canvas;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.apache.spark.sql.sedona_sql.expressions.st_predicates;
import org.graphframes.GraphFrame;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;

import java.nio.file.Path;
import java.util.ArrayList;

/** Distributed, exact 2D connected components for Dissolve Boundaries without fields. */
final class SpatialDissolveConnectedSupport {
    private SpatialDissolveConnectedSupport() { }

    record Prepared(Dataset<Row> source, String groupingColumnName) { }

    static boolean ensureCheckpointDirectory(Dataset<Row> source, CanvasNodeIssueSink issues) {
        var sparkContext = source.sparkSession().sparkContext();
        if (sparkContext.getCheckpointDir().nonEmpty()) return true;
        String configured = sparkContext.getConf().contains("spark.checkpoint.dir")
                ? sparkContext.getConf().get("spark.checkpoint.dir")
                : null;
        if (!CanvasNodeSupport.blank(configured)) {
            sparkContext.setCheckpointDir(configured.trim());
            return true;
        }
        if (sparkContext.master().startsWith("local")) {
            String applicationId = sparkContext.applicationId()
                    .replaceAll("[^A-Za-z0-9._-]", "_");
            sparkContext.setCheckpointDir(Path.of(
                    System.getProperty("java.io.tmpdir"),
                    "datascalpel-spark-checkpoints",
                    applicationId).toUri().toString());
            return true;
        }
        issues.error(
                "SPATIAL_DISSOLVE_CHECKPOINT_NOT_CONFIGURED",
                "集群模式执行空间连通组 Dissolve 前必须配置 spark.checkpoint.dir，并指向所有执行器可访问的文件系统",
                "configuration.dissolve.groupingMode"
        );
        return false;
    }

    static Prepared prepare(Dataset<Row> source, String geometryColumnName) {
        String identityName = TrackNodeSupport.internalName(
                source, "__datascalpel_dissolve_identity");
        String componentName = TrackNodeSupport.internalName(
                source, "__datascalpel_dissolve_component");
        String geometryReference = CanvasNodeSupport.quoteIdentifier(geometryColumnName);
        Column geometry = source.col(geometryReference);
        var geometryType = source.schema().apply(geometryColumnName).dataType();
        Column checked = functions.udf(
                (UDF1<Geometry, Geometry>) SpatialDissolveConnectedSupport::checkedPolygon,
                geometryType
        ).apply(geometry);
        Dataset<Row> prepared = source.withColumn(geometryColumnName, checked);
        Column preparedGeometry = prepared.col(geometryReference);
        prepared = prepared
                .filter(preparedGeometry.isNotNull())
                .filter(functions.not(st_functions.ST_IsEmpty(preparedGeometry)))
                .withColumn(identityName, functions.monotonically_increasing_id())
                .checkpoint();

        Dataset<Row> vertices = prepared.select(
                prepared.col(CanvasNodeSupport.quoteIdentifier(identityName)).alias("id"));
        Dataset<Row> left = prepared.alias("left_dissolve");
        Dataset<Row> right = prepared.alias("right_dissolve");
        Column leftId = functions.col("left_dissolve."
                + CanvasNodeSupport.quoteIdentifier(identityName));
        Column rightId = functions.col("right_dissolve."
                + CanvasNodeSupport.quoteIdentifier(identityName));
        Column leftGeometry = functions.col("left_dissolve." + geometryReference);
        Column rightGeometry = functions.col("right_dissolve." + geometryReference);
        Dataset<Row> edges = left.join(
                        right,
                        st_predicates.ST_Intersects(leftGeometry, rightGeometry),
                        "inner")
                .filter(leftId.lt(rightId))
                .select(leftId.alias("src"), rightId.alias("dst"));
        Dataset<Row> components = GraphFrame.apply(vertices, edges)
                .connectedComponents()
                .run();
        try {
            Dataset<Row> membership = components.select(
                    functions.col("id").alias(identityName),
                    functions.col("component").alias(componentName));
            Dataset<Row> joined = prepared.join(
                    membership,
                    new String[]{identityName},
                    "inner");
            var projection = new ArrayList<Column>(source.columns().length + 1);
            for (String columnName : source.columns()) {
                projection.add(joined.col(CanvasNodeSupport.quoteIdentifier(columnName)));
            }
            projection.add(joined.col(CanvasNodeSupport.quoteIdentifier(componentName)));
            return new Prepared(
                    joined.select(projection.toArray(Column[]::new)).checkpoint(),
                    componentName
            );
        } finally {
            components.unpersist(false);
        }
    }

    private static Geometry checkedPolygon(Geometry geometry) {
        if (geometry == null) return null;
        if (!(geometry instanceof Polygon) && !(geometry instanceof MultiPolygon)
                || !geometry.isValid()) {
            throw new IllegalArgumentException("SPATIAL_DISSOLVE_GEOMETRY_INVALID");
        }
        for (var coordinate : geometry.getCoordinates()) {
            if (!Double.isFinite(coordinate.getX()) || !Double.isFinite(coordinate.getY())) {
                throw new IllegalArgumentException("SPATIAL_DISSOLVE_GEOMETRY_INVALID");
            }
        }
        return geometry;
    }
}
