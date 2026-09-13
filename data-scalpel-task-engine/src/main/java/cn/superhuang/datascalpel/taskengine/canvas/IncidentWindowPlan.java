package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.TrackDetectIncidentsConfiguration;
import cn.superhuang.data.scalpel.contract.task.TrackIncidentSemantics;
import cn.superhuang.data.scalpel.contract.task.TrackIncidentScalar;
import cn.superhuang.data.scalpel.contract.task.TrackIncidentWindow;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;

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
        if (c.effectiveIncidentSemantics() != TrackIncidentSemantics.CONDITION_LIFECYCLE
                || c.conditionWindows().isEmpty() && c.conditionScalars().isEmpty()) {
            return new SparkCanvasTable(source.schema(), track.dataset());
        }
        var names = new HashSet<String>();
        boolean trackDistanceRequired = false;
        boolean trackSpeedRequired = false;
        boolean trackAccelerationRequired = false;
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
            switch (item.effectiveSource()) {
                case FIELD -> {
                    CanvasNodeSupport.required(item.sourceColumnName(), "请选择窗口来源字段", path + ".sourceColumnName", issues);
                    if (!CanvasNodeSupport.blank(item.sourceColumnName()) && !originalColumns.containsKey(item.sourceColumnName())) {
                        issues.error("COLUMN_NOT_FOUND", "窗口只能引用入口表的原始字段", path + ".sourceColumnName");
                    }
                }
                case TRACK_DISTANCE -> trackDistanceRequired = true;
                case TRACK_SPEED -> trackSpeedRequired = true;
                case TRACK_ACCELERATION -> trackAccelerationRequired = true;
            }
            if (item.kind() == null) issues.error("REQUIRED_CONFIGURATION", "请选择窗口统计函数", path + ".kind");
            if (item.startOffset() == null || item.endOffset() == null || item.startOffset() >= item.endOffset()
                    || item.startOffset() == Integer.MIN_VALUE) {
                issues.error("TRACK_INCIDENT_WINDOW_RANGE_INVALID", "窗口起点必须小于终点，使用左闭右开的有界观测偏移", path);
            }
        }
        for (int i = 0; i < c.conditionScalars().size(); i++) {
            var item = c.conditionScalars().get(i);
            String path = "configuration.conditionScalars[" + i + "]";
            if (CanvasNodeSupport.blank(item.bindingName()) || !item.bindingName().matches("[A-Za-z_][A-Za-z0-9_]{0,127}")) {
                issues.error("TRACK_INCIDENT_SCALAR_NAME_INVALID",
                        "轨迹标量名称需为 1～128 位字母、数字或下划线，不能以数字开头",
                        path + ".bindingName");
            } else if (!names.add(item.bindingName().toLowerCase(Locale.ROOT))) {
                issues.error("DUPLICATE_COLUMN_NAME", "轨迹标量名称与来源字段、窗口指标或其他标量冲突",
                        path + ".bindingName");
            }
            if (item.source() == null) {
                issues.error("REQUIRED_CONFIGURATION", "请选择轨迹标量来源", path + ".source");
            } else if (item.isPointCoordinate()) {
                if (item.offset() == null) {
                    issues.error("TRACK_INCIDENT_POINT_COORDINATE_OFFSET_REQUIRED",
                            "Point 坐标标量必须填写相对观测偏移", path + ".offset");
                }
                if (CanvasNodeSupport.blank(c.pointGeometryColumnName()) || track.pointSchema() == null
                        || track.pointSchema().geometry() == null
                        || track.pointSchema().geometry().kind() != GeometryKind.POINT) {
                    issues.error("TRACK_INCIDENT_POINT_COORDINATE_REQUIRES_GEOMETRY",
                            "Point 坐标标量必须选择带完整元数据的 Point Geometry",
                            "configuration.pointGeometryColumnName");
                }
            }
        }
        if (trackDistanceRequired) validateTrackDistanceSource(c, track, issues);
        if (trackSpeedRequired) validateTrackSpeedSource(c, track, issues);
        if (trackAccelerationRequired) validateTrackAccelerationSource(c, track, issues);
        if (issues.hasErrors()) return null;

        Dataset<Row> data = track.dataset();
        var partitions = new ArrayList<Column>();
        for (String name : c.trackIdColumns()) partitions.add(TrackNodeSupport.column(data, name));
        partitions.add(TrackNodeSupport.column(data, track.segmentColumnName()));
        var order = new ArrayList<Column>();
        order.add(TrackNodeSupport.column(data, c.timeColumnName()).asc());
        for (String name : c.orderByColumns()) order.add(TrackNodeSupport.column(data, name).asc_nulls_first());
        var base = Window.partitionBy(partitions.toArray(Column[]::new)).orderBy(order.toArray(Column[]::new));
        String distanceName = null;
        String speedName = null;
        String accelerationName = null;
        if (trackDistanceRequired || trackSpeedRequired || trackAccelerationRequired) {
            String segmentDistanceName = TrackNodeSupport.internalName(data,
                    "__datascalpel_incident_track_segment_distance_metres");
            Column point = TrackNodeSupport.column(data, c.pointGeometryColumnName());
            Column previous = functions.lag(point, 1).over(base);
            data = data.withColumn(segmentDistanceName,
                    TrackNodeSupport.distance(point, previous, SpatialDistanceMethod.GEODESIC));
            Column observation = functions.row_number().over(base);
            if (trackDistanceRequired) {
                distanceName = TrackNodeSupport.internalName(data, "__datascalpel_incident_track_distance_metres");
                var cumulative = base.rowsBetween(Window.unboundedPreceding(), Window.currentRow());
                Column missingLink = functions.when(observation.equalTo(1), functions.lit(0L))
                        .otherwise(functions.when(point.isNull().or(previous.isNull()), functions.lit(1L))
                                .otherwise(functions.lit(0L)));
                Column missingLinkCount = functions.sum(missingLink).over(cumulative);
                Column cumulativeDistance = functions.sum(functions.coalesce(
                        TrackNodeSupport.column(data, segmentDistanceName), functions.lit(0d))).over(cumulative);
                data = data.withColumn(distanceName,
                        functions.when(point.isNull().or(missingLinkCount.gt(0L)), functions.lit(null).cast("double"))
                                .otherwise(cumulativeDistance));
            }
            Column elapsedSeconds = null;
            if (trackSpeedRequired || trackAccelerationRequired) {
                speedName = TrackNodeSupport.internalName(data, "__datascalpel_incident_track_speed_metres_per_second");
                Column time = TrackNodeSupport.column(data, c.timeColumnName());
                Column previousTime = functions.lag(time, 1).over(base);
                elapsedSeconds = functions.unix_micros(time).minus(functions.unix_micros(previousTime))
                        .divide(functions.lit(1_000_000d));
                Column speed = functions.when(point.isNull(), functions.lit(null).cast("double"))
                        .when(observation.equalTo(1), functions.lit(0d))
                        .when(previous.isNull().or(elapsedSeconds.isNull()).or(elapsedSeconds.leq(0d)),
                                functions.lit(null).cast("double"))
                        .otherwise(TrackNodeSupport.column(data, segmentDistanceName).divide(elapsedSeconds));
                data = data.withColumn(speedName, speed);
            }
            if (trackAccelerationRequired) {
                accelerationName = TrackNodeSupport.internalName(data,
                        "__datascalpel_incident_track_acceleration_metres_per_second_squared");
                Column speed = TrackNodeSupport.column(data, speedName);
                Column previousSpeed = functions.lag(speed, 1).over(base);
                Column acceleration = functions.when(point.isNull(), functions.lit(null).cast("double"))
                        .when(observation.equalTo(1), functions.lit(0d))
                        .when(speed.isNull().or(previousSpeed.isNull()).or(elapsedSeconds.isNull())
                                        .or(elapsedSeconds.leq(0d)),
                                functions.lit(null).cast("double"))
                        .otherwise(speed.minus(previousSpeed).divide(elapsedSeconds));
                data = data.withColumn(accelerationName, acceleration);
            }
        }
        var projection = new ArrayList<Column>();
        for (String name : data.columns()) projection.add(TrackNodeSupport.column(data, name));
        for (var item : c.conditionWindows()) {
            var frame = base.rowsBetween(item.startOffset().longValue(), item.endOffset().longValue() - 1L);
            Column binding = aggregate(item, switch (item.effectiveSource()) {
                case FIELD -> TrackNodeSupport.column(data, item.sourceColumnName());
                // ArcGIS TrackDistance/Speed/AccelerationWindow return observation-aligned
                // values in [start,end), not individual geometry segments.
                case TRACK_DISTANCE -> TrackNodeSupport.column(data, distanceName);
                case TRACK_SPEED -> TrackNodeSupport.column(data, speedName);
                case TRACK_ACCELERATION -> TrackNodeSupport.column(data, accelerationName);
            }).over(frame);
            projection.add(binding.alias(item.bindingName()));
        }
        if (!c.conditionScalars().isEmpty()) {
            Column currentEpochMillis = functions.floor(functions.unix_micros(
                    TrackNodeSupport.column(data, c.timeColumnName())).divide(functions.lit(1_000d))).cast("long");
            var cumulative = base.rowsBetween(Window.unboundedPreceding(), Window.currentRow());
            Column startEpochMillis = functions.min(currentEpochMillis).over(cumulative);
            Column trackIndex = functions.row_number().over(base).minus(1L).cast("long");
            Column point = CanvasNodeSupport.blank(c.pointGeometryColumnName())
                    ? null : TrackNodeSupport.column(data, c.pointGeometryColumnName());
            for (var item : c.conditionScalars()) {
                Column scalar = switch (item.source()) {
                    case TRACK_START_TIME -> startEpochMillis;
                    case TRACK_DURATION -> currentEpochMillis.minus(startEpochMillis).cast("long");
                    case TRACK_CURRENT_TIME -> currentEpochMillis;
                    case TRACK_INDEX -> trackIndex;
                    case TRACK_POINT_X_AT -> functions.first(st_functions.ST_X(point), false)
                            .over(base.rowsBetween(item.offset().longValue(), item.offset().longValue()));
                    case TRACK_POINT_Y_AT -> functions.first(st_functions.ST_Y(point), false)
                            .over(base.rowsBetween(item.offset().longValue(), item.offset().longValue()));
                };
                projection.add(scalar.alias(item.bindingName()));
            }
        }
        var result = data.select(projection.toArray(Column[]::new));
        // Let the Analyzer determine executable types; no parallel platform-type allow-list.
        var visible = new ArrayList<Column>();
        source.schema().columns().forEach(column -> visible.add(TrackNodeSupport.column(result, column.name())));
        c.conditionWindows().forEach(item -> visible.add(TrackNodeSupport.column(result, item.bindingName())));
        c.conditionScalars().forEach(item -> visible.add(TrackNodeSupport.column(result, item.bindingName())));
        var fallbacks = new ArrayList<>(source.schema().columns());
        for (var item : c.conditionWindows()) {
            if (item.effectiveSource() != TrackIncidentWindow.Source.FIELD) {
                fallbacks.add(item.kind() == TrackIncidentWindow.Kind.COUNT
                        ? TrackNodeSupport.longColumn(item.bindingName(), true)
                        : TrackNodeSupport.doubleColumn(item.bindingName(), true));
            } else {
                var original = originalColumns.get(item.sourceColumnName());
                fallbacks.add(new CanvasColumnSchema(item.bindingName(), original.fieldType(), original.length(), original.precision(),
                        original.scale(), true, null, false, false, null, original.geometry()));
            }
        }
        for (TrackIncidentScalar item : c.conditionScalars()) {
            fallbacks.add(item.isPointCoordinate()
                    ? TrackNodeSupport.doubleColumn(item.bindingName(), true)
                    : TrackNodeSupport.longColumn(item.bindingName(), false));
        }
        var columns = SparkTypeMapper.fromStructType(result.select(visible.toArray(Column[]::new)).schema(), fallbacks);
        return new SparkCanvasTable(new CanvasTableSchema(source.schema().name(), source.schema().origin(), columns,
                source.schema().datasetKind(), source.schema().eventTimeColumn(), source.schema().watermarkDelay()), result);
    }

    private static void validateTrackDistanceSource(
            TrackDetectIncidentsConfiguration c,
            TrackNodeSupport.PreparedTrack track,
            CanvasNodeIssueSink issues
    ) {
        validateTrackPointSource(c, track, issues, "TRACK_INCIDENT_DISTANCE_WINDOW_REQUIRES_GEOMETRY",
                "轨迹距离窗口", "距离单位固定为米");
    }

    private static void validateTrackSpeedSource(
            TrackDetectIncidentsConfiguration c,
            TrackNodeSupport.PreparedTrack track,
            CanvasNodeIssueSink issues
    ) {
        validateTrackPointSource(c, track, issues, "TRACK_INCIDENT_SPEED_WINDOW_REQUIRES_GEOMETRY",
                "轨迹速度窗口", "速度单位固定为米/秒");
    }

    private static void validateTrackAccelerationSource(
            TrackDetectIncidentsConfiguration c,
            TrackNodeSupport.PreparedTrack track,
            CanvasNodeIssueSink issues
    ) {
        validateTrackPointSource(c, track, issues, "TRACK_INCIDENT_ACCELERATION_WINDOW_REQUIRES_GEOMETRY",
                "轨迹加速度窗口", "加速度单位固定为米/秒²");
    }

    private static void validateTrackPointSource(
            TrackDetectIncidentsConfiguration c,
            TrackNodeSupport.PreparedTrack track,
            CanvasNodeIssueSink issues,
            String missingCode,
            String label,
            String unitDescription
    ) {
        if (CanvasNodeSupport.blank(c.pointGeometryColumnName()) || track.pointSchema() == null) {
            issues.error(missingCode,
                    label + "必须选择 Point Geometry 字段",
                    "configuration.pointGeometryColumnName");
            return;
        }
        var geometry = track.pointSchema().geometry();
        if (geometry == null || geometry.kind() != GeometryKind.POINT) {
            issues.error("TRACK_POINT_GEOMETRY_REQUIRED", label + "需要 Point Geometry",
                    "configuration.pointGeometryColumnName");
            return;
        }
        if (!"EPSG".equalsIgnoreCase(geometry.crs().authority()) || geometry.crs().code() != 4326
                || geometry.dimension() != CoordinateDimension.XY) {
            issues.error("GEODESIC_REQUIRES_WGS84_XY", label + "只支持 EPSG:4326 XY，" + unitDescription,
                    "configuration.pointGeometryColumnName");
        }
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
