package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.TrackDetectIncidentsConfiguration;
import cn.superhuang.data.scalpel.contract.task.TrackIncidentSemantics;
import cn.superhuang.data.scalpel.contract.task.TrackIncidentWindow;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.functions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

/** Controlled condition bindings, evaluated together from the original prepared segment.
 * These names are local to predicates, never added to the node's output schema. */
final class IncidentWindowPlan {
    private IncidentWindowPlan() { }

    static SparkCanvasTable prepare(TrackDetectIncidentsConfiguration c, SparkCanvasTable source,
                                   TrackNodeSupport.PreparedTrack track, CanvasNodeIssueSink issues) {
        if (source == null || track == null) return source;
        if (c.effectiveIncidentSemantics() != TrackIncidentSemantics.CONDITION_LIFECYCLE || c.conditionWindows().isEmpty()) {
            return new SparkCanvasTable(source.schema(), track.dataset());
        }
        var names = new HashSet<String>();
        // Include platform temporary names so a binding cannot replace the segment or order guard.
        for (String name : track.dataset().columns()) names.add(name.toLowerCase(Locale.ROOT));
        var originalColumns = CanvasNodeSupport.columns(source.schema());
        for (int i = 0; i < c.conditionWindows().size(); i++) {
            var item = c.conditionWindows().get(i);
            String path = "configuration.conditionWindows[" + i + "]";
            if (CanvasNodeSupport.blank(item.bindingName()) || !item.bindingName().matches("[A-Za-z_][A-Za-z0-9_]{0,127}")) {
                issues.error("TRACK_INCIDENT_WINDOW_NAME_INVALID", "窗口指标名称需为 1～128 位字母、数字或下划线，不能以数字开头", path + ".bindingName");
            } else if (!names.add(item.bindingName().toLowerCase(Locale.ROOT))) {
                issues.error("DUPLICATE_COLUMN_NAME", "窗口指标名称与来源字段或其他指标冲突", path + ".bindingName");
            }
            CanvasNodeSupport.required(item.sourceColumnName(), "请选择窗口来源字段", path + ".sourceColumnName", issues);
            if (!CanvasNodeSupport.blank(item.sourceColumnName()) && !originalColumns.containsKey(item.sourceColumnName())) {
                issues.error("COLUMN_NOT_FOUND", "窗口只能引用入口表的原始字段", path + ".sourceColumnName");
            }
            if (item.kind() == null) issues.error("REQUIRED_CONFIGURATION", "请选择窗口统计函数", path + ".kind");
            if (item.startOffset() == null || item.endOffset() == null || item.startOffset() >= item.endOffset()
                    || item.startOffset() == Integer.MIN_VALUE) {
                issues.error("TRACK_INCIDENT_WINDOW_RANGE_INVALID", "窗口起点必须小于终点，使用左闭右开的有界观测偏移", path);
            }
        }
        if (issues.hasErrors()) return null;

        Dataset<Row> data = track.dataset();
        var partitions = new ArrayList<Column>();
        for (String name : c.trackIdColumns()) partitions.add(TrackNodeSupport.column(data, name));
        partitions.add(TrackNodeSupport.column(data, track.segmentColumnName()));
        var order = new ArrayList<Column>();
        order.add(TrackNodeSupport.column(data, c.timeColumnName()).asc());
        for (String name : c.orderByColumns()) order.add(TrackNodeSupport.column(data, name).asc_nulls_first());
        var base = Window.partitionBy(partitions.toArray(Column[]::new)).orderBy(order.toArray(Column[]::new));
        var projection = new ArrayList<Column>();
        for (String name : data.columns()) projection.add(TrackNodeSupport.column(data, name));
        for (var item : c.conditionWindows()) {
            var frame = base.rowsBetween(item.startOffset().longValue(), item.endOffset().longValue() - 1);
            projection.add(aggregate(item, TrackNodeSupport.column(data, item.sourceColumnName()))
                    .over(frame).alias(item.bindingName()));
        }
        var result = data.select(projection.toArray(Column[]::new));
        // Let the Analyzer determine executable types; no parallel platform-type allow-list.
        var visible = new ArrayList<Column>();
        source.schema().columns().forEach(column -> visible.add(TrackNodeSupport.column(result, column.name())));
        c.conditionWindows().forEach(item -> visible.add(TrackNodeSupport.column(result, item.bindingName())));
        var fallbacks = new ArrayList<>(source.schema().columns());
        for (var item : c.conditionWindows()) {
            var original = originalColumns.get(item.sourceColumnName());
            fallbacks.add(new CanvasColumnSchema(item.bindingName(), original.fieldType(), original.length(), original.precision(),
                    original.scale(), true, null, false, false, null, original.geometry()));
        }
        var columns = SparkTypeMapper.fromStructType(result.select(visible.toArray(Column[]::new)).schema(), fallbacks);
        return new SparkCanvasTable(new CanvasTableSchema(source.schema().name(), source.schema().origin(), columns,
                source.schema().datasetKind(), source.schema().eventTimeColumn(), source.schema().watermarkDelay()), result);
    }

    private static Column aggregate(TrackIncidentWindow item, Column source) {
        return switch (item.kind()) {
            case COUNT -> functions.count(source);
            case SUM -> functions.sum(source);
            case MEAN -> functions.avg(source);
            case MIN -> functions.min(source);
            case MAX -> functions.max(source);
            case FIRST -> functions.first(source, false);
            case LAST -> functions.last(source, false);
            case STDDEV_POP -> functions.stddev_pop(source);
            case VARIANCE_POP -> functions.var_pop(source);
        };
    }
}
