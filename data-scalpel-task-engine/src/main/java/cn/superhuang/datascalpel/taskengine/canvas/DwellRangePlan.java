package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.*;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.sedona_sql.expressions.st_aggregates;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.locationtech.jts.geom.Geometry;

import java.util.*;

/** Reference-center membership and its four projections, invoked by the single dwell Operator. */
final class DwellRangePlan {
    private DwellRangePlan() { }

    static CanvasNodeOperationResult apply(TrackFindDwellConfiguration c, Map<String, SparkCanvasTable> inputs,
                                           CanvasNodeOperationContext context) {
        var issues = context.issues();
        var inputSchemas = CanvasNodeSupport.schemas(inputs);
        CanvasNodeSupport.required(c.sourceTableName(), "请选择来源表", "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(c.timeColumnName(), "请选择时间字段", "configuration.timeColumnName", issues);
        CanvasNodeSupport.required(c.outputTableName(), "请输入输出表名", "configuration.outputTableName", issues);
        if (!CanvasNodeSupport.blank(c.outputTableName()) && inputs.containsKey(c.outputTableName()))
            issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在", "configuration.outputTableName");
        SparkCanvasTable source = CanvasNodeSupport.blank(c.sourceTableName()) ? null : inputs.get(c.sourceTableName());
        if (!CanvasNodeSupport.blank(c.sourceTableName()) && source == null) issues.error("TABLE_NOT_FOUND", "来源表不存在", "configuration.sourceTableName");
        TrackDwellRangeOptions options = c.rangeOptions();
        if (options == null || options.resultMode() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择驻留结果类型", "configuration.rangeOptions.resultMode");
        }
        if (!Double.isFinite(c.distanceThreshold()) || c.distanceThreshold() <= 0 || c.distanceThresholdUnit() == null) {
            issues.error("INVALID_DWELL_DISTANCE_THRESHOLD", "距离容差必须是带单位的有限正数", "configuration.distanceThreshold");
        }
        if (!Double.isFinite(c.minimumDuration()) || c.minimumDuration() <= 0 || c.minimumDurationUnit() == null) {
            issues.error("INVALID_DWELL_DURATION", "最短持续时间必须是带单位的有限正数", "configuration.minimumDuration");
        }
        var prepared = TrackNodeSupport.prepare(source, c.pointGeometryColumnName(), true, c.trackIdColumns(),
                c.timeColumnName(), c.distanceMethod(), c.boundaries(), issues, "configuration",
                options == null ? List.of() : options.orderByColumns(), "configuration.rangeOptions.orderByColumns");
        if (prepared == null || issues.hasErrors()) return CanvasNodeOperationResult.invalid(inputSchemas);
        if (prepared.pointSchema().geometry().dimension() != CoordinateDimension.XY) {
            issues.error("TRACK_DWELL_XY_REQUIRED", "候选范围驻留当前需要 XY Geometry，请先显式转换维度", "configuration.pointGeometryColumnName");
        }
        boolean features = options.resultMode() == TrackDwellResultMode.DWELL_FEATURES || options.resultMode() == TrackDwellResultMode.ALL_FEATURES;
        List<TrackNodeSupport.ResolvedSummary> summaries = features ? List.of()
                : TrackNodeSupport.validateSummaries(c.summaryStatistics(), CanvasNodeSupport.columns(source.schema()), issues, "configuration.summaryStatistics", true);
        Map<String, String> outputNames = new LinkedHashMap<>();
        outputNames.put("dwellIdColumnName", c.dwellIdColumnName());
        if (features) outputNames.put("rangeOptions.dwellFlagColumnName", options.dwellFlagColumnName());
        else {
            outputNames.put("startTimeColumnName", c.startTimeColumnName());
            outputNames.put("endTimeColumnName", c.endTimeColumnName());
            outputNames.put("durationColumnName", c.durationColumnName());
            outputNames.put("pointCountColumnName", c.pointCountColumnName());
            outputNames.put("outputGeometryColumnName", c.outputGeometryColumnName());
            outputNames.put("rangeOptions.meanDistanceColumnName", options.meanDistanceColumnName());
            if (options.durationUnit() == null || options.meanDistanceUnit() == null) issues.error("REQUIRED_CONFIGURATION",
                    "请选择输出时长和平均相邻距离的单位", "configuration.rangeOptions");
            for (int i = 0; i < summaries.size(); i++) outputNames.put("summaryStatistics[" + i + "].outputColumnName", summaries.get(i).statistic().outputColumnName());
        }
        Set<String> names = new HashSet<>();
        for (var column : features ? source.schema().columns() : prepared.trackIdSchemas()) names.add(column.name().toLowerCase(Locale.ROOT));
        outputNames.forEach((path, name) -> {
            CanvasNodeSupport.required(name, "请输入输出字段名", "configuration." + path, issues);
            if (!CanvasNodeSupport.blank(name) && !names.add(name.toLowerCase(Locale.ROOT))) {
                issues.error("DUPLICATE_COLUMN_NAME", "驻留输出字段重名", "configuration." + path);
            }
        });
        if (issues.hasErrors()) return CanvasNodeOperationResult.invalid(inputSchemas);
        var threshold = TrackNodeSupport.distanceThreshold(c.distanceThreshold(), c.distanceThresholdUnit(), c.distanceMethod(),
                prepared.pointSchema().geometry(), issues, "configuration.distanceThreshold");
        var outputDistance = features ? null : TrackNodeSupport.distanceThreshold(1, options.meanDistanceUnit(), c.distanceMethod(),
                prepared.pointSchema().geometry(), issues, "configuration.rangeOptions.meanDistanceUnit");
        if (issues.hasErrors()) return CanvasNodeOperationResult.invalid(inputSchemas);

        Dataset<Row> data = prepared.dataset();
        // Grouping keys and aggregate aliases share a namespace; user output names may use any prefix.
        String segment = prepared.segmentColumnName();
        if (names.contains(segment.toLowerCase(Locale.ROOT))) {
            String replacement = internalName(data, segment, names);
            data = data.withColumnRenamed(segment, replacement);
            segment = replacement;
        }
        String membership = internalName(data, "__datascalpel_dwell_membership", names);
        List<String> keys = new ArrayList<>(c.trackIdColumns());
        keys.add(segment);
        List<String> sortNames = new ArrayList<>(keys);
        sortNames.add(c.timeColumnName());
        sortNames.addAll(options.orderByColumns());
        var schema = data.schema();
        var assignment = new DwellRangeAssignment(keys.stream().mapToInt(schema::fieldIndex).toArray(),
                schema.fieldIndex(c.pointGeometryColumnName()), schema.fieldIndex(c.timeColumnName()),
                threshold.value(), TrackNodeSupport.durationMillis(c.minimumDuration(), c.minimumDurationUnit()),
                c.distanceMethod() == SpatialDistanceMethod.GEODESIC);
        Dataset<Row> assigned = data.repartition(columns(keys)).sortWithinPartitions(columns(sortNames))
                .mapPartitions(assignment, Encoders.row(schema.add(membership, org.apache.spark.sql.types.DataTypes.LongType, true)));
        List<String> dwellKeys = new ArrayList<>(keys);
        dwellKeys.add(membership);
        List<Column> identity = new ArrayList<>();
        for (int index = 0; index < dwellKeys.size(); index++) identity.add(col(dwellKeys.get(index)).alias("key_" + index));
        Column dwellId = functions.sha2(functions.to_json(functions.struct(identity.toArray(Column[]::new))), 256);
        List<CanvasColumnSchema> outputColumns = new ArrayList<>();
        Dataset<Row> result;
        if (features) {
            List<Column> projection = new ArrayList<>();
            for (var column : source.schema().columns()) projection.add(col(column.name()));
            projection.add(functions.when(col(membership).isNotNull(), dwellId).alias(c.dwellIdColumnName()));
            projection.add(col(membership).isNotNull().alias(options.dwellFlagColumnName()));
            if (options.resultMode() == TrackDwellResultMode.DWELL_FEATURES) assigned = assigned.filter(col(membership).isNotNull());
            result = assigned.select(projection.toArray(Column[]::new));
            outputColumns.addAll(source.schema().columns());
            outputColumns.add(idColumn(c.dwellIdColumnName(), options.resultMode() == TrackDwellResultMode.ALL_FEATURES));
            outputColumns.add(TrackNodeSupport.booleanColumn(options.dwellFlagColumnName(), false));
        } else {
            Dataset<Row> members = assigned.filter(col(membership).isNotNull());
            List<String> order = new ArrayList<>(List.of(c.timeColumnName())); order.addAll(options.orderByColumns());
            var window = Window.partitionBy(columns(dwellKeys)).orderBy(columns(order));
            String step = internalName(members, "__datascalpel_dwell_step", names);
            members = members.withColumn(step, TrackNodeSupport.distance(col(c.pointGeometryColumnName()),
                    functions.lag(col(c.pointGeometryColumnName()), 1).over(window), c.distanceMethod()));
            String collected = internalName(members, "__datascalpel_dwell_collected", names);
            List<Column> aggregates = new ArrayList<>(List.of(
                    functions.min(col(c.timeColumnName())).alias(c.startTimeColumnName()),
                    functions.max(col(c.timeColumnName())).alias(c.endTimeColumnName()),
                    functions.count(functions.lit(1)).alias(c.pointCountColumnName()),
                    functions.avg(col(step)).divide(outputDistance.value()).alias(options.meanDistanceColumnName()),
                    st_aggregates.ST_Collect_Agg(col(c.pointGeometryColumnName())).alias(collected)));
            Column orderKey = functions.struct(columns(order));
            for (var summary : summaries) aggregates.add((switch (summary.statistic().kind()) {
                case FIRST -> functions.min_by(col(summary.source().name()), orderKey);
                case LAST -> functions.max_by(col(summary.source().name()), orderKey);
                default -> TrackNodeSupport.summaryExpression(summary, members);
            }).alias(summary.statistic().outputColumnName()));
            Dataset<Row> grouped = members.groupBy(columns(dwellKeys)).agg(aggregates.getFirst(), aggregates.subList(1, aggregates.size()).toArray(Column[]::new));
            boolean geodesic = c.distanceMethod() == SpatialDistanceMethod.GEODESIC;
            Column geometry = options.resultMode() == TrackDwellResultMode.MEAN_CENTERS
                    ? functions.udf((UDF1<Geometry, Geometry>) points -> DwellRangeAssignment.meanCenter(points, geodesic),
                        schema.apply(c.pointGeometryColumnName()).dataType()).apply(col(collected))
                    : functions.udf((UDF1<Geometry, Geometry>) points -> DwellHullGeometry.hull(points, geodesic),
                        schema.apply(c.pointGeometryColumnName()).dataType()).apply(col(collected));
            geometry = st_functions.ST_SetSRID(geometry, functions.lit(prepared.pointSchema().geometry().crs().code()));
            List<Column> projection = new ArrayList<>(Arrays.asList(columns(c.trackIdColumns())));
            projection.add(dwellId.alias(c.dwellIdColumnName()));
            projection.add(col(c.startTimeColumnName())); projection.add(col(c.endTimeColumnName()));
            projection.add(functions.unix_micros(col(c.endTimeColumnName())).minus(functions.unix_micros(col(c.startTimeColumnName())))
                    .divide(1000d * TrackNodeSupport.millisPerUnit(options.durationUnit())).alias(c.durationColumnName()));
            projection.add(col(c.pointCountColumnName())); projection.add(col(options.meanDistanceColumnName()));
            for (var summary : summaries) projection.add(col(summary.statistic().outputColumnName()));
            projection.add(geometry.alias(c.outputGeometryColumnName()));
            result = grouped.select(projection.toArray(Column[]::new));
            outputColumns.addAll(prepared.trackIdSchemas()); outputColumns.add(idColumn(c.dwellIdColumnName(), false));
            outputColumns.add(TrackNodeSupport.timestampColumn(c.startTimeColumnName(), false));
            outputColumns.add(TrackNodeSupport.timestampColumn(c.endTimeColumnName(), false));
            outputColumns.add(TrackNodeSupport.doubleColumn(c.durationColumnName(), false));
            outputColumns.add(TrackNodeSupport.longColumn(c.pointCountColumnName(), false));
            outputColumns.add(TrackNodeSupport.doubleColumn(options.meanDistanceColumnName(), true));
            for (var summary : summaries) outputColumns.add(TrackNodeSupport.summarySchema(summary));
            outputColumns.add(new CanvasColumnSchema(c.outputGeometryColumnName(), PlatformDataType.GEOMETRY, null, null, null,
                    false, null, false, false, null, new GeometryTypeDefinition(options.resultMode() == TrackDwellResultMode.MEAN_CENTERS
                    ? GeometryKind.POINT : GeometryKind.GEOMETRY, prepared.pointSchema().geometry().crs(), CoordinateDimension.XY)));
        }
        if (!features) outputColumns = cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper.fromStructType(result.schema(), outputColumns);
        var outputSchema = new CanvasTableSchema(c.outputTableName(), null, outputColumns, CanvasDatasetKind.BOUNDED,
                features ? source.schema().eventTimeColumn() : null, null);
        var output = new LinkedHashMap<>(inputs); output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, result));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static Column col(String name) { return functions.col(CanvasNodeSupport.quoteIdentifier(name)); }
    private static String internalName(Dataset<Row> data, String base, Set<String> outputNames) {
        Set<String> occupied = new HashSet<>(outputNames);
        for (String column : data.columns()) occupied.add(column.toLowerCase(Locale.ROOT));
        String result = base;
        while (occupied.contains(result.toLowerCase(Locale.ROOT))) result += "_";
        return result;
    }
    private static Column[] columns(List<String> names) { return names.stream().map(DwellRangePlan::col).toArray(Column[]::new); }
    private static CanvasColumnSchema idColumn(String name, boolean nullable) {
        return new CanvasColumnSchema(name, PlatformDataType.STRING, 64, null, null, nullable, null, false, false, null, null);
    }
}
