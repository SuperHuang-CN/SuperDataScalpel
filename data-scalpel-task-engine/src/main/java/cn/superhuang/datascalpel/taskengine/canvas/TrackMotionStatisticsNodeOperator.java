package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SpatialAccelerationUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialSpeedUnit;
import cn.superhuang.data.scalpel.contract.task.TrackMotionMetric;
import cn.superhuang.data.scalpel.contract.task.TrackMotionStatisticsConfiguration;
import cn.superhuang.data.scalpel.contract.task.TrackMotionStatisticsNodeDefinition;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TrackMotionStatisticsNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.TRACK_MOTION_STATISTICS;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.PROCESSOR;
    }

    @Override
    public Set<CanvasExecutionMode> supportedModes() {
        return Set.of(CanvasExecutionMode.BATCH);
    }

    @Override
    public CanvasNodeOperationResult apply(
            CanvasNodeDefinition definition,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeOperationContext context
    ) {
        if (!(definition instanceof TrackMotionStatisticsNodeDefinition node)) {
            throw new IllegalArgumentException("TRACK_MOTION_STATISTICS operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        TrackMotionStatisticsConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(inputSchemas);
        if (configuration.usesObservationWindow()) return MotionWindowPlan.apply(configuration, inputs, context);
        CanvasNodeIssueSink issues = context.issues();
        validateConfiguration(configuration, inputs, issues);
        SparkCanvasTable source = inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error("TABLE_NOT_FOUND", "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName");
        }
        TrackNodeSupport.PreparedTrack prepared = TrackNodeSupport.prepare(
                source, configuration.pointGeometryColumnName(), true,
                configuration.trackIdColumns(), configuration.timeColumnName(),
                configuration.distanceMethod(), configuration.boundaries(), issues, "configuration");
        List<ResolvedMetric> metrics = validateMetrics(configuration, source, prepared, issues);
        if (issues.hasErrors() || prepared == null) return CanvasNodeOperationResult.invalid(inputSchemas);

        Dataset<Row> dataset = prepared.dataset();
        List<Column> partitionColumns = new ArrayList<>();
        for (CanvasColumnSchema id : prepared.trackIdSchemas()) {
            partitionColumns.add(TrackNodeSupport.column(dataset, id.name()));
        }
        partitionColumns.add(TrackNodeSupport.column(dataset, prepared.segmentColumnName()));
        WindowSpec ordered = Window.partitionBy(partitionColumns.toArray(Column[]::new))
                .orderBy(TrackNodeSupport.column(dataset, configuration.timeColumnName()).asc());
        String previousPointName = TrackNodeSupport.internalName(dataset, "__datascalpel_motion_previous_point");
        String previousTimeName = TrackNodeSupport.internalName(dataset, "__datascalpel_motion_previous_time");
        String rawDistanceName = TrackNodeSupport.internalName(dataset, "__datascalpel_motion_raw_distance");
        String metresName = TrackNodeSupport.internalName(dataset, "__datascalpel_motion_metres");
        String durationMillisName = TrackNodeSupport.internalName(dataset, "__datascalpel_motion_duration_millis");
        String speedName = TrackNodeSupport.internalName(dataset, "__datascalpel_motion_speed_mps");
        String accelerationName = TrackNodeSupport.internalName(dataset, "__datascalpel_motion_acceleration_mps2");
        String zChangeName = TrackNodeSupport.internalName(dataset, "__datascalpel_motion_z_change");

        Dataset<Row> historical = dataset
                .withColumn(previousPointName, functions.lag(
                        TrackNodeSupport.column(dataset, configuration.pointGeometryColumnName()),
                        configuration.historyPoints()).over(ordered))
                .withColumn(previousTimeName, functions.lag(
                        TrackNodeSupport.column(dataset, configuration.timeColumnName()),
                        configuration.historyPoints()).over(ordered));
        Column currentPoint = TrackNodeSupport.column(historical, configuration.pointGeometryColumnName());
        Column previousPoint = TrackNodeSupport.column(historical, previousPointName);
        Column currentTime = TrackNodeSupport.column(historical, configuration.timeColumnName());
        Column previousTime = TrackNodeSupport.column(historical, previousTimeName);
        Column durationMillis = functions.unix_micros(currentTime)
                .minus(functions.unix_micros(previousTime)).divide(functions.lit(1000d));
        Column rawDistance = TrackNodeSupport.distance(currentPoint, previousPoint, configuration.distanceMethod());
        double metresPerRawUnit = metresPerRawUnit(configuration, prepared, issues);
        if (issues.hasErrors()) return CanvasNodeOperationResult.invalid(inputSchemas);
        Dataset<Row> measured = historical
                .withColumn(rawDistanceName, rawDistance)
                .withColumn(metresName, rawDistance.multiply(functions.lit(metresPerRawUnit)))
                .withColumn(durationMillisName, durationMillis);
        Dataset<Row> motionBase = measured
                .withColumn(speedName, TrackNodeSupport.column(measured, metresName)
                        .divide(TrackNodeSupport.column(measured, durationMillisName)
                                .divide(functions.lit(1000d))))
                .withColumn(zChangeName, st_functions.ST_Z(currentPoint).minus(st_functions.ST_Z(previousPoint)));

        List<Column> accelerationPartitions = new ArrayList<>();
        for (CanvasColumnSchema id : prepared.trackIdSchemas()) {
            accelerationPartitions.add(TrackNodeSupport.column(motionBase, id.name()));
        }
        accelerationPartitions.add(TrackNodeSupport.column(motionBase, prepared.segmentColumnName()));
        WindowSpec accelerationWindow = Window.partitionBy(accelerationPartitions.toArray(Column[]::new))
                .orderBy(TrackNodeSupport.column(motionBase, configuration.timeColumnName()).asc());
        Column speed = TrackNodeSupport.column(motionBase, speedName);
        Dataset<Row> motion = motionBase.withColumn(accelerationName,
                speed.minus(functions.lag(speed, 1).over(accelerationWindow))
                        .divide(TrackNodeSupport.column(motionBase, durationMillisName).divide(functions.lit(1000d))));

        List<Column> projection = new ArrayList<>();
        for (CanvasColumnSchema column : source.schema().columns()) {
            projection.add(TrackNodeSupport.column(motion, column.name()));
        }
        for (ResolvedMetric metric : metrics) {
            projection.add(metricExpression(
                    metric.metric(), TrackNodeSupport.column(motion, rawDistanceName),
                    TrackNodeSupport.column(motion, metresName),
                    TrackNodeSupport.column(motion, durationMillisName),
                    TrackNodeSupport.column(motion, speedName),
                    TrackNodeSupport.column(motion, accelerationName),
                    TrackNodeSupport.column(motion, configuration.pointGeometryColumnName()),
                    TrackNodeSupport.column(motion, previousPointName),
                    TrackNodeSupport.column(motion, zChangeName), configuration, prepared, issues)
                    .alias(metric.metric().outputColumnName()));
        }
        if (issues.hasErrors()) return CanvasNodeOperationResult.invalid(inputSchemas);
        Dataset<Row> result = motion.select(projection.toArray(Column[]::new));
        List<CanvasColumnSchema> outputColumns = new ArrayList<>(source.schema().columns());
        for (ResolvedMetric metric : metrics) {
            outputColumns.add(metric.metric() instanceof TrackMotionMetric.Idle
                    ? TrackNodeSupport.booleanColumn(metric.metric().outputColumnName(), true)
                    : TrackNodeSupport.doubleColumn(metric.metric().outputColumnName(), true));
        }
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(), null, outputColumns, CanvasDatasetKind.BOUNDED,
                source.schema().eventTimeColumn(), null);
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, result));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateConfiguration(
            TrackMotionStatisticsConfiguration configuration,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeIssueSink issues
    ) {
        CanvasNodeSupport.required(configuration.sourceTableName(), "请选择来源表",
                "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(configuration.pointGeometryColumnName(), "请选择点 Geometry 字段",
                "configuration.pointGeometryColumnName", issues);
        CanvasNodeSupport.required(configuration.timeColumnName(), "请选择时间字段",
                "configuration.timeColumnName", issues);
        CanvasNodeSupport.required(configuration.outputTableName(), "请输入输出表名",
                "configuration.outputTableName", issues);
        if (configuration.historyPoints() < 1
                || configuration.historyPoints() > TrackMotionStatisticsConfiguration.MAX_HISTORY_POINTS) {
            issues.error("INVALID_TRACK_HISTORY_POINTS", "历史点数必须在 1 到 100 之间",
                    "configuration.historyPoints");
        }
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName");
        }
        boolean idle = configuration.metrics() != null
                && configuration.metrics().stream().anyMatch(TrackMotionMetric.Idle.class::isInstance);
        if (idle) {
            if (configuration.idleDistanceThreshold() == null
                    || !Double.isFinite(configuration.idleDistanceThreshold())
                    || configuration.idleDistanceThreshold() < 0) {
                issues.error("INVALID_IDLE_DISTANCE_THRESHOLD", "IDLE 指标需要非负的距离阈值",
                        "configuration.idleDistanceThreshold");
            }
            if (configuration.idleDistanceThresholdUnit() == null) {
                issues.error("INVALID_IDLE_DISTANCE_THRESHOLD", "IDLE 指标需要距离阈值单位",
                        "configuration.idleDistanceThresholdUnit");
            }
        }
    }

    private static List<ResolvedMetric> validateMetrics(
            TrackMotionStatisticsConfiguration configuration,
            SparkCanvasTable source,
            TrackNodeSupport.PreparedTrack prepared,
            CanvasNodeIssueSink issues
    ) {
        List<TrackMotionMetric> configured = configuration.metrics();
        if (configured == null || configured.isEmpty()) {
            issues.error("TRACK_MOTION_METRICS_REQUIRED", "请至少配置一个运动指标", "configuration.metrics");
            return List.of();
        }
        if (configured.size() > TrackMotionStatisticsConfiguration.MAX_METRICS) {
            issues.error("TRACK_MOTION_METRIC_COUNT_EXCEEDED", "运动指标不能超过 16 项", "configuration.metrics");
        }
        Set<UUID> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        if (source != null) {
            for (CanvasColumnSchema column : source.schema().columns()) {
                names.add(column.name().toLowerCase(Locale.ROOT));
            }
        }
        List<ResolvedMetric> resolved = new ArrayList<>();
        for (int index = 0; index < configured.size(); index++) {
            TrackMotionMetric metric = configured.get(index);
            String path = "configuration.metrics[" + index + "]";
            if (metric == null) {
                issues.error("REQUIRED_CONFIGURATION", "运动指标不能为空", path);
                continue;
            }
            try {
                if (!ids.add(UUID.fromString(metric.metricId()))) {
                    issues.error("DUPLICATE_TRACK_MOTION_METRIC_ID", "运动指标 ID 重复", path + ".metricId");
                }
            } catch (RuntimeException exception) {
                issues.error("INVALID_TRACK_MOTION_METRIC_ID", "运动指标 ID 必须是 UUID", path + ".metricId");
            }
            CanvasNodeSupport.required(metric.outputColumnName(), "请输入运动指标输出字段名",
                    path + ".outputColumnName", issues);
            if (!CanvasNodeSupport.blank(metric.outputColumnName())
                    && !names.add(metric.outputColumnName().toLowerCase(Locale.ROOT))) {
                issues.error("DUPLICATE_COLUMN_NAME", "运动指标输出字段名重复：" + metric.outputColumnName(),
                        path + ".outputColumnName");
            }
            validateMetricUnit(metric, prepared, path, issues);
            resolved.add(new ResolvedMetric(metric));
        }
        return List.copyOf(resolved);
    }

    private static void validateMetricUnit(
            TrackMotionMetric metric,
            TrackNodeSupport.PreparedTrack prepared,
            String path,
            CanvasNodeIssueSink issues
    ) {
        switch (metric) {
            case TrackMotionMetric.Distance item -> requireUnit(item.outputUnit(), path, issues);
            case TrackMotionMetric.ElevationChange item -> {
                requireUnit(item.outputUnit(), path, issues);
                if (prepared != null && (prepared.pointSchema().geometry().dimension() == CoordinateDimension.XY
                        || prepared.pointSchema().geometry().dimension() == CoordinateDimension.XYM)) {
                    issues.error("TRACK_Z_DIMENSION_REQUIRED", "高程变化需要 XYZ 或 XYZM Point",
                            path + ".kind");
                }
            }
            case TrackMotionMetric.Duration item -> requireUnit(item.outputUnit(), path, issues);
            case TrackMotionMetric.Speed item -> requireUnit(item.outputUnit(), path, issues);
            case TrackMotionMetric.Acceleration item -> requireUnit(item.outputUnit(), path, issues);
            case TrackMotionMetric.Bearing item -> {
                if (!"DEGREES".equals(item.outputUnit())) {
                    issues.error("INVALID_TRACK_METRIC_UNIT", "方向单位固定为 DEGREES", path + ".outputUnit");
                }
            }
            case TrackMotionMetric.Slope item -> {
                if (!"PERCENT".equals(item.outputUnit())) {
                    issues.error("INVALID_TRACK_METRIC_UNIT", "坡度单位固定为 PERCENT", path + ".outputUnit");
                }
                if (prepared != null && (prepared.pointSchema().geometry().dimension() == CoordinateDimension.XY
                        || prepared.pointSchema().geometry().dimension() == CoordinateDimension.XYM)) {
                    issues.error("TRACK_Z_DIMENSION_REQUIRED", "坡度需要 XYZ 或 XYZM Point", path + ".kind");
                }
            }
            case TrackMotionMetric.Idle item -> {
                if (item.outputUnit() != null) {
                    issues.error("INVALID_TRACK_METRIC_UNIT", "IDLE 指标不能配置单位", path + ".outputUnit");
                }
            }
        }
    }

    private static void requireUnit(Object unit, String path, CanvasNodeIssueSink issues) {
        if (unit == null) issues.error("INVALID_TRACK_METRIC_UNIT", "请选择指标输出单位", path + ".outputUnit");
    }

    private static Column metricExpression(
            TrackMotionMetric metric,
            Column rawDistance,
            Column metres,
            Column durationMillis,
            Column speedMetresPerSecond,
            Column accelerationMetres,
            Column currentPoint,
            Column previousPoint,
            Column zChange,
            TrackMotionStatisticsConfiguration configuration,
            TrackNodeSupport.PreparedTrack prepared,
            CanvasNodeIssueSink issues
    ) {
        return switch (metric) {
            case TrackMotionMetric.Distance item -> rawDistance.divide(functions.lit(
                    rawDistanceUnitsPerOutputUnit(item.outputUnit(), configuration, prepared, issues)));
            case TrackMotionMetric.ElevationChange item -> zChange.divide(functions.lit(
                    rawDistanceUnitsPerOutputUnit(item.outputUnit(), configuration, prepared, issues)));
            case TrackMotionMetric.Duration item -> durationMillis.divide(functions.lit(
                    TrackNodeSupport.millisPerUnit(item.outputUnit())));
            case TrackMotionMetric.Speed item -> speedMetresPerSecond.multiply(functions.lit(
                    speedFactor(item.outputUnit())));
            case TrackMotionMetric.Acceleration item -> accelerationMetres.multiply(functions.lit(
                    accelerationFactor(item.outputUnit())));
            case TrackMotionMetric.Bearing item -> functions.degrees(st_functions.ST_Azimuth(previousPoint, currentPoint));
            case TrackMotionMetric.Slope item -> zChange.divide(rawDistance).multiply(functions.lit(100d));
            case TrackMotionMetric.Idle item -> {
                TrackNodeSupport.DistanceThreshold threshold = TrackNodeSupport.distanceThreshold(
                        configuration.idleDistanceThreshold(), configuration.idleDistanceThresholdUnit(),
                        configuration.distanceMethod(), prepared.pointSchema().geometry(), issues,
                        "configuration.idleDistanceThreshold");
                yield rawDistance.leq(threshold.value());
            }
        };
    }

    private static double metresPerRawUnit(
            TrackMotionStatisticsConfiguration configuration,
            TrackNodeSupport.PreparedTrack prepared,
            CanvasNodeIssueSink issues
    ) {
        if (configuration.distanceMethod() == SpatialDistanceMethod.GEODESIC) return 1d;
        SpatialDistanceSupport.Resolution sourceUnitsPerMetre = SpatialDistanceSupport.resolve(
                1d, SpatialDistanceUnit.METERS, prepared.pointSchema().geometry().crs());
        if (!sourceUnitsPerMetre.valid() || sourceUnitsPerMetre.angular()) {
            boolean needsLinear = configuration.metrics().stream().anyMatch(metric ->
                    metric instanceof TrackMotionMetric.Speed || metric instanceof TrackMotionMetric.Acceleration);
            if (needsLinear) {
                issues.error("TRACK_LINEAR_UNIT_REQUIRED",
                        "速度和加速度需要可换算为米的投影 CRS", "configuration.metrics");
            }
            return 1d;
        }
        return 1d / sourceUnitsPerMetre.sourceCrsValue();
    }

    private static double rawDistanceUnitsPerOutputUnit(
            SpatialDistanceUnit unit,
            TrackMotionStatisticsConfiguration configuration,
            TrackNodeSupport.PreparedTrack prepared,
            CanvasNodeIssueSink issues
    ) {
        if (configuration.distanceMethod() == SpatialDistanceMethod.GEODESIC) {
            double metres = SpatialDistanceSupport.metresPerConfiguredUnit(unit);
            if (!Double.isFinite(metres)) {
                issues.error("INVALID_TRACK_METRIC_UNIT", "测地线距离不能使用来源 CRS 单位", "configuration.metrics");
                return 1d;
            }
            return metres;
        }
        SpatialDistanceSupport.Resolution resolution = SpatialDistanceSupport.sourceUnitsPerConfiguredUnit(
                unit, prepared.pointSchema().geometry().crs());
        if (!resolution.valid()) {
            issues.error("INVALID_TRACK_METRIC_UNIT", resolution.error(), "configuration.metrics");
            return 1d;
        }
        return resolution.sourceCrsValue();
    }

    private static double speedFactor(SpatialSpeedUnit unit) {
        return switch (unit) {
            case METERS_PER_SECOND -> 1d;
            case KILOMETERS_PER_HOUR -> 3.6d;
            case FEET_PER_SECOND -> 3.280839895d;
            case MILES_PER_HOUR -> 2.236936292d;
            case KNOTS -> 1.943844492d;
        };
    }

    private static double accelerationFactor(SpatialAccelerationUnit unit) {
        return unit == SpatialAccelerationUnit.FEET_PER_SECOND_SQUARED ? 3.280839895d : 1d;
    }

    private record ResolvedMetric(TrackMotionMetric metric) {
    }
}
