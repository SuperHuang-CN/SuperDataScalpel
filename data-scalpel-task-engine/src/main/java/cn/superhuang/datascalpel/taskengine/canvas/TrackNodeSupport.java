package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialDurationUnit;
import cn.superhuang.data.scalpel.contract.task.TrackBoundaryConfiguration;
import cn.superhuang.data.scalpel.contract.task.TrackSummaryStatistic;
import cn.superhuang.data.scalpel.contract.task.TrackSummaryStatisticKind;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class TrackNodeSupport {

    private TrackNodeSupport() {
    }

    static PreparedTrack prepare(
            SparkCanvasTable source,
            String pointGeometryColumnName,
            boolean geometryRequired,
            List<String> trackIdColumns,
            String timeColumnName,
            SpatialDistanceMethod distanceMethod,
            TrackBoundaryConfiguration boundaries,
            CanvasNodeIssueSink issues,
            String path
    ) {
        return prepare(source, pointGeometryColumnName, geometryRequired, trackIdColumns, timeColumnName,
                distanceMethod, boundaries, issues, path, null);
    }

    /** A non-null tie-breaker list opts into deterministic instant-observation ordering. */
    static PreparedTrack prepare(
            SparkCanvasTable source,
            String pointGeometryColumnName,
            boolean geometryRequired,
            List<String> trackIdColumns,
            String timeColumnName,
            SpatialDistanceMethod distanceMethod,
            TrackBoundaryConfiguration boundaries,
            CanvasNodeIssueSink issues,
            String path,
            List<String> orderByColumns
    ) {
        return prepare(source, pointGeometryColumnName, geometryRequired, trackIdColumns, timeColumnName,
                distanceMethod, boundaries, issues, path, orderByColumns, path + ".orderByColumns");
    }

    static PreparedTrack prepare(
            SparkCanvasTable source, String pointGeometryColumnName, boolean geometryRequired,
            List<String> trackIdColumns, String timeColumnName, SpatialDistanceMethod distanceMethod,
            TrackBoundaryConfiguration boundaries, CanvasNodeIssueSink issues, String path,
            List<String> orderByColumns, String orderPathPrefix
    ) {
        return prepare(source, pointGeometryColumnName, geometryRequired, trackIdColumns, timeColumnName,
                distanceMethod, boundaries, issues, path, orderByColumns, orderPathPrefix, false);
    }

    /** Area reconstruction alone may use polygon observations; other track nodes remain Point-only. */
    static PreparedTrack prepare(
            SparkCanvasTable source, String pointGeometryColumnName, boolean geometryRequired,
            List<String> trackIdColumns, String timeColumnName, SpatialDistanceMethod distanceMethod,
            TrackBoundaryConfiguration boundaries, CanvasNodeIssueSink issues, String path,
            List<String> orderByColumns, String orderPathPrefix, boolean allowPolygon
    ) {
        if (source == null) return null;
        if (source.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error("BOUNDED_INPUT_REQUIRED", "轨迹分析只支持有界输入", path + ".sourceTableName");
        }
        Map<String, CanvasColumnSchema> columns = CanvasNodeSupport.columns(source.schema());
        List<CanvasColumnSchema> ids = validateTrackIds(trackIdColumns, columns, issues, path);
        CanvasColumnSchema time = columns.get(timeColumnName);
        if (!CanvasNodeSupport.blank(timeColumnName) && time == null) {
            issues.error("COLUMN_NOT_FOUND", "时间字段不存在：" + timeColumnName,
                    path + ".timeColumnName");
        } else if (time != null && time.fieldType() != PlatformDataType.TIMESTAMP) {
            issues.error("TRACK_TIME_COLUMN_TYPE_INVALID", "轨迹时间字段必须是 TIMESTAMP",
                    path + ".timeColumnName");
        }
        CanvasColumnSchema point = null;
        boolean pointRequired = geometryRequired || hasDistanceBoundary(boundaries) || orderByColumns == null;
        if (!CanvasNodeSupport.blank(pointGeometryColumnName)) {
            point = columns.get(pointGeometryColumnName);
            if (point == null) {
                issues.error("COLUMN_NOT_FOUND", "点 Geometry 字段不存在：" + pointGeometryColumnName,
                        path + ".pointGeometryColumnName");
            } else if (point.fieldType() != PlatformDataType.GEOMETRY || point.geometry() == null
                    || pointRequired && point.geometry().kind() != GeometryKind.POINT
                    && !(allowPolygon && (point.geometry().kind() == GeometryKind.POLYGON
                            || point.geometry().kind() == GeometryKind.MULTIPOLYGON))) {
                issues.error(allowPolygon ? "TRACK_AREA_GEOMETRY_REQUIRED" : "TRACK_POINT_GEOMETRY_REQUIRED",
                        allowPolygon ? "面轨迹需要 Point、Polygon 或 MultiPolygon Geometry" : "轨迹分析需要带完整元数据的 Point Geometry",
                        path + ".pointGeometryColumnName");
            }
        } else if (geometryRequired) {
            issues.error("REQUIRED_CONFIGURATION", "请选择点 Geometry 字段",
                    path + ".pointGeometryColumnName");
        }
        validateBoundaries(boundaries, point, distanceMethod, issues, path + ".boundaries");
        TrackTimeBoundarySupport.Bucket fixedBoundary = TrackTimeBoundarySupport.resolve(
                boundaries == null ? null : boundaries.fixedTimeBoundary(), issues, path + ".boundaries.fixedTimeBoundary");
        if (distanceMethod == null && (geometryRequired || hasDistanceBoundary(boundaries))) {
            issues.error("REQUIRED_CONFIGURATION", "请选择距离方法", path + ".distanceMethod");
        }
        if (point != null && point.geometry() != null && pointRequired && distanceMethod == SpatialDistanceMethod.GEODESIC) {
            GeometryTypeDefinition geometry = point.geometry();
            if (!"EPSG".equalsIgnoreCase(geometry.crs().authority()) || geometry.crs().code() != 4326
                    || geometry.dimension() != CoordinateDimension.XY) {
                issues.error("GEODESIC_REQUIRES_WGS84_XY", "测地线轨迹分析只支持 EPSG:4326 XY",
                        path + ".distanceMethod");
            }
        }
        if (orderByColumns != null) {
            Set<String> orderNames = new HashSet<>();
            for (int index = 0; index < orderByColumns.size(); index++) {
                String name = orderByColumns.get(index);
                String orderPath = orderPathPrefix + "[" + index + "]";
                if (CanvasNodeSupport.blank(name) || !columns.containsKey(name)) {
                    issues.error("COLUMN_NOT_FOUND", "同时间顺序字段不存在", orderPath);
                } else if (columns.get(name).fieldType() == PlatformDataType.GEOMETRY) {
                    issues.error("TRACK_ORDER_COLUMN_INVALID", "同时间顺序不能使用 Geometry 字段", orderPath);
                } else if (!orderNames.add(name.toLowerCase(Locale.ROOT))) {
                    issues.error("DUPLICATE_COLUMN_NAME", "同时间顺序字段重复", orderPath);
                }
            }
        }
        if (issues.hasErrors() || ids.isEmpty() || time == null || (geometryRequired && point == null)) {
            return null;
        }

        Dataset<Row> dataset = source.dataset();
        if (fixedBoundary != null) dataset = dataset.filter(column(dataset, timeColumnName).isNotNull());
        if (orderByColumns != null) {
            // Instant observations without time cannot participate. Uniqueness is checked lazily in Runner.
            dataset = dataset.filter(column(dataset, timeColumnName).isNotNull());
            List<Column> observationKeys = new ArrayList<>();
            for (String name : trackIdColumns) observationKeys.add(column(dataset, name));
            observationKeys.add(column(dataset, timeColumnName));
            for (String name : orderByColumns) observationKeys.add(column(dataset, name));
            // Time is non-null after the filter. Count it explicitly to preserve source lineage.
            Column ambiguous = functions.count(column(dataset, timeColumnName))
                    .over(Window.partitionBy(observationKeys.toArray(Column[]::new))).gt(1);
            dataset = dataset.withColumn(timeColumnName, functions.when(ambiguous,
                    functions.raise_error(functions.lit("TRACK_OBSERVATION_ORDER_NOT_UNIQUE")))
                    .otherwise(column(dataset, timeColumnName)));
        }
        String segment = internalName(dataset, "__datascalpel_track_segment");
        String previousTime = internalName(dataset, "__datascalpel_track_previous_time");
        String previousPoint = internalName(dataset, "__datascalpel_track_previous_point");
        String boundaryBreak = internalName(dataset, "__datascalpel_track_boundary_break");
        String timeBucket = internalName(dataset, "__datascalpel_track_time_bucket");
        if (fixedBoundary != null) {
            dataset = dataset.withColumn(timeBucket, functions.udf(fixedBoundary,
                    org.apache.spark.sql.types.DataTypes.LongType).apply(column(dataset, timeColumnName)));
        }
        List<Column> partitionColumns = new ArrayList<>();
        for (String name : trackIdColumns) partitionColumns.add(column(dataset, name));
        List<Column> sortColumns = new ArrayList<>();
        sortColumns.add(column(dataset, timeColumnName).asc());
        if (orderByColumns != null) {
            for (String name : orderByColumns) sortColumns.add(column(dataset, name).asc_nulls_first());
        }
        WindowSpec ordered = Window.partitionBy(partitionColumns.toArray(Column[]::new))
                .orderBy(sortColumns.toArray(Column[]::new));
        Dataset<Row> staged = dataset.withColumn(previousTime, functions.lag(column(dataset, timeColumnName), 1).over(ordered));
        if (point != null) {
            staged = staged.withColumn(previousPoint,
                    functions.lag(column(dataset, pointGeometryColumnName), 1).over(ordered));
        }
        Column isBreak = column(staged, previousTime).isNull();
        if (fixedBoundary != null) {
            Column bucket = column(staged, timeBucket);
            isBreak = isBreak.or(bucket.notEqual(functions.lag(bucket, 1).over(ordered)));
        }
        if (boundaries != null && boundaries.maximumTimeGap() != null) {
            double limitMillis = durationMillis(boundaries.maximumTimeGap(), boundaries.maximumTimeGapUnit());
            Column gapMillis = functions.unix_micros(column(staged, timeColumnName))
                    .minus(functions.unix_micros(column(staged, previousTime)))
                    .divide(functions.lit(1000d));
            isBreak = isBreak.or(gapMillis.gt(limitMillis));
        }
        if (hasDistanceBoundary(boundaries) && point != null) {
            DistanceThreshold threshold = distanceThreshold(
                    boundaries.maximumDistanceGap(), boundaries.maximumDistanceGapUnit(),
                    distanceMethod, point.geometry(), issues, path + ".boundaries.maximumDistanceGap");
            if (!threshold.valid()) return null;
            Column currentPoint = column(staged, pointGeometryColumnName);
            Column previousPointColumn = column(staged, previousPoint);
            Column exceedsGap = allowPolygon && distanceMethod == SpatialDistanceMethod.GEODESIC
                    ? TrackGeodesicAreaColumns.exceedsGap(currentPoint, previousPointColumn, threshold.value())
                    : distance(currentPoint, previousPointColumn, distanceMethod).gt(threshold.value());
            isBreak = isBreak.or(currentPoint.isNull()).or(previousPointColumn.isNull())
                    .or(exceedsGap);
        }
        staged = staged.withColumn(boundaryBreak, functions.when(isBreak, 1).otherwise(0));
        WindowSpec cumulative = ordered.rowsBetween(Window.unboundedPreceding(), Window.currentRow());
        staged = staged.withColumn(segment, functions.sum(column(staged, boundaryBreak)).over(cumulative));
        return new PreparedTrack(source, staged, List.copyOf(ids), time, point, segment, ordered);
    }

    static List<ResolvedSummary> validateSummaries(
            List<TrackSummaryStatistic> statistics,
            Map<String, CanvasColumnSchema> sourceColumns,
            CanvasNodeIssueSink issues,
            String path,
            boolean allowNumericAny
    ) {
        if (statistics == null) {
            issues.error("INVALID_TRACK_SUMMARIES", "片段汇总必须是数组", path);
            return List.of();
        }
        if (statistics.size() > 32) {
            issues.error("TRACK_SUMMARY_COUNT_EXCEEDED", "片段汇总不能超过 32 项", path);
        }
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        List<ResolvedSummary> resolved = new ArrayList<>();
        for (int index = 0; index < statistics.size(); index++) {
            TrackSummaryStatistic statistic = statistics.get(index);
            String itemPath = path + "[" + index + "]";
            if (statistic == null || statistic.kind() == null) {
                issues.error("REQUIRED_CONFIGURATION", "片段汇总配置不完整", itemPath);
                continue;
            }
            if (!validUuid(statistic.statisticId())) {
                issues.error("INVALID_TRACK_SUMMARY_ID", "片段汇总 ID 必须是 UUID", itemPath + ".statisticId");
            } else if (!ids.add(statistic.statisticId())) {
                issues.error("DUPLICATE_TRACK_SUMMARY_ID", "片段汇总 ID 重复", itemPath + ".statisticId");
            }
            CanvasNodeSupport.required(statistic.outputColumnName(), "请输入汇总输出字段名",
                    itemPath + ".outputColumnName", issues);
            if (!CanvasNodeSupport.blank(statistic.outputColumnName())
                    && !names.add(statistic.outputColumnName().toLowerCase(Locale.ROOT))) {
                issues.error("DUPLICATE_COLUMN_NAME", "汇总输出字段名重复：" + statistic.outputColumnName(),
                        itemPath + ".outputColumnName");
            }
            CanvasColumnSchema source = null;
            if (statistic.kind() != TrackSummaryStatisticKind.COUNT) {
                if (CanvasNodeSupport.blank(statistic.sourceColumnName())) {
                    issues.error("REQUIRED_CONFIGURATION", "请选择汇总来源字段", itemPath + ".sourceColumnName");
                    continue;
                }
                source = CanvasNodeSupport.blank(statistic.sourceColumnName()) ? null : sourceColumns.get(statistic.sourceColumnName());
                if (source == null) {
                    issues.error("COLUMN_NOT_FOUND", "汇总来源字段不存在：" + statistic.sourceColumnName(),
                            itemPath + ".sourceColumnName");
                    continue;
                }
                if (source.fieldType() == PlatformDataType.GEOMETRY) {
                    issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED", "轨迹汇总不能直接使用 Geometry 字段",
                            itemPath + ".sourceColumnName");
                }
                if (numericStatistic(statistic.kind()) && !numeric(source.fieldType())) {
                    issues.error("TRACK_SUMMARY_NUMERIC_FIELD_REQUIRED", "该汇总类型需要数值字段",
                            itemPath + ".sourceColumnName");
                }
                if (statistic.kind() == TrackSummaryStatisticKind.ANY && source.fieldType() != PlatformDataType.STRING
                        && !(allowNumericAny && numeric(source.fieldType()))) {
                    issues.error("TRACK_SUMMARY_ANY_FIELD_NOT_SUPPORTED", allowNumericAny
                            ? "驻留 ANY 需要字符串或数值字段" : "轨迹重建 ANY 需要字符串字段", itemPath + ".sourceColumnName");
                }
            }
            resolved.add(new ResolvedSummary(statistic, source));
        }
        return List.copyOf(resolved);
    }

    static Column summaryExpression(ResolvedSummary summary, Dataset<Row> dataset) {
        TrackSummaryStatistic statistic = summary.statistic();
        Column source = summary.source() == null ? null : column(dataset, summary.source().name());
        return switch (statistic.kind()) {
            case COUNT -> functions.count(functions.lit(1));
            case COUNT_FIELD -> functions.count(source);
            case ANY -> functions.first(source, true);
            case SUM -> functions.sum(source);
            case MEAN -> functions.avg(source);
            case MIN -> functions.min(source);
            case MAX -> functions.max(source);
            case RANGE -> functions.max(source).minus(functions.min(source));
            case STDDEV -> functions.stddev_samp(source);
            case VARIANCE -> functions.var_samp(source);
            case FIRST -> functions.first(source, true);
            case LAST -> functions.last(source, true);
        };
    }

    static CanvasColumnSchema summarySchema(ResolvedSummary summary) {
        TrackSummaryStatistic statistic = summary.statistic();
        if (statistic.kind() == TrackSummaryStatisticKind.COUNT || statistic.kind() == TrackSummaryStatisticKind.COUNT_FIELD) {
            return new CanvasColumnSchema(statistic.outputColumnName(), PlatformDataType.LONG,
                    null, null, null, false, null, false, false, null, null);
        }
        if (statistic.kind() == TrackSummaryStatisticKind.MEAN
                || statistic.kind() == TrackSummaryStatisticKind.STDDEV
                || statistic.kind() == TrackSummaryStatisticKind.VARIANCE) {
            return doubleColumn(statistic.outputColumnName(), true);
        }
        CanvasColumnSchema source = summary.source();
        return new CanvasColumnSchema(statistic.outputColumnName(), source.fieldType(), source.length(),
                source.precision(), source.scale(), true, null, false, false, source.comment(), source.geometry());
    }

    static DistanceThreshold distanceThreshold(
            double value,
            SpatialDistanceUnit unit,
            SpatialDistanceMethod method,
            GeometryTypeDefinition geometry,
            CanvasNodeIssueSink issues,
            String path
    ) {
        if (method == SpatialDistanceMethod.GEODESIC) {
            double metres = value * SpatialDistanceSupport.metresPerConfiguredUnit(unit);
            if (!Double.isFinite(metres)) {
                issues.error("INVALID_SPATIAL_DISTANCE_CONFIGURATION",
                        "测地线距离必须使用明确的线性单位", path);
                return DistanceThreshold.invalid();
            }
            return new DistanceThreshold(metres, true);
        }
        SpatialDistanceSupport.Resolution resolution = SpatialDistanceSupport.resolve(value, unit, geometry.crs());
        if (!resolution.valid()) {
            issues.error("INVALID_SPATIAL_DISTANCE_CONFIGURATION", resolution.error(), path);
            return DistanceThreshold.invalid();
        }
        if (resolution.angular()) {
            issues.warning("PLANAR_GEOGRAPHIC_CRS", "地理 CRS 的平面距离按角度计算",
                    path);
        }
        return new DistanceThreshold(resolution.sourceCrsValue(), true);
    }

    static Column distance(Column left, Column right, SpatialDistanceMethod method) {
        return method == SpatialDistanceMethod.GEODESIC
                ? st_functions.ST_DistanceSpheroid(left, right)
                : st_functions.ST_Distance(left, right);
    }

    static double durationMillis(double value, SpatialDurationUnit unit) {
        return value * switch (unit) {
            case MILLISECONDS -> 1d;
            case SECONDS -> 1_000d;
            case MINUTES -> 60_000d;
            case HOURS -> 3_600_000d;
            case DAYS -> 86_400_000d;
            case WEEKS -> 604_800_000d;
        };
    }

    static double millisPerUnit(SpatialDurationUnit unit) {
        return durationMillis(1d, unit);
    }

    static CanvasColumnSchema doubleColumn(String name, boolean nullable) {
        return new CanvasColumnSchema(name, PlatformDataType.DOUBLE,
                null, null, null, nullable, null, false, false, null, null);
    }

    static CanvasColumnSchema longColumn(String name, boolean nullable) {
        return new CanvasColumnSchema(name, PlatformDataType.LONG,
                null, null, null, nullable, null, false, false, null, null);
    }

    static CanvasColumnSchema booleanColumn(String name, boolean nullable) {
        return new CanvasColumnSchema(name, PlatformDataType.BOOLEAN,
                null, null, null, nullable, null, false, false, null, null);
    }

    static CanvasColumnSchema timestampColumn(String name, boolean nullable) {
        return new CanvasColumnSchema(name, PlatformDataType.TIMESTAMP,
                null, null, null, nullable, null, false, false, null, null);
    }

    static CanvasColumnSchema renamed(CanvasColumnSchema source, String name, boolean nullable) {
        return new CanvasColumnSchema(name, source.fieldType(), source.length(), source.precision(), source.scale(),
                nullable, source.defaultValue(), source.autoIncrement(), source.generated(),
                source.comment(), source.geometry());
    }

    static Column column(Dataset<Row> dataset, String name) {
        return dataset.col(CanvasNodeSupport.quoteIdentifier(name));
    }

    static String internalName(Dataset<Row> dataset, String base) {
        Set<String> names = new HashSet<>();
        for (String name : dataset.columns()) names.add(name.toLowerCase(Locale.ROOT));
        String candidate = base;
        while (names.contains(candidate.toLowerCase(Locale.ROOT))) candidate += "_";
        return candidate;
    }

    private static List<CanvasColumnSchema> validateTrackIds(
            List<String> trackIdColumns,
            Map<String, CanvasColumnSchema> sourceColumns,
            CanvasNodeIssueSink issues,
            String path
    ) {
        if (trackIdColumns == null || trackIdColumns.isEmpty()) {
            issues.error("TRACK_ID_COLUMNS_REQUIRED", "请至少选择一个轨迹标识字段",
                    path + ".trackIdColumns");
            return List.of();
        }
        if (trackIdColumns.size() > 8) {
            issues.error("TRACK_ID_COLUMN_COUNT_EXCEEDED", "轨迹标识字段不能超过 8 个",
                    path + ".trackIdColumns");
        }
        Set<String> names = new HashSet<>();
        List<CanvasColumnSchema> resolved = new ArrayList<>();
        for (int index = 0; index < trackIdColumns.size(); index++) {
            String name = trackIdColumns.get(index);
            String itemPath = path + ".trackIdColumns[" + index + "]";
            if (CanvasNodeSupport.blank(name)) {
                issues.error("REQUIRED_CONFIGURATION", "轨迹标识字段不能为空", itemPath);
                continue;
            }
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                issues.error("DUPLICATE_COLUMN_NAME", "轨迹标识字段重复：" + name, itemPath);
            }
            CanvasColumnSchema column = sourceColumns.get(name);
            if (column == null) {
                issues.error("COLUMN_NOT_FOUND", "轨迹标识字段不存在：" + name, itemPath);
            } else if (column.fieldType() == PlatformDataType.GEOMETRY) {
                issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED", "Geometry 不能作为轨迹标识", itemPath);
            } else {
                resolved.add(column);
            }
        }
        return resolved;
    }

    private static void validateBoundaries(
            TrackBoundaryConfiguration boundaries,
            CanvasColumnSchema point,
            SpatialDistanceMethod method,
            CanvasNodeIssueSink issues,
            String path
    ) {
        if (boundaries == null) {
            issues.error("REQUIRED_CONFIGURATION", "轨迹边界配置不能为空", path);
            return;
        }
        validatePair(boundaries.maximumTimeGap(), boundaries.maximumTimeGapUnit(),
                "最大时间间隔", path + ".maximumTimeGap", path + ".maximumTimeGapUnit", issues);
        validatePair(boundaries.maximumDistanceGap(), boundaries.maximumDistanceGapUnit(),
                "最大空间间隔", path + ".maximumDistanceGap", path + ".maximumDistanceGapUnit", issues);
        if (boundaries.maximumDistanceGap() != null && point == null) {
            issues.error("TRACK_DISTANCE_BOUNDARY_REQUIRES_GEOMETRY",
                    "配置最大空间间隔时必须选择 Point Geometry", path + ".maximumDistanceGap");
        }
        if (boundaries.maximumDistanceGap() != null && method == null) {
            issues.error("REQUIRED_CONFIGURATION", "配置空间边界时必须选择距离方法", path);
        }
    }

    private static void validatePair(
            Double value,
            Object unit,
            String label,
            String valuePath,
            String unitPath,
            CanvasNodeIssueSink issues
    ) {
        if ((value == null) != (unit == null)) {
            issues.error("INVALID_TRACK_BOUNDARY", label + "的数值和单位必须同时配置或同时为空",
                    value == null ? valuePath : unitPath);
        }
        if (value != null && (!Double.isFinite(value) || value <= 0)) {
            issues.error("INVALID_TRACK_BOUNDARY", label + "必须是有限正数", valuePath);
        }
    }

    private static boolean hasDistanceBoundary(TrackBoundaryConfiguration boundaries) {
        return boundaries != null && boundaries.maximumDistanceGap() != null;
    }

    private static boolean numericStatistic(TrackSummaryStatisticKind kind) {
        return kind == TrackSummaryStatisticKind.SUM || kind == TrackSummaryStatisticKind.MEAN
                || kind == TrackSummaryStatisticKind.RANGE || kind == TrackSummaryStatisticKind.STDDEV
                || kind == TrackSummaryStatisticKind.VARIANCE;
    }

    private static boolean numeric(PlatformDataType type) {
        return type == PlatformDataType.BYTE || type == PlatformDataType.SHORT
                || type == PlatformDataType.INTEGER || type == PlatformDataType.LONG
                || type == PlatformDataType.FLOAT || type == PlatformDataType.DOUBLE
                || type == PlatformDataType.DECIMAL;
    }

    private static boolean validUuid(String value) {
        if (CanvasNodeSupport.blank(value)) return false;
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    record PreparedTrack(
            SparkCanvasTable source,
            Dataset<Row> dataset,
            List<CanvasColumnSchema> trackIdSchemas,
            CanvasColumnSchema timeSchema,
            CanvasColumnSchema pointSchema,
            String segmentColumnName,
            WindowSpec orderedWindow
    ) {
        List<Column> groupColumns() {
            List<Column> result = new ArrayList<>();
            for (CanvasColumnSchema id : trackIdSchemas) result.add(column(dataset, id.name()));
            result.add(column(dataset, segmentColumnName));
            return result;
        }
    }

    record ResolvedSummary(TrackSummaryStatistic statistic, CanvasColumnSchema source) {
    }

    record DistanceThreshold(double value, boolean valid) {
        static DistanceThreshold invalid() {
            return new DistanceThreshold(Double.NaN, false);
        }
    }
}
