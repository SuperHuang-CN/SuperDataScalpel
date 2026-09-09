package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import net.sf.geographiclib.Geodesic;
import org.apache.spark.sql.*;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.api.java.UDF2;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.apache.spark.sql.types.DataTypes;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import java.util.*;

/** Lazy per-observation measurements and fully contained history-window summaries. */
final class MotionWindowPlan {
    private MotionWindowPlan() { }
    private enum Value { POINT, PREVIOUS_POINT, SECONDS, DISTANCE, SPEED, ACCELERATION, ELEVATION, CHANGE, SLOPE, IDLE, BEARING }

    static CanvasNodeOperationResult apply(TrackMotionStatisticsConfiguration c, Map<String, SparkCanvasTable> inputs,
                                           CanvasNodeOperationContext context) {
        var issues = context.issues();
        var inputSchemas = CanvasNodeSupport.schemas(inputs);
        CanvasNodeSupport.required(c.sourceTableName(), "请选择来源表", "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(c.timeColumnName(), "请选择时间字段", "configuration.timeColumnName", issues);
        CanvasNodeSupport.required(c.outputTableName(), "请输入输出表名", "configuration.outputTableName", issues);
        if (!CanvasNodeSupport.blank(c.outputTableName()) && inputs.containsKey(c.outputTableName()))
            issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在", "configuration.outputTableName");
        var source = CanvasNodeSupport.blank(c.sourceTableName()) ? null : inputs.get(c.sourceTableName());
        if (source == null && !CanvasNodeSupport.blank(c.sourceTableName())) issues.error("TABLE_NOT_FOUND", "来源表不存在", "configuration.sourceTableName");
        var options = c.windowOptions();
        if (options == null) {
            issues.error("REQUIRED_CONFIGURATION", "请配置历史窗口", "configuration.windowOptions");
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        var prepared = TrackNodeSupport.prepare(source, c.pointGeometryColumnName(), true, c.trackIdColumns(),
                c.timeColumnName(), c.distanceMethod(), c.boundaries(), issues, "configuration",
                options.orderByColumns(), "configuration.windowOptions.orderByColumns");
        Set<TrackMotionStatisticGroup> groups = validate(c, source, prepared, issues);
        if (issues.hasErrors() || prepared == null) return CanvasNodeOperationResult.invalid(inputSchemas);
        boolean vertical = groups.contains(TrackMotionStatisticGroup.ELEVATION) || groups.contains(TrackMotionStatisticGroup.SLOPE);
        boolean idle = groups.contains(TrackMotionStatisticGroup.IDLE);
        boolean geodesic = c.distanceMethod() == SpatialDistanceMethod.GEODESIC;
        double rawToMetres = 1d;
        if (!geodesic) {
            var conversion = SpatialDistanceSupport.resolve(1, SpatialDistanceUnit.METERS, prepared.pointSchema().geometry().crs());
            if (conversion.valid() && !conversion.angular()) rawToMetres = 1d / conversion.sourceCrsValue();
            else if (groups.contains(TrackMotionStatisticGroup.SPEED) || groups.contains(TrackMotionStatisticGroup.ACCELERATION)
                    || groups.contains(TrackMotionStatisticGroup.SLOPE)
                    || groups.contains(TrackMotionStatisticGroup.DISTANCE) && options.distanceUnit() != SpatialDistanceUnit.SOURCE_CRS_UNIT
                    || idle && c.idleDistanceThresholdUnit() != SpatialDistanceUnit.SOURCE_CRS_UNIT) {
                issues.error("TRACK_LINEAR_UNIT_REQUIRED", "这些指标需要可换算为米的处理坐标系", "configuration.distanceMethod");
            }
        }
        double distanceUnit = options.distanceUnit() == SpatialDistanceUnit.SOURCE_CRS_UNIT ? rawToMetres
                : unitMetres(options.distanceUnit());
        if (geodesic && groups.contains(TrackMotionStatisticGroup.DISTANCE) && options.distanceUnit() == SpatialDistanceUnit.SOURCE_CRS_UNIT)
            issues.error("INVALID_TRACK_METRIC_UNIT", "测地距离需要明确线性单位", "configuration.windowOptions.distanceUnit");
        double idleMetres = 0, idleSeconds = 0;
        if (idle) {
            idleMetres = TrackNodeSupport.distanceThreshold(c.idleDistanceThreshold(), c.idleDistanceThresholdUnit(),
                    c.distanceMethod(), prepared.pointSchema().geometry(), issues, "configuration.idleDistanceThreshold").value() * rawToMetres;
            idleSeconds = TrackNodeSupport.durationMillis(options.idleTimeThreshold(), options.idleTimeThresholdUnit()) / 1000d;
            if (!Double.isFinite(idleSeconds) || !Double.isFinite(idleMetres))
                issues.error("INVALID_IDLE_TIME_THRESHOLD", "静止阈值换算超出范围", "configuration.windowOptions.idleTimeThreshold");
        }
        if (issues.hasErrors()) return CanvasNodeOperationResult.invalid(inputSchemas);
        Dataset<Row> data = prepared.dataset();
        Set<String> occupied = new HashSet<>();
        for (String name : data.columns()) occupied.add(name.toLowerCase(Locale.ROOT));
        options.statistics().forEach(s -> occupied.add(s.outputColumnName().toLowerCase(Locale.ROOT)));
        Map<Value, String> names = new EnumMap<>(Value.class);
        for (Value value : Value.values()) {
            String name = "__datascalpel_motion_window_" + value.name().toLowerCase(Locale.ROOT);
            while (!occupied.add(name.toLowerCase(Locale.ROOT))) name += "_";
            names.put(value, name);
        }
        List<String> keys = new ArrayList<>(c.trackIdColumns()); keys.add(prepared.segmentColumnName());
        List<String> order = new ArrayList<>(List.of(c.timeColumnName())); order.addAll(options.orderByColumns());
        WindowSpec sorted = Window.partitionBy(columns(keys)).orderBy(columns(order));
        data = data.withColumn(names.get(Value.POINT), functions.udf((UDF1<Geometry, Geometry>) p -> validPoint(p, geodesic),
                        data.schema().apply(c.pointGeometryColumnName()).dataType()).apply(col(c.pointGeometryColumnName())));
        data = data.withColumn(names.get(Value.PREVIOUS_POINT), functions.lag(v(names, Value.POINT), 1).over(sorted))
                .withColumn(names.get(Value.SECONDS), functions.unix_micros(col(c.timeColumnName()))
                        .minus(functions.unix_micros(functions.lag(col(c.timeColumnName()), 1).over(sorted))).divide(1_000_000d));
        data = data.withColumn(names.get(Value.DISTANCE),
                TrackNodeSupport.distance(v(names, Value.POINT), v(names, Value.PREVIOUS_POINT), c.distanceMethod()).multiply(rawToMetres));
        data = data.withColumn(names.get(Value.SPEED), functions.when(v(names, Value.SECONDS).gt(0),
                functions.try_divide(v(names, Value.DISTANCE), v(names, Value.SECONDS))));
        data = data.withColumn(names.get(Value.ACCELERATION), functions.when(v(names, Value.SECONDS).gt(0),
                functions.try_divide(v(names, Value.SPEED).minus(functions.lag(v(names, Value.SPEED), 1).over(sorted)), v(names, Value.SECONDS))));
        if (vertical) {
            Column elevation = CanvasNodeSupport.blank(options.elevationColumnName())
                    ? st_functions.ST_Z(v(names, Value.POINT)) : col(options.elevationColumnName()).cast(DataTypes.DoubleType);
            elevation = functions.when(functions.not(functions.isnan(elevation)).and(functions.abs(elevation).leq(Double.MAX_VALUE)), elevation);
            data = data.withColumn(names.get(Value.ELEVATION), elevation.multiply(unitMetres(options.inputElevationUnit())));
            data = data.withColumn(names.get(Value.CHANGE), v(names, Value.ELEVATION).minus(functions.lag(v(names, Value.ELEVATION), 1).over(sorted)));
            data = data.withColumn(names.get(Value.SLOPE), functions.when(v(names, Value.DISTANCE).gt(0),
                    functions.try_divide(v(names, Value.CHANGE), v(names, Value.DISTANCE))));
        }
        if (idle) data = data.withColumn(names.get(Value.IDLE), functions.when(v(names, Value.DISTANCE).isNotNull()
                        .and(v(names, Value.SECONDS).gt(0)), v(names, Value.DISTANCE).lt(idleMetres).and(v(names, Value.SECONDS).gt(idleSeconds))));
        if (groups.contains(TrackMotionStatisticGroup.BEARING)) data = data.withColumn(names.get(Value.BEARING),
                functions.udf((UDF2<Geometry, Geometry, Double>) (previous, current) -> bearing(previous, current, geodesic), DataTypes.DoubleType)
                        .apply(v(names, Value.PREVIOUS_POINT), v(names, Value.POINT)));

        int n = options.observationCount();
        WindowSpec points = sorted.rowsBetween(-(n - 1L), 0);
        WindowSpec segments = sorted.rowsBetween(-Math.max(0, n - 2L), 0);
        WindowSpec accelerations = sorted.rowsBetween(-Math.max(0, n - 3L), 0);
        List<Column> projection = new ArrayList<>();
        source.schema().columns().forEach(field -> projection.add(col(field.name())));
        List<CanvasColumnSchema> outputColumns = new ArrayList<>(source.schema().columns());
        for (var statistic : options.statistics()) {
            Column expression = expression(statistic.kind(), names, points, segments, accelerations, n,
                    distanceUnit, options);
            projection.add(expression.alias(statistic.outputColumnName()));
            outputColumns.add(statistic.kind() == TrackMotionStatistic.IDLING
                    ? TrackNodeSupport.booleanColumn(statistic.outputColumnName(), true)
                    : TrackNodeSupport.doubleColumn(statistic.outputColumnName(), true));
        }
        var schema = new CanvasTableSchema(c.outputTableName(), null, outputColumns, CanvasDatasetKind.BOUNDED,
                source.schema().eventTimeColumn(), null);
        var output = new LinkedHashMap<>(inputs);
        output.put(schema.name(), new SparkCanvasTable(schema, data.select(projection.toArray(Column[]::new))));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static Column expression(TrackMotionStatistic kind, Map<Value, String> names, WindowSpec points,
            WindowSpec segments, WindowSpec accelerationWindow, int n, double distanceUnit, TrackMotionWindowOptions o) {
        Column d = v(names, Value.DISTANCE), t = v(names, Value.SECONDS), s = v(names, Value.SPEED);
        Column a = v(names, Value.ACCELERATION), z = v(names, Value.ELEVATION), dz = v(names, Value.CHANGE);
        Column slope = v(names, Value.SLOPE), idle = v(names, Value.IDLE);
        Column value = switch (kind) {
            case DISTANCE -> d;
            case TOT_DISTANCE -> aggregate(d, segments, n, "SUM");
            case MIN_DISTANCE -> aggregate(d, segments, n, "MIN");
            case MAX_DISTANCE -> aggregate(d, segments, n, "MAX");
            case AVG_DISTANCE -> aggregate(d, segments, n, "AVG");
            case DURATION -> t;
            case TOT_DURATION -> aggregate(t, segments, n, "SUM");
            case MIN_DURATION -> aggregate(t, segments, n, "MIN");
            case MAX_DURATION -> aggregate(t, segments, n, "MAX");
            case AVG_DURATION -> aggregate(t, segments, n, "AVG");
            case SPEED -> s;
            case MIN_SPEED -> aggregate(s, segments, n, "MIN");
            case MAX_SPEED -> aggregate(s, segments, n, "MAX");
            case AVG_SPEED -> functions.try_divide(aggregate(functions.when(s.isNotNull(), d), segments, n, "SUM"),
                    aggregate(functions.when(s.isNotNull(), t), segments, n, "SUM"));
            case ACCELERATION -> a;
            case MIN_ACCELERATION -> aggregate(a, accelerationWindow, n - 1, "MIN");
            case MAX_ACCELERATION -> aggregate(a, accelerationWindow, n - 1, "MAX");
            case ELEVATION -> z;
            case ELEV_CHANGE -> dz;
            case TOT_ELEV_CHANGE -> aggregate(dz, segments, n, "SUM");
            case MIN_ELEVATION -> functions.min(z).over(points);
            case MAX_ELEVATION -> functions.max(z).over(points);
            case AVG_ELEVATION -> functions.avg(z).over(points);
            case SLOPE -> slope;
            case MIN_SLOPE -> aggregate(slope, segments, n, "MIN");
            case MAX_SLOPE -> aggregate(slope, segments, n, "MAX");
            case AVG_SLOPE -> aggregate(slope, segments, n, "AVG");
            case IDLING -> idle;
            case TOT_IDLE_TIME -> aggregate(functions.when(idle, t).when(idle.equalTo(false), 0d), segments, n, "SUM");
            case PCT_IDLE_TIME -> functions.try_divide(
                    aggregate(functions.when(idle, t).when(idle.equalTo(false), 0d), segments, n, "SUM"),
                    aggregate(functions.when(idle.isNotNull(), t), segments, n, "SUM")).multiply(100d);
            case BEARING -> v(names, Value.BEARING);
        };
        return switch (kind.group()) {
            case DISTANCE -> value.divide(distanceUnit);
            case DURATION -> value.multiply(1000d / TrackNodeSupport.millisPerUnit(o.durationUnit()));
            case SPEED -> value.multiply(speedFactor(o.speedUnit()));
            case ACCELERATION -> value.multiply(o.accelerationUnit() == SpatialAccelerationUnit.FEET_PER_SECOND_SQUARED ? 1d / 0.3048d : 1d);
            case ELEVATION -> value.divide(unitMetres(o.elevationUnit()));
            case IDLE -> kind == TrackMotionStatistic.TOT_IDLE_TIME
                    ? value.multiply(1000d / TrackNodeSupport.millisPerUnit(o.durationUnit())) : value;
            default -> value;
        };
    }

    private static Column aggregate(Column c, WindowSpec window, int observations, String kind) {
        if (observations < 2) return functions.lit(null).cast(DataTypes.DoubleType);
        return (switch (kind) { case "SUM" -> functions.sum(c); case "MIN" -> functions.min(c);
            case "MAX" -> functions.max(c); default -> functions.avg(c); }).over(window);
    }

    private static Set<TrackMotionStatisticGroup> validate(TrackMotionStatisticsConfiguration c, SparkCanvasTable source,
            TrackNodeSupport.PreparedTrack prepared, CanvasNodeIssueSink issues) {
        var o = c.windowOptions();
        if (o.observationCount() == null || o.observationCount() < 1 || o.observationCount() > 100)
            issues.error("INVALID_TRACK_HISTORY_WINDOW", "历史窗口需要 1 到 100 个观测（含当前点）", "configuration.windowOptions.observationCount");
        Set<TrackMotionStatistic> kinds = EnumSet.noneOf(TrackMotionStatistic.class);
        Set<TrackMotionStatisticGroup> groups = EnumSet.noneOf(TrackMotionStatisticGroup.class);
        Set<String> names = new HashSet<>();
        Set<UUID> ids = new HashSet<>();
        if (source != null) source.schema().columns().forEach(field -> names.add(field.name().toLowerCase(Locale.ROOT)));
        if (o.statistics().isEmpty()) issues.error("TRACK_MOTION_METRICS_REQUIRED", "请选择指标组", "configuration.windowOptions.statistics");
        for (int i = 0; i < o.statistics().size(); i++) {
            var statistic = o.statistics().get(i);
            String path = "configuration.windowOptions.statistics[" + i + "]";
            if (statistic.kind() == null) { issues.error("REQUIRED_CONFIGURATION", "请选择指标", path + ".kind"); continue; }
            groups.add(statistic.kind().group());
            if (!kinds.add(statistic.kind())) issues.error("DUPLICATE_TRACK_MOTION_STATISTIC", "指标不能重复", path + ".kind");
            try {
                if (!ids.add(UUID.fromString(statistic.statisticId()))) issues.error("DUPLICATE_TRACK_MOTION_METRIC_ID", "指标 ID 重复", path + ".statisticId");
            } catch (RuntimeException e) { issues.error("INVALID_TRACK_MOTION_METRIC_ID", "指标 ID 必须是 UUID", path + ".statisticId"); }
            CanvasNodeSupport.required(statistic.outputColumnName(), "请输入输出字段名", path + ".outputColumnName", issues);
            if (!CanvasNodeSupport.blank(statistic.outputColumnName()) && !names.add(statistic.outputColumnName().toLowerCase(Locale.ROOT)))
                issues.error("DUPLICATE_COLUMN_NAME", "输出字段名重复", path + ".outputColumnName");
        }
        for (var kind : TrackMotionStatistic.values()) if (groups.contains(kind.group()) && !kinds.contains(kind))
            issues.error("TRACK_MOTION_GROUP_INCOMPLETE", "所选指标组缺少 " + kind, "configuration.windowOptions.statistics");
        if (groups.contains(TrackMotionStatisticGroup.DISTANCE)) requiredUnit(o.distanceUnit(), "distanceUnit", issues);
        if (groups.contains(TrackMotionStatisticGroup.DURATION) || groups.contains(TrackMotionStatisticGroup.IDLE)) requiredUnit(o.durationUnit(), "durationUnit", issues);
        if (groups.contains(TrackMotionStatisticGroup.SPEED)) requiredUnit(o.speedUnit(), "speedUnit", issues);
        if (groups.contains(TrackMotionStatisticGroup.ACCELERATION)) requiredUnit(o.accelerationUnit(), "accelerationUnit", issues);
        if (groups.contains(TrackMotionStatisticGroup.ELEVATION) || groups.contains(TrackMotionStatisticGroup.SLOPE)) {
            if (!Double.isFinite(unitMetres(o.inputElevationUnit())))
                issues.error("TRACK_ELEVATION_UNIT_REQUIRED", "输入高程需要明确线性单位，不能从 XY 推断", "configuration.windowOptions.inputElevationUnit");
            if (groups.contains(TrackMotionStatisticGroup.ELEVATION) && !Double.isFinite(unitMetres(o.elevationUnit())))
                issues.error("INVALID_TRACK_METRIC_UNIT", "请选择高程输出线性单位", "configuration.windowOptions.elevationUnit");
            if (CanvasNodeSupport.blank(o.elevationColumnName())) {
                if (prepared != null && prepared.pointSchema().geometry().dimension() != CoordinateDimension.XYZ
                        && prepared.pointSchema().geometry().dimension() != CoordinateDimension.XYZM)
                    issues.error("TRACK_Z_DIMENSION_REQUIRED", "请选择独立高程字段，或使用带 Z 的 Point", "configuration.windowOptions.elevationColumnName");
            } else if (source != null && !CanvasNodeSupport.columns(source.schema()).containsKey(o.elevationColumnName()))
                issues.error("COLUMN_NOT_FOUND", "高程来源字段不存在", "configuration.windowOptions.elevationColumnName");
        }
        if (groups.contains(TrackMotionStatisticGroup.IDLE)) {
            if (c.idleDistanceThreshold() == null || !Double.isFinite(c.idleDistanceThreshold()) || c.idleDistanceThreshold() < 0 || c.idleDistanceThresholdUnit() == null)
                issues.error("INVALID_IDLE_DISTANCE_THRESHOLD", "请配置非负距离阈值和单位", "configuration.idleDistanceThreshold");
            if (o.idleTimeThreshold() == null || !Double.isFinite(o.idleTimeThreshold()) || o.idleTimeThreshold() < 0 || o.idleTimeThresholdUnit() == null)
                issues.error("INVALID_IDLE_TIME_THRESHOLD", "请配置非负时间阈值和单位", "configuration.windowOptions.idleTimeThreshold");
        }
        return groups;
    }
    private static void requiredUnit(Object unit, String name, CanvasNodeIssueSink issues) {
        if (unit == null) issues.error("INVALID_TRACK_METRIC_UNIT", "请选择指标输出单位", "configuration.windowOptions." + name);
    }
    private static Geometry validPoint(Geometry value, boolean geodesic) {
        if (value == null || value.isEmpty()) return null;
        if (!(value instanceof Point p) || !Double.isFinite(p.getX()) || !Double.isFinite(p.getY())
                || geodesic && (Math.abs(p.getX()) > 180 || Math.abs(p.getY()) > 90))
            throw new IllegalArgumentException("TRACK_MOTION_POINT_INVALID");
        return value;
    }
    private static Double bearing(Geometry previous, Geometry current, boolean geodesic) {
        if (previous == null || current == null || previous.equalsExact(current)) return null;
        var a = previous.getCoordinate(); var b = current.getCoordinate();
        double degrees;
        if (geodesic) {
            var inverse = Geodesic.WGS84.Inverse(a.y, a.x, b.y, b.x);
            if (inverse.s12 == 0d) return null; // +180 and -180 may denote the same position.
            degrees = inverse.azi1;
        } else degrees = Math.toDegrees(Math.atan2(b.x - a.x, b.y - a.y));
        return (degrees + 360) % 360;
    }
    private static double unitMetres(SpatialDistanceUnit unit) { return unit == null ? Double.NaN : SpatialDistanceSupport.metresPerConfiguredUnit(unit); }
    private static double speedFactor(SpatialSpeedUnit unit) {
        return switch (unit) { case METERS_PER_SECOND -> 1d; case KILOMETERS_PER_HOUR -> 3.6d;
            case FEET_PER_SECOND -> 1d / 0.3048d; case MILES_PER_HOUR -> 3600d / 1609.344d; case KNOTS -> 3600d / 1852d; };
    }
    private static Column v(Map<Value, String> names, Value name) { return col(names.get(name)); }
    private static Column col(String name) { return functions.col(CanvasNodeSupport.quoteIdentifier(name)); }
    private static Column[] columns(List<String> names) { return names.stream().map(MotionWindowPlan::col).toArray(Column[]::new); }
}
