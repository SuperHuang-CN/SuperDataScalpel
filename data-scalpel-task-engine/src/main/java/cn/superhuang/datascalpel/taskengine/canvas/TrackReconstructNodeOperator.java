package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.TrackReconstructConfiguration;
import cn.superhuang.data.scalpel.contract.task.TrackReconstructNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TrackSummaryStatisticKind;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.api.java.UDF1;
import org.locationtech.jts.geom.Geometry;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TrackReconstructNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.TRACK_RECONSTRUCT;
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
        if (!(definition instanceof TrackReconstructNodeDefinition node)) {
            throw new IllegalArgumentException("TRACK_RECONSTRUCT operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        TrackReconstructConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(inputSchemas);
        CanvasNodeIssueSink issues = context.issues();
        requireConfiguration(configuration, inputs, issues);
        double geodesicStep = geodesicStep(configuration, issues);
        SparkCanvasTable source = CanvasNodeSupport.blank(configuration.sourceTableName()) ? null : inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error("TABLE_NOT_FOUND", "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName");
        }
        TrackReconstructPlan.validate(configuration, source, issues);
        TrackAreaPlan.validate(source, configuration, issues);
        source = TrackReconstructPlan.validObservations(source, configuration);
        TrackNodeSupport.PreparedTrack prepared = TrackNodeSupport.prepare(
                source, configuration.pointGeometryColumnName(), true,
                configuration.trackIdColumns(), configuration.timeColumnName(),
                configuration.distanceMethod(), configuration.boundaries(), issues, "configuration",
                configuration.usesOrderedReconstruction() ? configuration.reconstruction().orderByColumns() : null,
                "configuration.reconstruction.orderByColumns", configuration.usesAreaGeometry());
        Map<String, CanvasColumnSchema> sourceColumns = source == null
                ? Map.of() : CanvasNodeSupport.columns(source.schema());
        List<TrackNodeSupport.ResolvedSummary> summaries = TrackNodeSupport.validateSummaries(
                configuration.summaryStatistics(), sourceColumns, issues, "configuration.summaryStatistics", false);
        validateOutputNames(configuration, prepared, summaries, issues);
        if (issues.hasErrors() || prepared == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        TrackAreaPlan.Prepared area = TrackAreaPlan.prepare(new SparkCanvasTable(source.schema(), prepared.dataset()), configuration, issues);
        if (issues.hasErrors()) return CanvasNodeOperationResult.invalid(inputSchemas);
        prepared = new TrackNodeSupport.PreparedTrack(prepared.source(), area.source().dataset(), prepared.trackIdSchemas(),
                prepared.timeSchema(), prepared.pointSchema(), prepared.segmentColumnName(), prepared.orderedWindow());
        if (configuration.usesOrderedReconstruction()) {
            prepared = TrackReconstructPlan.segment(prepared, configuration, issues);
            if (prepared == null || issues.hasErrors()) return CanvasNodeOperationResult.invalid(inputSchemas);
        } else {
            issues.warning("TRACK_ORDER_TIE_NOT_VERIFIED",
                    "相同轨迹和相同时间的点顺序由运行数据保证", "configuration.timeColumnName");
        }

        Dataset<Row> dataset = prepared.dataset();
        Set<String> occupied = new HashSet<>();
        for (String name : dataset.columns()) occupied.add(name.toLowerCase(Locale.ROOT));
        java.util.Arrays.asList(configuration.startTimeColumnName(), configuration.endTimeColumnName(),
                configuration.outputGeometryColumnName(), configuration.pointCountColumnName())
                .forEach(name -> occupied.add(name.toLowerCase(Locale.ROOT)));
        summaries.forEach(summary -> occupied.add(summary.statistic().outputColumnName().toLowerCase(Locale.ROOT)));
        String pointsColumn = internalName(occupied, "__datascalpel_track_points");
        String countColumn = internalName(occupied, "__datascalpel_track_count");
        Column order = configuration.usesOrderedReconstruction() ? TrackReconstructPlan.order(configuration, dataset)
                : TrackNodeSupport.column(dataset, configuration.timeColumnName());
        List<Column> aggregations = new ArrayList<>();
        aggregations.add(functions.min(TrackNodeSupport.column(dataset, configuration.timeColumnName()))
                .alias(configuration.startTimeColumnName()));
        aggregations.add(functions.max(TrackNodeSupport.column(dataset, configuration.timeColumnName()))
                .alias(configuration.endTimeColumnName()));
        aggregations.add(functions.count(configuration.usesOrderedReconstruction()
                ? TrackNodeSupport.column(dataset, configuration.timeColumnName()) : functions.lit(1)).alias(countColumn));
        for (TrackNodeSupport.ResolvedSummary summary : summaries) {
            Column expression = switch (summary.statistic().kind()) {
                case COUNT -> functions.count(configuration.usesOrderedReconstruction()
                        ? TrackNodeSupport.column(dataset, configuration.timeColumnName()) : functions.lit(1));
                case FIRST -> functions.min_by(
                        TrackNodeSupport.column(dataset, summary.source().name()),
                        order);
                case LAST -> functions.max_by(
                        TrackNodeSupport.column(dataset, summary.source().name()),
                        order);
                default -> TrackNodeSupport.summaryExpression(summary, dataset);
            };
            aggregations.add(expression.alias(summary.statistic().outputColumnName()));
        }
        aggregations.add(functions.collect_list(
                functions.struct(
                        order.alias("time"),
                        TrackNodeSupport.column(dataset, configuration.usesAreaGeometry()
                                ? area.footprintColumn() : configuration.pointGeometryColumnName()).alias("point")))
                .alias(pointsColumn));
        List<Column> groups = prepared.groupColumns();
        Dataset<Row> grouped = dataset.groupBy(groups.toArray(Column[]::new))
                .agg(aggregations.getFirst(), aggregations.subList(1, aggregations.size()).toArray(Column[]::new));
        if (configuration.usesOrderedReconstruction() && !configuration.usesAreaGeometry())
            grouped = grouped.filter(TrackNodeSupport.column(grouped, countColumn).gt(1));
        Column orderedPoints = functions.transform(
                functions.array_sort(
                        TrackNodeSupport.column(grouped, pointsColumn),
                        (left, right) -> functions.when(
                                        left.getField("time").lt(right.getField("time")), functions.lit(-1))
                                .when(left.getField("time").gt(right.getField("time")), functions.lit(1))
                                .otherwise(functions.lit(0))),
                item -> item.getField("point"));
        GeometryTypeDefinition sourceGeometry = prepared.pointSchema().geometry();
        Column line = configuration.usesAreaGeometry()
                ? st_functions.ST_SetSRID(TrackAreaPlan.connect(orderedPoints,
                        source.dataset().schema().apply(configuration.pointGeometryColumnName()).dataType(), configuration, issues), functions.lit(sourceGeometry.crs().code()))
                : st_functions.ST_SetSRID(st_functions.ST_MakeLine(orderedPoints), functions.lit(sourceGeometry.crs().code()));
        if (configuration.usesMethodPath() && !configuration.usesAreaGeometry()) {
            line = configuration.distanceMethod() == SpatialDistanceMethod.GEODESIC
                    ? functions.udf((UDF1<Geometry, Geometry>) geometry -> TrackGeodesicPath.build(geometry, geodesicStep),
                            source.dataset().schema().apply(configuration.pointGeometryColumnName()).dataType()).apply(line)
                    : st_functions.ST_Multi(line);
        }
        Column checkedLine = (configuration.usesAreaGeometry() ? line : functions.when(
                TrackNodeSupport.column(grouped, countColumn).lt(2),
                functions.raise_error(functions.lit("TRACK_SEGMENT_REQUIRES_TWO_POINTS")))
                .otherwise(line))
                .alias(configuration.outputGeometryColumnName());
        List<Column> projection = new ArrayList<>();
        for (CanvasColumnSchema id : prepared.trackIdSchemas()) {
            projection.add(TrackNodeSupport.column(grouped, id.name()));
        }
        projection.add(TrackNodeSupport.column(grouped, configuration.startTimeColumnName()));
        projection.add(TrackNodeSupport.column(grouped, configuration.endTimeColumnName()));
        projection.add(TrackNodeSupport.column(grouped, countColumn).alias(configuration.pointCountColumnName()));
        for (TrackNodeSupport.ResolvedSummary summary : summaries) {
            projection.add(TrackNodeSupport.column(grouped, summary.statistic().outputColumnName()));
        }
        projection.add(checkedLine);
        Dataset<Row> result = grouped.select(projection.toArray(Column[]::new));
        result.schema();

        List<CanvasColumnSchema> outputColumns = new ArrayList<>();
        for (CanvasColumnSchema id : prepared.trackIdSchemas()) {
            outputColumns.add(TrackNodeSupport.renamed(id, id.name(), id.nullable()));
        }
        outputColumns.add(TrackNodeSupport.timestampColumn(configuration.startTimeColumnName(), false));
        outputColumns.add(TrackNodeSupport.timestampColumn(configuration.endTimeColumnName(), false));
        outputColumns.add(TrackNodeSupport.longColumn(configuration.pointCountColumnName(), false));
        for (TrackNodeSupport.ResolvedSummary summary : summaries) {
            outputColumns.add(TrackNodeSupport.summarySchema(summary));
        }
        outputColumns.add(new CanvasColumnSchema(
                configuration.outputGeometryColumnName(), PlatformDataType.GEOMETRY,
                null, null, null, false, null, false, false, null,
                new GeometryTypeDefinition(configuration.usesAreaGeometry() ? GeometryKind.MULTIPOLYGON
                        : configuration.usesMethodPath() ? GeometryKind.MULTILINESTRING : GeometryKind.LINESTRING,
                        sourceGeometry.crs(), sourceGeometry.dimension())));
        outputColumns = cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper.fromStructType(result.schema(), outputColumns);
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(), null, outputColumns,
                CanvasDatasetKind.BOUNDED, null, null);
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, result));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static double geodesicStep(TrackReconstructConfiguration c, CanvasNodeIssueSink issues) {
        if (!c.usesMethodPath() || c.usesAreaGeometry() || c.distanceMethod() != SpatialDistanceMethod.GEODESIC) return Double.NaN;
        var options = c.reconstruction().pathGeometry();
        String path = "configuration.reconstruction.pathGeometry";
        Double length = options.maximumGeodesicSegmentLength();
        double factor = SpatialDistanceSupport.metresPerConfiguredUnit(options.maximumGeodesicSegmentLengthUnit());
        if (!Double.isFinite(factor)) issues.error("INVALID_TRACK_GEODESIC_SEGMENT_LENGTH",
                "测地加密必须选择线性距离单位，不能使用经纬度单位", path + ".maximumGeodesicSegmentLengthUnit");
        if (length == null || !Double.isFinite(length) || length <= 0
                || (Double.isFinite(factor) && (!Double.isFinite(length * factor) || length * factor <= 0))) {
            issues.error("INVALID_TRACK_GEODESIC_SEGMENT_LENGTH", "测地加密最大段长必须为有限正数", path + ".maximumGeodesicSegmentLength");
        }
        return length == null ? Double.NaN : length * factor;
    }

    private static void requireConfiguration(
            TrackReconstructConfiguration configuration,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeIssueSink issues
    ) {
        CanvasNodeSupport.required(configuration.sourceTableName(), "请选择来源表",
                "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(configuration.pointGeometryColumnName(), "请选择观测 Geometry 字段",
                "configuration.pointGeometryColumnName", issues);
        CanvasNodeSupport.required(configuration.timeColumnName(), "请选择时间字段",
                "configuration.timeColumnName", issues);
        CanvasNodeSupport.required(configuration.outputTableName(), "请输入输出表名",
                "configuration.outputTableName", issues);
        CanvasNodeSupport.required(configuration.outputGeometryColumnName(), "请输入轨迹 Geometry 字段名",
                "configuration.outputGeometryColumnName", issues);
        CanvasNodeSupport.required(configuration.startTimeColumnName(), "请输入开始时间字段名",
                "configuration.startTimeColumnName", issues);
        CanvasNodeSupport.required(configuration.endTimeColumnName(), "请输入结束时间字段名",
                "configuration.endTimeColumnName", issues);
        CanvasNodeSupport.required(configuration.pointCountColumnName(), "请输入点数字段名",
                "configuration.pointCountColumnName", issues);
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName");
        }
    }

    private static void validateOutputNames(
            TrackReconstructConfiguration configuration,
            TrackNodeSupport.PreparedTrack prepared,
            List<TrackNodeSupport.ResolvedSummary> summaries,
            CanvasNodeIssueSink issues
    ) {
        if (prepared == null) return;
        Set<String> names = new HashSet<>();
        for (CanvasColumnSchema id : prepared.trackIdSchemas()) names.add(id.name().toLowerCase(Locale.ROOT));
        List<String> configured = new ArrayList<>(java.util.Arrays.asList(
                configuration.startTimeColumnName(), configuration.endTimeColumnName(),
                configuration.pointCountColumnName(), configuration.outputGeometryColumnName()));
        for (TrackNodeSupport.ResolvedSummary summary : summaries) {
            configured.add(summary.statistic().outputColumnName());
        }
        for (String name : configured) {
            if (!CanvasNodeSupport.blank(name) && !names.add(name.toLowerCase(Locale.ROOT))) {
                issues.error("DUPLICATE_COLUMN_NAME", "轨迹输出字段名重复：" + name, "configuration");
            }
        }
    }

    private static String internalName(Set<String> occupied, String base) {
        while (!occupied.add(base.toLowerCase(Locale.ROOT))) base += "_";
        return base;
    }
}
