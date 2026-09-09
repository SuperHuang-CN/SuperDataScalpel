package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.*;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.NumericType;

import java.util.*;

/** Controlled numeric observation windows. Each binding reads the original input, never another binding. */
final class TrackBufferWindowPlan {
    private static final Set<TrackSummaryStatisticKind> ALLOWED = Set.of(TrackSummaryStatisticKind.COUNT_FIELD,
            TrackSummaryStatisticKind.SUM, TrackSummaryStatisticKind.MEAN, TrackSummaryStatisticKind.MIN,
            TrackSummaryStatisticKind.MAX, TrackSummaryStatisticKind.RANGE, TrackSummaryStatisticKind.STDDEV,
            TrackSummaryStatisticKind.VARIANCE, TrackSummaryStatisticKind.FIRST, TrackSummaryStatisticKind.LAST);
    private static final String PATH = "configuration.reconstruction.areaGeometry.windowBindings";
    private TrackBufferWindowPlan() { }

    static SparkCanvasTable prepare(SparkCanvasTable source, TrackReconstructConfiguration c, CanvasNodeIssueSink issues) {
        var options = c.reconstruction().areaGeometry();
        if (options.bufferMode() != TrackBufferMode.EXPRESSION || options.windowBindings().isEmpty()) return source;
        var bindings = options.windowBindings();
        if (bindings.size() > 32) issues.error("TRACK_BUFFER_WINDOW_COUNT_EXCEEDED", "缓冲窗口绑定不能超过 32 项", PATH);
        var names = new HashSet<String>();
        for (String name : source.dataset().columns()) names.add(name.toLowerCase(Locale.ROOT));
        var columns = CanvasNodeSupport.columns(source.schema());
        for (int i = 0; i < bindings.size(); i++) {
            var b = bindings.get(i); String p = PATH + "[" + i + "]";
            if (b == null) { issues.error("INVALID_TRACK_BUFFER_WINDOW", "缓冲窗口绑定不能为空", p); continue; }
            if (b.name() == null || !b.name().matches("[A-Za-z_][A-Za-z0-9_]{0,127}")
                    || b.name().toLowerCase(Locale.ROOT).startsWith("__datascalpel_"))
                issues.error("INVALID_TRACK_BUFFER_WINDOW", "绑定名需为 1～128 位字母、数字或下划线，不能数字开头或使用平台内部前缀", p + ".name");
            else if (!names.add(b.name().toLowerCase(Locale.ROOT)))
                issues.error("DUPLICATE_COLUMN_NAME", "窗口绑定名与来源字段或其他绑定重名", p + ".name");
            if (CanvasNodeSupport.blank(b.sourceColumnName()) || !columns.containsKey(b.sourceColumnName()))
                issues.error("COLUMN_NOT_FOUND", "缓冲窗口必须使用原始来源字段，不能引用其他绑定", p + ".sourceColumnName");
            else if (!(source.dataset().schema().apply(b.sourceColumnName()).dataType() instanceof NumericType))
                issues.error("TRACK_BUFFER_WINDOW_NUMERIC_REQUIRED", "缓冲窗口需要数值来源字段", p + ".sourceColumnName");
            if (b.startOffset() == null || b.endOffset() == null || b.startOffset() < -1000 || b.startOffset() > 1000
                    || b.endOffset() < -1000 || b.endOffset() > 1000 || b.startOffset() > b.endOffset())
                issues.error("INVALID_TRACK_BUFFER_WINDOW", "起止偏移需为 -1000～1000 的整数且起点不大于终点（两端包含）", p + ".startOffset");
            if (b.statistic() == null || !ALLOWED.contains(b.statistic()))
                issues.error("INVALID_TRACK_BUFFER_WINDOW", "请选择支持的数值窗口统计", p + ".statistic");
        }
        if (issues.hasErrors()) return source;
        Dataset<Row> data = source.dataset();
        var partitions = new ArrayList<Column>();
        c.trackIdColumns().forEach(name -> partitions.add(TrackNodeSupport.column(source.dataset(), name)));
        var sorting = new ArrayList<Column>();
        sorting.add(TrackNodeSupport.column(data, c.timeColumnName()).asc());
        for (String name : c.reconstruction().orderByColumns()) sorting.add(TrackNodeSupport.column(data, name).asc_nulls_first());
        String bucket = null;
        var fixed = TrackTimeBoundarySupport.resolve(c.boundaries().fixedTimeBoundary(), issues, "configuration.boundaries.fixedTimeBoundary");
        if (fixed != null) {
            bucket = TrackNodeSupport.internalName(data, "__datascalpel_buffer_period");
            data = data.withColumn(bucket, functions.udf(fixed, DataTypes.LongType).apply(TrackNodeSupport.column(data, c.timeColumnName())));
            partitions.add(TrackNodeSupport.column(data, bucket));
        }
        var ordered = Window.partitionBy(partitions.toArray(Column[]::new)).orderBy(sorting.toArray(Column[]::new));
        var projection = new ArrayList<Column>();
        for (String name : data.columns()) projection.add(TrackNodeSupport.column(data, name));
        for (var b : bindings) {
            Column field = TrackNodeSupport.column(data, b.sourceColumnName());
            Column aggregate = switch (b.statistic()) {
                case COUNT_FIELD -> functions.count(field);
                case SUM -> functions.sum(field);
                case MEAN -> functions.avg(field);
                case MIN -> functions.min(field);
                case MAX -> functions.max(field);
                case RANGE -> functions.max(field).minus(functions.min(field));
                case STDDEV -> functions.stddev_samp(field);
                case VARIANCE -> functions.var_samp(field);
                case FIRST -> functions.first(field, false);
                case LAST -> functions.last(field, false);
                default -> throw new IllegalStateException("Validated window statistic required");
            };
            // RANGE has two window aggregates; attaching OVER to a subtraction is not valid Spark SQL.
            var frame = ordered.rowsBetween(b.startOffset(), b.endOffset());
            Column value = b.statistic() == TrackSummaryStatisticKind.RANGE
                    ? functions.max(field).over(frame).minus(functions.min(field).over(frame)) : aggregate.over(frame);
            projection.add(value.alias(b.name()));
        }
        data = data.select(projection.toArray(Column[]::new));
        if (bucket != null) data = data.drop(bucket);
        data.schema();
        return new SparkCanvasTable(source.schema(), data);
    }
}
