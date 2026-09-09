package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.*;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.apache.spark.sql.types.DataTypes;

import java.util.*;

/** Ordered segmentation and explicit split-boundary observation ownership; no Spark actions. */
final class TrackReconstructPlan {
    private TrackReconstructPlan() { }

    static SparkCanvasTable validObservations(SparkCanvasTable source, TrackReconstructConfiguration c) {
        if (source == null || !c.usesOrderedReconstruction()) return source;
        var columns = CanvasNodeSupport.columns(source.schema());
        var geometry = CanvasNodeSupport.blank(c.pointGeometryColumnName()) ? null : columns.get(c.pointGeometryColumnName());
        var time = CanvasNodeSupport.blank(c.timeColumnName()) ? null : columns.get(c.timeColumnName());
        if (geometry == null || geometry.fieldType() != PlatformDataType.GEOMETRY
                || time == null || time.fieldType() != PlatformDataType.TIMESTAMP) return source;
        Dataset<Row> data = source.dataset();
        Column shape = col(c.pointGeometryColumnName());
        Column checked = c.usesAreaGeometry() && c.distanceMethod() == SpatialDistanceMethod.GEODESIC
                ? TrackGeodesicAreaColumns.validObservation(shape, data.schema().apply(c.pointGeometryColumnName()).dataType())
                : functions.when(shape.isNull().or(st_functions.ST_IsEmpty(shape)), shape)
                .when(st_functions.ST_IsValid(shape), shape)
                .otherwise(functions.raise_error(functions.lit("TRACK_RECONSTRUCT_GEOMETRY_INVALID")));
        data = data.withColumn(c.pointGeometryColumnName(), checked)
                .filter(col(c.timeColumnName()).isNotNull().and(col(c.pointGeometryColumnName()).isNotNull())
                        .and(functions.not(st_functions.ST_IsEmpty(col(c.pointGeometryColumnName())))));
        return new SparkCanvasTable(source.schema(), data);
    }

    static void validate(TrackReconstructConfiguration c, SparkCanvasTable source, CanvasNodeIssueSink issues) {
        if (!c.usesOrderedReconstruction() || source == null) return;
        var rule = c.reconstruction().splitExpression();
        if (rule == null || !rule.active()) return;
        String path = "configuration.reconstruction.splitExpression";
        if (TrackSplitExpressionPolicy.findViolation(rule.expression()) != null) {
            issues.error("INVALID_TRACK_SPLIT_EXPRESSION", "请输入单个受控布尔表达式；不允许 SQL 语句或自定义窗口", path + ".expression");
        }
        if (rule.bindings().size() > 32) {
            issues.error("TRACK_SPLIT_BINDING_COUNT_EXCEEDED", "观测窗口绑定不能超过 32 项", path + ".bindings");
        }
        var sourceColumns = CanvasNodeSupport.columns(source.schema());
        Set<String> occupied = new HashSet<>();
        sourceColumns.keySet().forEach(name -> occupied.add(name.toLowerCase(Locale.ROOT)));
        for (int i = 0; i < rule.bindings().size(); i++) {
            var binding = rule.bindings().get(i);
            String itemPath = path + ".bindings[" + i + "]";
            if (binding == null) {
                issues.error("INVALID_TRACK_SPLIT_BINDING", "窗口绑定不能为空", itemPath);
                continue;
            }
            if (binding.name() == null || !binding.name().matches("[A-Za-z_][A-Za-z0-9_]{0,127}")) {
                issues.error("INVALID_TRACK_SPLIT_BINDING", "绑定名必须是 1～128 位字母、数字或下划线，不能数字开头", itemPath + ".name");
            } else if (!occupied.add(binding.name().toLowerCase(Locale.ROOT))) {
                issues.error("DUPLICATE_COLUMN_NAME", "窗口绑定名与来源字段或其他绑定重名", itemPath + ".name");
            }
            if (CanvasNodeSupport.blank(binding.sourceColumnName()) || !sourceColumns.containsKey(binding.sourceColumnName())) {
                issues.error("COLUMN_NOT_FOUND", "窗口绑定的来源字段不存在", itemPath + ".sourceColumnName");
            }
            if (binding.offset() == null || binding.offset() < -1000 || binding.offset() > 1000) {
                issues.error("INVALID_TRACK_SPLIT_BINDING", "观测偏移必须为 -1000～1000 的整数", itemPath + ".offset");
            }
        }
    }

