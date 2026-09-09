package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.*;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.api.java.UDF2;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Generator;
import org.apache.spark.sql.catalyst.expressions.SubqueryExpression;
import org.apache.spark.sql.catalyst.expressions.WindowExpression;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.NumericType;
import org.locationtech.jts.geom.Geometry;

import java.util.ArrayList;

/** Adds a private lazy footprint column before segmentation so gaps use original observations. */
final class TrackAreaPlan {
    private static final String PATH = "configuration.reconstruction.areaGeometry";
    private TrackAreaPlan() { }

    static void validate(SparkCanvasTable source, TrackReconstructConfiguration c, CanvasNodeIssueSink issues) {
        if (!c.usesAreaGeometry() || source == null) return;
        var options = c.reconstruction().areaGeometry();
        geodesicStep(c, issues);
        var geometry = CanvasNodeSupport.columns(source.schema()).get(c.pointGeometryColumnName());
        if (geometry == null || geometry.geometry() == null) return;
        if (geometry.geometry().dimension() != CoordinateDimension.XY)
            issues.error("TRACK_AREA_XY_REQUIRED", "面轨迹当前只支持 XY，不能静默丢弃 Z/M", "configuration.pointGeometryColumnName");
        if (options.bufferMode() == null)
            issues.error("REQUIRED_CONFIGURATION", "请选择缓冲距离来源", PATH + ".bufferMode");
        if (options.bufferMode() == TrackBufferMode.NONE && geometry.geometry().kind() == GeometryKind.POINT)
            issues.error("TRACK_POINT_BUFFER_REQUIRED", "点生成面轨迹时必须配置缓冲距离", PATH + ".bufferMode");
    }

    static Prepared prepare(SparkCanvasTable source, TrackReconstructConfiguration c, CanvasNodeIssueSink issues) {
        if (!c.usesAreaGeometry() || source == null) return new Prepared(source, null);
        var options = c.reconstruction().areaGeometry();
        source = TrackBufferWindowPlan.prepare(source, c, issues);
        if (issues.hasErrors()) return new Prepared(source, null);
        var geometry = CanvasNodeSupport.columns(source.schema()).get(c.pointGeometryColumnName());
        boolean buffer = options.bufferMode() != TrackBufferMode.NONE;
        double factor = 1;
        Column distance = functions.lit(0d);
        if (buffer && options.bufferMode() != null) {
            var conversion = TrackNodeSupport.distanceThreshold(1, options.bufferUnit(), c.distanceMethod(),
                    geometry.geometry(), issues, PATH + ".bufferUnit");
            factor = conversion.value();
            if (options.bufferMode() == TrackBufferMode.FIELD) {
                if (CanvasNodeSupport.blank(options.bufferField())
                        || !CanvasNodeSupport.columns(source.schema()).containsKey(options.bufferField()))
                    issues.error("COLUMN_NOT_FOUND", "请选择缓冲距离字段", PATH + ".bufferField");
                else distance = TrackNodeSupport.column(source.dataset(), options.bufferField());
            } else if (TrackSplitExpressionPolicy.findViolation(options.bufferExpression()) != null) {
                issues.error("INVALID_TRACK_BUFFER_EXPRESSION", "请输入单个受控数值表达式，不允许 SQL 语句或自定义窗口", PATH + ".bufferExpression");
            } else distance = functions.expr(options.bufferExpression());
        }
        if (issues.hasErrors()) return new Prepared(source, null);
        String footprintName = TrackNodeSupport.internalName(source.dataset(), "__datascalpel_track_footprint");
        var names = new java.util.HashSet<String>();
        for (String name : source.dataset().columns()) names.add(name.toLowerCase(java.util.Locale.ROOT));
        if (c.reconstruction().splitExpression() != null) c.reconstruction().splitExpression().bindings()
                .forEach(binding -> { if (binding != null && binding.name() != null) names.add(binding.name().toLowerCase(java.util.Locale.ROOT)); });
        while (names.contains(footprintName.toLowerCase(java.util.Locale.ROOT))) footprintName += "_";
        try {
            // Analyze only: no actions and no UDF execution. Reject aggregates, generators and subqueries.
            var analyzed = source.dataset().select(distance.alias(footprintName));
            if (!(analyzed.schema().apply(0).dataType() instanceof NumericType)) throw new IllegalArgumentException();
            var expressions = analyzed.queryExecution().analyzed().expressions().iterator();
            while (expressions.hasNext()) if (!scalar(expressions.next())) throw new IllegalArgumentException();
            if (!scalarPlan(analyzed.queryExecution().analyzed(), source.dataset().queryExecution().analyzed()))
                throw new IllegalArgumentException();
            var allowed = new java.util.HashSet<String>();
            source.schema().columns().forEach(column -> allowed.add(column.name()));
            if (options.bufferMode() == TrackBufferMode.EXPRESSION) options.windowBindings().forEach(binding -> allowed.add(binding.name()));
            var references = analyzed.queryExecution().analyzed().expressions().iterator();
            while (references.hasNext()) {
                var attributes = references.next().references().iterator();
                while (attributes.hasNext()) if (!allowed.contains(attributes.next().name())) throw new IllegalArgumentException();
            }
            // Including the original columns also prevents an aggregate-only plan from changing row membership.
            source.dataset().withColumn(footprintName, distance).schema();
        } catch (Exception exception) {
            issues.error("INVALID_TRACK_BUFFER_EXPRESSION", "缓冲距离必须是确定性的逐行数值字段或表达式，不允许聚合、展开或窗口",
                    PATH + (options.bufferMode() == TrackBufferMode.FIELD ? ".bufferField" : ".bufferExpression"));
            return new Prepared(source, null);
        }
        var geometryType = source.dataset().schema().apply(c.pointGeometryColumnName()).dataType();
        Column radius = distance.cast(DataTypes.DoubleType).multiply(factor);
        Column footprint = c.distanceMethod() == SpatialDistanceMethod.GEODESIC
                ? TrackGeodesicAreaColumns.footprint(TrackNodeSupport.column(source.dataset(), c.pointGeometryColumnName()),
                        radius, buffer, geodesicStep(c, issues), geometryType)
                : functions.udf((UDF2<Geometry, Double, Geometry>) (shape, value) ->
                        TrackAreaGeometry.footprint(shape, value, buffer), geometryType)
                .apply(TrackNodeSupport.column(source.dataset(), c.pointGeometryColumnName()),
                        radius);
        Dataset<Row> data = source.dataset().withColumn(footprintName, footprint);
        if (options.bufferMode() == TrackBufferMode.EXPRESSION) for (var binding : options.windowBindings()) data = data.drop(binding.name());
        return new Prepared(new SparkCanvasTable(source.schema(), data), footprintName);
    }

