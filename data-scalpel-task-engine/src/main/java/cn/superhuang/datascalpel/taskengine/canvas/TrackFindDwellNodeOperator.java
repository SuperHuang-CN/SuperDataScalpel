package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.DwellGeometryKind;
import cn.superhuang.data.scalpel.contract.task.TrackFindDwellConfiguration;
import cn.superhuang.data.scalpel.contract.task.TrackFindDwellNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TrackSummaryStatisticKind;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_aggregates;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TrackFindDwellNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.TRACK_FIND_DWELL;
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
        if (!(definition instanceof TrackFindDwellNodeDefinition node)) {
            throw new IllegalArgumentException("TRACK_FIND_DWELL operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        TrackFindDwellConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(inputSchemas);
        if (configuration.usesReferenceCenter()) return DwellRangePlan.apply(configuration, inputs, context);
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
        Map<String, CanvasColumnSchema> sourceColumns = source == null
                ? Map.of() : CanvasNodeSupport.columns(source.schema());
        List<TrackNodeSupport.ResolvedSummary> summaries = TrackNodeSupport.validateSummaries(
                configuration.summaryStatistics(), sourceColumns, issues, "configuration.summaryStatistics", true);
        validateOutputNames(configuration, prepared, summaries, issues);
        if (issues.hasErrors() || prepared == null) return CanvasNodeOperationResult.invalid(inputSchemas);

        TrackNodeSupport.DistanceThreshold distanceThreshold = TrackNodeSupport.distanceThreshold(
                configuration.distanceThreshold(), configuration.distanceThresholdUnit(),
                configuration.distanceMethod(), prepared.pointSchema().geometry(), issues,
                "configuration.distanceThreshold");
        if (issues.hasErrors() || !distanceThreshold.valid()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        Dataset<Row> dataset = prepared.dataset();
        String previousPoint = TrackNodeSupport.internalName(dataset, "__datascalpel_dwell_previous_point");
        String dwellBreak = TrackNodeSupport.internalName(dataset, "__datascalpel_dwell_break");
        String dwellSegment = TrackNodeSupport.internalName(dataset, "__datascalpel_dwell_segment");
        List<Column> partitions = new ArrayList<>();
        for (CanvasColumnSchema id : prepared.trackIdSchemas()) {
            partitions.add(TrackNodeSupport.column(dataset, id.name()));
        }
        partitions.add(TrackNodeSupport.column(dataset, prepared.segmentColumnName()));
        WindowSpec ordered = Window.partitionBy(partitions.toArray(Column[]::new))
                .orderBy(TrackNodeSupport.column(dataset, configuration.timeColumnName()).asc());
        Dataset<Row> staged = dataset.withColumn(previousPoint,
                functions.lag(TrackNodeSupport.column(dataset, configuration.pointGeometryColumnName()), 1)
                        .over(ordered));
        Column currentPoint = TrackNodeSupport.column(staged, configuration.pointGeometryColumnName());
        Column priorPoint = TrackNodeSupport.column(staged, previousPoint);
        Column distance = TrackNodeSupport.distance(currentPoint, priorPoint, configuration.distanceMethod());
        Column startsDwell = priorPoint.isNull().or(currentPoint.isNull())
                .or(distance.gt(distanceThreshold.value()));
        staged = staged.withColumn(dwellBreak, functions.when(startsDwell, 1).otherwise(0));
        WindowSpec cumulative = ordered.rowsBetween(Window.unboundedPreceding(), Window.currentRow());
        staged = staged.withColumn(dwellSegment, functions.sum(TrackNodeSupport.column(staged, dwellBreak)).over(cumulative));

        String countColumn = TrackNodeSupport.internalName(staged, "__datascalpel_dwell_count");
        String pointsColumn = TrackNodeSupport.internalName(staged, "__datascalpel_dwell_points");
        List<Column> groups = new ArrayList<>();
        for (CanvasColumnSchema id : prepared.trackIdSchemas()) groups.add(TrackNodeSupport.column(staged, id.name()));
        groups.add(TrackNodeSupport.column(staged, prepared.segmentColumnName()));
        groups.add(TrackNodeSupport.column(staged, dwellSegment));
        List<Column> aggregations = new ArrayList<>();
        aggregations.add(functions.min(TrackNodeSupport.column(staged, configuration.timeColumnName()))
                .alias(configuration.startTimeColumnName()));
        aggregations.add(functions.max(TrackNodeSupport.column(staged, configuration.timeColumnName()))
                .alias(configuration.endTimeColumnName()));
        aggregations.add(functions.count(functions.lit(1)).alias(countColumn));
        for (TrackNodeSupport.ResolvedSummary summary : summaries) {
            Column expression = switch (summary.statistic().kind()) {
                case FIRST -> functions.min_by(
                        TrackNodeSupport.column(staged, summary.source().name()),
                        TrackNodeSupport.column(staged, configuration.timeColumnName()));
                case LAST -> functions.max_by(
                        TrackNodeSupport.column(staged, summary.source().name()),
                        TrackNodeSupport.column(staged, configuration.timeColumnName()));
                default -> TrackNodeSupport.summaryExpression(summary, staged);
            };
            aggregations.add(expression.alias(summary.statistic().outputColumnName()));
        }
        aggregations.add(st_aggregates.ST_Collect_Agg(
                TrackNodeSupport.column(staged, configuration.pointGeometryColumnName())).alias(pointsColumn));
        Dataset<Row> grouped = staged.groupBy(groups.toArray(Column[]::new))
                .agg(aggregations.getFirst(), aggregations.subList(1, aggregations.size()).toArray(Column[]::new));
        Column durationMillis = functions.unix_micros(
                TrackNodeSupport.column(grouped, configuration.endTimeColumnName()))
                .minus(functions.unix_micros(
                        TrackNodeSupport.column(grouped, configuration.startTimeColumnName())))
                .divide(functions.lit(1000d));
        grouped = grouped.filter(durationMillis.geq(TrackNodeSupport.durationMillis(
                configuration.minimumDuration(), configuration.minimumDurationUnit())));
        GeometryTypeDefinition pointType = prepared.pointSchema().geometry();
        Column collected = TrackNodeSupport.column(grouped, pointsColumn);
        Column geometry = configuration.outputGeometryKind() == DwellGeometryKind.CENTROID
                ? st_functions.ST_Centroid(collected) : st_functions.ST_ConvexHull(collected);
        geometry = st_functions.ST_SetSRID(geometry, functions.lit(pointType.crs().code()));

        List<Column> projection = new ArrayList<>();
        List<Column> dwellIdParts = new ArrayList<>();
        for (CanvasColumnSchema id : prepared.trackIdSchemas()) {
            Column idColumn = TrackNodeSupport.column(grouped, id.name());
            projection.add(idColumn);
            dwellIdParts.add(idColumn.cast("string"));
        }
        dwellIdParts.add(TrackNodeSupport.column(grouped, prepared.segmentColumnName()).cast("string"));
        dwellIdParts.add(TrackNodeSupport.column(grouped, dwellSegment).cast("string"));
        projection.add(functions.sha2(functions.concat_ws("|", dwellIdParts.toArray(Column[]::new)), 256)
                .alias(configuration.dwellIdColumnName()));
        projection.add(TrackNodeSupport.column(grouped, configuration.startTimeColumnName()));
        projection.add(TrackNodeSupport.column(grouped, configuration.endTimeColumnName()));
        projection.add(durationMillis.divide(functions.lit(
                TrackNodeSupport.millisPerUnit(configuration.minimumDurationUnit())))
                .alias(configuration.durationColumnName()));
        projection.add(TrackNodeSupport.column(grouped, countColumn).alias(configuration.pointCountColumnName()));
        for (TrackNodeSupport.ResolvedSummary summary : summaries) {
            projection.add(TrackNodeSupport.column(grouped, summary.statistic().outputColumnName()));
        }
        projection.add(geometry.alias(configuration.outputGeometryColumnName()));
        Dataset<Row> result = grouped.select(projection.toArray(Column[]::new));

        List<CanvasColumnSchema> outputColumns = new ArrayList<>();
        for (CanvasColumnSchema id : prepared.trackIdSchemas()) outputColumns.add(id);
        outputColumns.add(new CanvasColumnSchema(configuration.dwellIdColumnName(), PlatformDataType.STRING,
                64, null, null, false, null, false, false, null, null));
        outputColumns.add(TrackNodeSupport.timestampColumn(configuration.startTimeColumnName(), false));
        outputColumns.add(TrackNodeSupport.timestampColumn(configuration.endTimeColumnName(), false));
        outputColumns.add(TrackNodeSupport.doubleColumn(configuration.durationColumnName(), false));
        outputColumns.add(TrackNodeSupport.longColumn(configuration.pointCountColumnName(), false));
        for (TrackNodeSupport.ResolvedSummary summary : summaries) outputColumns.add(TrackNodeSupport.summarySchema(summary));
        outputColumns.add(new CanvasColumnSchema(
                configuration.outputGeometryColumnName(), PlatformDataType.GEOMETRY,
                null, null, null, false, null, false, false, null,
                new GeometryTypeDefinition(
                        configuration.outputGeometryKind() == DwellGeometryKind.CENTROID
                                ? GeometryKind.POINT : GeometryKind.GEOMETRY,
                        pointType.crs(), pointType.dimension())));
        outputColumns = cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper.fromStructType(result.schema(), outputColumns);
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(), null, outputColumns,
                CanvasDatasetKind.BOUNDED, null, null);
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, result));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateConfiguration(
            TrackFindDwellConfiguration configuration,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeIssueSink issues
    ) {
        CanvasNodeSupport.required(configuration.sourceTableName(), "请选择来源表", "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(configuration.pointGeometryColumnName(), "请选择点 Geometry 字段",
                "configuration.pointGeometryColumnName", issues);
        CanvasNodeSupport.required(configuration.timeColumnName(), "请选择时间字段", "configuration.timeColumnName", issues);
        CanvasNodeSupport.required(configuration.outputTableName(), "请输入输出表名", "configuration.outputTableName", issues);
        CanvasNodeSupport.required(configuration.dwellIdColumnName(), "请输入驻留 ID 字段名", "configuration.dwellIdColumnName", issues);
        CanvasNodeSupport.required(configuration.startTimeColumnName(), "请输入开始时间字段名", "configuration.startTimeColumnName", issues);
        CanvasNodeSupport.required(configuration.endTimeColumnName(), "请输入结束时间字段名", "configuration.endTimeColumnName", issues);
        CanvasNodeSupport.required(configuration.durationColumnName(), "请输入持续时间字段名", "configuration.durationColumnName", issues);
        CanvasNodeSupport.required(configuration.pointCountColumnName(), "请输入点数字段名", "configuration.pointCountColumnName", issues);
        CanvasNodeSupport.required(configuration.outputGeometryColumnName(), "请输入驻留 Geometry 字段名",
                "configuration.outputGeometryColumnName", issues);
        if (!Double.isFinite(configuration.distanceThreshold()) || configuration.distanceThreshold() <= 0
                || configuration.distanceThresholdUnit() == null) {
            issues.error("INVALID_DWELL_DISTANCE_THRESHOLD", "驻留空间半径必须是带单位的有限正数",
                    "configuration.distanceThreshold");
        }
        if (!Double.isFinite(configuration.minimumDuration()) || configuration.minimumDuration() <= 0
                || configuration.minimumDurationUnit() == null) {
            issues.error("INVALID_DWELL_DURATION", "最短持续时间必须是带单位的有限正数",
                    "configuration.minimumDuration");
        }
        if (configuration.outputGeometryKind() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择驻留位置 Geometry 类型", "configuration.outputGeometryKind");
        }
        if (!CanvasNodeSupport.blank(configuration.outputTableName()) && inputs.containsKey(configuration.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName");
        }
    }

    private static void validateOutputNames(
            TrackFindDwellConfiguration configuration,
            TrackNodeSupport.PreparedTrack prepared,
            List<TrackNodeSupport.ResolvedSummary> summaries,
            CanvasNodeIssueSink issues
    ) {
        if (prepared == null) return;
        Set<String> names = new HashSet<>();
        for (CanvasColumnSchema id : prepared.trackIdSchemas()) names.add(id.name().toLowerCase(Locale.ROOT));
        List<String> configured = new ArrayList<>(List.of(
                configuration.dwellIdColumnName(), configuration.startTimeColumnName(),
                configuration.endTimeColumnName(), configuration.durationColumnName(),
                configuration.pointCountColumnName(), configuration.outputGeometryColumnName()));
        for (TrackNodeSupport.ResolvedSummary summary : summaries) configured.add(summary.statistic().outputColumnName());
        for (String name : configured) {
            if (!CanvasNodeSupport.blank(name) && !names.add(name.toLowerCase(Locale.ROOT))) {
                issues.error("DUPLICATE_COLUMN_NAME", "驻留输出字段名重复：" + name, "configuration");
            }
        }
    }
}