    static TrackNodeSupport.PreparedTrack segment(TrackNodeSupport.PreparedTrack prepared,
            TrackReconstructConfiguration c, CanvasNodeIssueSink issues) {
        Dataset<Row> data = prepared.dataset();
        boolean activeExpression = c.reconstruction().splitExpression() != null && c.reconstruction().splitExpression().active();
        Set<String> names = new HashSet<>();
        for (String name : data.columns()) names.add(name.toLowerCase(Locale.ROOT));
        if (activeExpression) c.reconstruction().splitExpression().bindings()
                .forEach(binding -> names.add(binding.name().toLowerCase(Locale.ROOT)));
        String bucket = name(names, "bucket");
        String split = name(names, "split");
        String fixedSplit = name(names, "fixed_split");
        String resultSegment = name(names, "segment");
        String neighbor = name(names, "neighbor");
        String baseSegment = prepared.segmentColumnName();
        if (activeExpression && c.reconstruction().splitExpression().bindings().stream()
                .anyMatch(binding -> binding.name().equalsIgnoreCase(prepared.segmentColumnName()))) {
            baseSegment = name(names, "base_segment");
            data = data.withColumnRenamed(prepared.segmentColumnName(), baseSegment);
        }
        var fixed = TrackTimeBoundarySupport.resolve(c.boundaries().fixedTimeBoundary(), issues,
                "configuration.boundaries.fixedTimeBoundary");
        if (fixed != null) data = data.withColumn(bucket, functions.udf(fixed, DataTypes.LongType).apply(col(c.timeColumnName())));
        List<Column> ids = c.trackIdColumns().stream().map(TrackReconstructPlan::col).toList();
        var sorting = new ArrayList<Column>();
        sorting.add(col(c.timeColumnName()).asc());
        c.reconstruction().orderByColumns().forEach(field -> sorting.add(col(field).asc_nulls_first()));
        WindowSpec ordered = Window.partitionBy(ids.toArray(Column[]::new)).orderBy(sorting.toArray(Column[]::new));
        List<Column> bindingPartition = new ArrayList<>(ids);
        if (fixed != null) bindingPartition.add(col(bucket));
        WindowSpec bindingWindow = Window.partitionBy(bindingPartition.toArray(Column[]::new)).orderBy(sorting.toArray(Column[]::new));
        var rule = c.reconstruction().splitExpression();
        if (rule != null && rule.active()) {
            for (var binding : rule.bindings()) {
                Column source = col(binding.sourceColumnName());
                Column value = binding.offset() < 0 ? functions.lag(source, -binding.offset()).over(bindingWindow)
                        : binding.offset() > 0 ? functions.lead(source, binding.offset()).over(bindingWindow) : source;
                data = data.withColumn(binding.name(), value);
            }
            try {
                var expression = functions.expr(rule.expression());
                var projection = data.select(expression.alias(split));
                var type = projection.schema().apply(split).dataType();
                var expressions = projection.queryExecution().analyzed().expressions().iterator();
                boolean deterministic = true;
                while (expressions.hasNext()) deterministic &= expressions.next().deterministic();
                if ((!type.equals(DataTypes.BooleanType) && !type.equals(DataTypes.NullType)) || !deterministic)
                    throw new IllegalArgumentException("Boolean deterministic split required");
                data = data.withColumn(split, functions.coalesce(expression.cast(DataTypes.BooleanType), functions.lit(false)));
            } catch (Exception exception) {
                issues.error("INVALID_TRACK_SPLIT_EXPRESSION", "表达式必须可解析为确定性的布尔条件；请检查字段及窗口绑定", "configuration.reconstruction.splitExpression.expression");
                return null;
            }
        } else data = data.withColumn(split, functions.lit(false));
        Column previous = functions.lag(col(baseSegment), 1).over(ordered);
        Column periodChanged = fixed == null ? functions.lit(false)
                : col(bucket).notEqual(functions.lag(col(bucket), 1).over(ordered));
        data = data.withColumn(fixedSplit, functions.coalesce(periodChanged, functions.lit(false)));
        Column breaks = previous.isNull().or(previous.notEqual(col(baseSegment))).or(col(split));
        data = data.withColumn(split, functions.when(breaks, 1).otherwise(0));
        data = data.withColumn(resultSegment, functions.sum(col(split))
                .over(ordered.rowsBetween(Window.unboundedPreceding(), Window.currentRow())));

        TrackSplitBoundaryOption option = c.reconstruction().effectiveBoundaryOption();
        if (option != TrackSplitBoundaryOption.GAP) {
            Column neighborSegment;
            Column bridgeAllowed;
            if (option == TrackSplitBoundaryOption.FINISH_LAST) {
                neighborSegment = functions.lag(col(resultSegment), 1).over(ordered);
                bridgeAllowed = functions.not(col(fixedSplit));
            } else {
                neighborSegment = functions.lead(col(resultSegment), 1).over(ordered);
                bridgeAllowed = functions.not(functions.coalesce(functions.lead(col(fixedSplit), 1).over(ordered), functions.lit(true)));
            }
            data = data.withColumn(neighbor, functions.when(bridgeAllowed, neighborSegment));
            Dataset<Row> bridge = data.filter(col(neighbor).isNotNull().and(col(neighbor).notEqual(col(resultSegment))))
                    .withColumn(resultSegment, col(neighbor));
            data = data.unionByName(bridge);
        }
        return new TrackNodeSupport.PreparedTrack(prepared.source(), data, prepared.trackIdSchemas(),
                prepared.timeSchema(), prepared.pointSchema(), resultSegment, ordered);
    }

    static Column order(TrackReconstructConfiguration c, Dataset<Row> data) {
        var fields = new ArrayList<Column>();
        fields.add(TrackNodeSupport.column(data, c.timeColumnName()));
        c.reconstruction().orderByColumns().forEach(field -> fields.add(TrackNodeSupport.column(data, field)));
        return functions.struct(fields.toArray(Column[]::new));
    }

    private static String name(Set<String> names, String suffix) {
        String name = "__datascalpel_reconstruct_" + suffix;
        while (!names.add(name.toLowerCase(Locale.ROOT))) name += "_";
        return name;
    }

    private static Column col(String name) { return functions.col(CanvasNodeSupport.quoteIdentifier(name)); }
}