    static Column connect(Column orderedFootprints, org.apache.spark.sql.types.DataType geometryType) {
        return functions.udf((UDF1<scala.collection.Seq<Geometry>, Geometry>) values -> {
            var observations = new ArrayList<Geometry>();
            var iterator = values.iterator();
            while (iterator.hasNext()) observations.add(iterator.next());
            return TrackAreaGeometry.connect(observations);
        }, geometryType).apply(orderedFootprints);
    }

    static Column connect(Column orderedFootprints, org.apache.spark.sql.types.DataType geometryType,
            TrackReconstructConfiguration configuration, CanvasNodeIssueSink issues) {
        return configuration.distanceMethod() == SpatialDistanceMethod.GEODESIC
                ? TrackGeodesicAreaColumns.connect(orderedFootprints, geodesicStep(configuration, issues), geometryType)
                : connect(orderedFootprints, geometryType);
    }

    private static double geodesicStep(TrackReconstructConfiguration c, CanvasNodeIssueSink issues) {
        if (!c.usesAreaGeometry() || c.distanceMethod() != SpatialDistanceMethod.GEODESIC) return Double.NaN;
        var sampling = c.reconstruction().areaGeometry().geodesicBoundary();
        String path = PATH + ".geodesicBoundary";
        if (sampling == null) {
            issues.error("REQUIRED_CONFIGURATION", "请配置测地面边界采样段长及单位", path);
            return Double.NaN;
        }
        double factor = SpatialDistanceSupport.metresPerConfiguredUnit(sampling.maximumSegmentLengthUnit());
        Double length = sampling.maximumSegmentLength();
        if (!Double.isFinite(factor)) issues.error("INVALID_TRACK_GEODESIC_SEGMENT_LENGTH",
                "面边界采样须使用线性距离单位", path + ".maximumSegmentLengthUnit");
        if (length == null || !Double.isFinite(length) || length <= 0
                || Double.isFinite(factor) && (!Double.isFinite(length * factor) || length * factor <= 0))
            issues.error("INVALID_TRACK_GEODESIC_SEGMENT_LENGTH", "面边界采样段长必须为有限正数", path + ".maximumSegmentLength");
        return length == null ? Double.NaN : length * factor;
    }

    private static boolean scalar(Expression expression) {
        if (!expression.deterministic() || expression instanceof AggregateExpression || expression instanceof Generator
                || expression instanceof SubqueryExpression || expression instanceof WindowExpression) return false;
        var children = expression.children().iterator();
        while (children.hasNext()) if (!scalar(children.next())) return false;
        return true;
    }

    private static boolean scalarPlan(org.apache.spark.sql.catalyst.plans.logical.LogicalPlan plan,
            org.apache.spark.sql.catalyst.plans.logical.LogicalPlan input) {
        if (plan.equals(input)) return true;
        var expressions = plan.expressions().iterator();
        while (expressions.hasNext()) if (!scalar(expressions.next())) return false;
        var children = plan.children().iterator();
        while (children.hasNext()) if (!scalarPlan(children.next(), input)) return false;
        return true;
    }

    record Prepared(SparkCanvasTable source, String footprintColumn) { }
}
