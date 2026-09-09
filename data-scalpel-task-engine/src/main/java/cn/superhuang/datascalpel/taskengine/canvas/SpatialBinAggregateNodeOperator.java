package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SpatialBinAggregateConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialBinAggregateNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialBinShape;
import cn.superhuang.data.scalpel.contract.task.SpatialBinSizeSemantics;
import cn.superhuang.data.scalpel.contract.task.SpatialBinStatistic;
import cn.superhuang.data.scalpel.contract.task.SpatialBinStatisticKind;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupSummary;
import cn.superhuang.data.scalpel.contract.task.SpatialPlanarGridOptions;
import cn.superhuang.data.scalpel.contract.task.SpatialTemporalSlicing;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.RelationalGroupedDataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.api.java.UDF1;
import org.locationtech.jts.geom.Geometry;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_constructors;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SpatialBinAggregateNodeOperator implements CanvasNodeOperator {

    private static final String BIN_X = "__datascalpel_bin_x";
    private static final String BIN_Y = "__datascalpel_bin_y";
    private static final String BIN_ID = "__datascalpel_bin_id";
    private static final String BIN_GEOMETRY = "__datascalpel_bin_geometry";
    private static final String WINDOW_START = "__datascalpel_bin_window_start";
    private static final String WINDOW_END = "__datascalpel_bin_window_end";
    private static final String GROUP_VALUE = "__datascalpel_bin_group_value";
    private static final String MATCHED = "__datascalpel_bin_matched";
    private static final String GROUP_COUNT = "__datascalpel_bin_group_count";
    private static final String MINORITY = "__datascalpel_bin_minority";
    private static final String MAJORITY = "__datascalpel_bin_majority";
    private static final String PERCENTAGE = "__datascalpel_bin_percentage";

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_BIN_AGGREGATE;
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
        if (!(definition instanceof SpatialBinAggregateNodeDefinition node)) {
            throw new IllegalArgumentException("SPATIAL_BIN_AGGREGATE operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        SpatialBinAggregateConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(inputSchemas);
        CanvasNodeIssueSink issues = context.issues();
        validateBase(configuration, inputs, issues);
        SparkCanvasTable source = CanvasNodeSupport.blank(configuration.sourceTableName()) ? null : inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error("TABLE_NOT_FOUND", "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName");
        }
        if (source == null) return CanvasNodeOperationResult.invalid(inputSchemas);
        Map<String, CanvasColumnSchema> columns = CanvasNodeSupport.columns(source.schema());
        CanvasColumnSchema point = validatePoint(source, configuration, columns, issues);
        List<ResolvedStatistic> statistics = validateStatistics(configuration.statistics(), columns, issues);
        CanvasColumnSchema groupColumn = validateGroup(configuration.groupSummary(), columns, issues);
        SpatialTemporalSupport.WindowParameters window = validateTemporal(
                configuration.temporalSlicing(), columns, issues);
        validateOutputNames(configuration, issues);
        boolean h3 = configuration.binShape() == SpatialBinShape.H3;
        if (!h3) PlanarGridSupport.validate(configuration.planarGrid(), issues);
        Integer h3Resolution = h3 ? H3GridSupport.resolve(configuration, issues) : null;
        Map<String, String> sourceNames = new LinkedHashMap<>();
        for (var column : source.schema().columns()) sourceNames.put(column.name(), "__datascalpel_bin_source_" + sourceNames.size());
        SpatialDistanceSupport.Resolution size = h3 || point == null || point.geometry() == null ? null : SpatialDistanceSupport.resolve(
                configuration.binSize(), configuration.binSizeUnit(), point.geometry().crs());
        if (size != null && (!size.valid() || size.angular())) {
            issues.error("SPATIAL_BIN_PROJECTED_CRS_REQUIRED",
                    size.valid() ? "格网聚合需要投影 CRS" : size.error(), "configuration.binSizeUnit");
        }
        if (h3 && configuration.includeEmptyBins()) {
            issues.error("SPATIAL_H3_EMPTY_BINS_UNSUPPORTED", "H3 当前只输出有观测点的格网；不能用平面索引包络补齐球面空格网", "configuration.includeEmptyBins");
        } else if (configuration.includeEmptyBins()) {
            issues.warning("SPATIAL_EMPTY_BINS_MAY_EXPAND_RESULT",
                    "输出空格网可能显著扩大结果量", "configuration.includeEmptyBins");
        }
        if (issues.hasErrors() || point == null || (h3 ? h3Resolution == null : size == null)) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        double sideLength = h3 ? 1d : configuration.binShape() == SpatialBinShape.HEXAGON
                && configuration.effectiveBinSizeSemantics() == SpatialBinSizeSemantics.HEXAGON_FLAT_TO_FLAT
                ? size.sourceCrsValue() / Math.sqrt(3d) : size.sourceCrsValue();
        if (!Double.isFinite(sideLength) || sideLength <= 0) {
            issues.error("INVALID_SPATIAL_BIN_SIZE", "换算后的格网边长超出可计算范围", "configuration.binSize");
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        PlanarGridSupport.Bounds explicitBounds = h3 ? null : PlanarGridSupport.bounds(configuration.planarGrid(),
                configuration.binShape(), sideLength, configuration.includeEmptyBins(), issues);
        if (issues.hasErrors()) return CanvasNodeOperationResult.invalid(inputSchemas);
        Dataset<Row> prepared = h3 ? prepareH3Points(source.dataset(), configuration, h3Resolution, window, sourceNames)
                : preparePoints(source.dataset(), configuration, sideLength, point.geometry().crs().code(), window, sourceNames);
        List<Column> occupiedScopeColumns = h3 ? new ArrayList<>(List.of(prepared.col(BIN_ID))) : new ArrayList<>(List.of(
                prepared.col(BIN_X), prepared.col(BIN_Y), prepared.col(BIN_ID), prepared.col(BIN_GEOMETRY)));
        if (configuration.temporalSlicing() != null) {
            occupiedScopeColumns.add(prepared.col(WINDOW_START));
            occupiedScopeColumns.add(prepared.col(WINDOW_END));
        }
        Dataset<Row> scope = configuration.includeEmptyBins()
                ? buildCompleteScope(
                        prepared, configuration, sideLength, point.geometry().crs().code(), explicitBounds)
                // An explicit aggregate exposes the cell/window grouping to the lineage analyzer.
                // It is equivalent to distinct and remains a lazy plan; the unused count is pruned.
                : prepared.groupBy(occupiedScopeColumns.toArray(Column[]::new))
                    .agg(functions.count(prepared.col(BIN_ID)).alias(GROUP_COUNT)).drop(GROUP_COUNT);
        if (h3) scope = scope.withColumn(BIN_GEOMETRY, functions.udf((UDF1<String, Geometry>) H3GridSupport::boundary,
                source.dataset().schema().apply(configuration.pointGeometryColumnName()).dataType()).apply(scope.col(BIN_ID)));
        if (configuration.includeEmptyBins() && configuration.temporalSlicing() != null) {
            Dataset<Row> windows = prepared.groupBy(prepared.col(WINDOW_START), prepared.col(WINDOW_END))
                    .agg(functions.count(prepared.col(WINDOW_START)).alias(GROUP_COUNT)).drop(GROUP_COUNT);
            scope = scope.crossJoin(windows);
        }
        Dataset<Row> points = prepared.alias("bin_points");
        Dataset<Row> bins = scope.alias("bin_scope");
        Column join = qualified("bin_scope", BIN_ID).equalTo(qualified("bin_points", BIN_ID));
        if (configuration.temporalSlicing() != null) {
            join = join.and(qualified("bin_scope", WINDOW_START).equalTo(qualified("bin_points", WINDOW_START)))
                    .and(qualified("bin_scope", WINDOW_END).equalTo(qualified("bin_points", WINDOW_END)));
        }
        Dataset<Row> joined = bins.join(points, join,
                configuration.includeEmptyBins() ? "left_outer" : "inner");

        List<Column> projection = new ArrayList<>();
        projection.add(qualified("bin_scope", BIN_ID));
        projection.add(qualified("bin_scope", BIN_GEOMETRY));
        if (configuration.temporalSlicing() != null) {
            projection.add(qualified("bin_scope", WINDOW_START));
            projection.add(qualified("bin_scope", WINDOW_END));
        }
        if (groupColumn != null) {
            projection.add(qualified("bin_points", sourceNames.get(groupColumn.name())).alias(GROUP_VALUE));
        }
        projection.add(qualified("bin_points", sourceNames.get(configuration.pointGeometryColumnName()))
                .isNotNull().alias(MATCHED));
        for (int index = 0; index < statistics.size(); index++) {
            ResolvedStatistic statistic = statistics.get(index);
            Column value = statistic.statistic().kind() == SpatialBinStatisticKind.COUNT
                    ? functions.lit(1L)
                    : qualified("bin_points", sourceNames.get(statistic.source().name()));
            projection.add(value.alias(statisticColumn(index)));
        }
        Dataset<Row> projected = joined.select(projection.toArray(Column[]::new));

        List<Column> groups = new ArrayList<>();
        groups.add(projected.col(BIN_ID));
        groups.add(projected.col(BIN_GEOMETRY));
        if (configuration.temporalSlicing() != null) {
            groups.add(projected.col(WINDOW_START));
            groups.add(projected.col(WINDOW_END));
        }
        if (groupColumn != null) groups.add(projected.col(GROUP_VALUE));
        List<Column> aggregations = new ArrayList<>();
        aggregations.add(functions.sum(functions.when(projected.col(MATCHED), 1L).otherwise(0L))
                .alias(GROUP_COUNT));
        for (int index = 0; index < statistics.size(); index++) {
            ResolvedStatistic statistic = statistics.get(index);
            aggregations.add(aggregate(projected.col(statisticColumn(index)), projected.col(MATCHED),
                    statistic.statistic().kind()).alias(statisticColumn(index)));
        }
        RelationalGroupedDataset grouped = projected.groupBy(groups.toArray(Column[]::new));
        Dataset<Row> aggregate = grouped.agg(
                aggregations.getFirst(), aggregations.subList(1, aggregations.size()).toArray(Column[]::new));
        aggregate = applyGroupIndicators(aggregate, configuration);

        List<Column> resultColumns = new ArrayList<>();
        resultColumns.add(aggregate.col(BIN_ID).alias(configuration.binIdColumnName()));
        resultColumns.add(aggregate.col(BIN_GEOMETRY).alias(configuration.binGeometryColumnName()));
        SpatialTemporalSlicing temporal = configuration.temporalSlicing();
        if (temporal != null) {
            resultColumns.add(aggregate.col(WINDOW_START).alias(temporal.windowStartColumnName()));
            resultColumns.add(aggregate.col(WINDOW_END).alias(temporal.windowEndColumnName()));
        }
        SpatialGroupSummary group = configuration.groupSummary();
        if (group != null) resultColumns.add(aggregate.col(GROUP_VALUE).alias(group.groupByColumnName()));
        if (group != null && group.includeMinorityMajority()) {
            resultColumns.add(aggregate.col(MINORITY).alias(group.minorityFlagColumnName()));
            resultColumns.add(aggregate.col(MAJORITY).alias(group.majorityFlagColumnName()));
        }
        if (group != null && group.includeGroupPercentage()) {
            resultColumns.add(aggregate.col(PERCENTAGE).alias(group.groupPercentageColumnName()));
        }
        for (int index = 0; index < statistics.size(); index++) {
            resultColumns.add(aggregate.col(statisticColumn(index)).alias(statistics.get(index).statistic().outputColumnName()));
        }
        Dataset<Row> result = aggregate.select(resultColumns.toArray(Column[]::new));

        List<CanvasColumnSchema> outputColumns = outputSchema(configuration, point, groupColumn, statistics, h3Resolution);
        // SUM(INT) is BIGINT and Decimal aggregates widen their precision. Report the analyzed
        // types instead of copying the input type or declaring every mean as DOUBLE.
        outputColumns = SparkTypeMapper.fromStructType(result.schema(), outputColumns);
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(), null, outputColumns, CanvasDatasetKind.BOUNDED, null, null);
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, result));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static Dataset<Row> prepareH3Points(Dataset<Row> source, SpatialBinAggregateConfiguration c, int resolution,
            SpatialTemporalSupport.WindowParameters window, Map<String, String> sourceNames) {
        List<Column> columns = new ArrayList<>();
        sourceNames.forEach((name, alias) -> columns.add(source.col(CanvasNodeSupport.quoteIdentifier(name)).alias(alias)));
        columns.add(functions.udf((UDF1<Geometry, String>) geometry -> H3GridSupport.cell(geometry, resolution),
                org.apache.spark.sql.types.DataTypes.StringType).apply(source.col(CanvasNodeSupport.quoteIdentifier(c.pointGeometryColumnName()))).alias(BIN_ID));
        Dataset<Row> prepared = source.select(columns.toArray(Column[]::new)).filter(functions.col(BIN_ID).isNotNull());
        return c.temporalSlicing() == null || window == null ? prepared : SpatialTemporalSupport.addWindows(prepared,
                prepared.col(sourceNames.get(c.temporalSlicing().timeColumnName())), window, WINDOW_START, WINDOW_END);
    }

    private static Dataset<Row> preparePoints(
            Dataset<Row> source,
            SpatialBinAggregateConfiguration configuration,
            double size,
            int srid,
            SpatialTemporalSupport.WindowParameters window,
            Map<String, String> sourceNames
    ) {
        Column geometry = source.col(CanvasNodeSupport.quoteIdentifier(configuration.pointGeometryColumnName()));
        SpatialPlanarGridOptions grid = configuration.planarGrid();
        if (grid != null) source = source.filter(geometry.isNotNull().and(st_functions.ST_IsEmpty(geometry).equalTo(false)));
        Column physicalX = st_functions.ST_X(geometry);
        Column physicalY = st_functions.ST_Y(geometry);
        if (grid != null && grid.usesExplicitBounds()) {
            var extent = grid.extent();
            source = source.filter(physicalX.geq(extent.minX()).and(physicalX.lt(extent.maxX()))
                    .and(physicalY.geq(extent.minY())).and(physicalY.lt(extent.maxY())));
        }
        Column x = physicalX.minus(PlanarGridSupport.originX(grid));
        Column y = physicalY.minus(PlanarGridSupport.originY(grid));
        if (grid != null) {
            x = PlanarGridSupport.checkedCoordinate(x, size);
            y = PlanarGridSupport.checkedCoordinate(y, size);
        }
        var cell = PlanarGridSupport.pointIndices(x, y, size, configuration.binShape());
        Column binX = cell.q(), binY = cell.r();
        List<Column> projection = new ArrayList<>();
        for (var name : sourceNames.entrySet())
            projection.add(source.col(CanvasNodeSupport.quoteIdentifier(name.getKey())).alias(name.getValue()));
        projection.add(binX.alias(BIN_X));
        projection.add(binY.alias(BIN_Y));
        Dataset<Row> result = source.select(projection.toArray(Column[]::new));
        result = result.withColumn(BIN_ID, functions.concat_ws(":",
                functions.lit(PlanarGridSupport.identity(grid, configuration.binShape(), size, srid)), result.col(BIN_X), result.col(BIN_Y)));
        result = result.withColumn(BIN_GEOMETRY, PlanarGridSupport.geometry(
                result.col(BIN_X), result.col(BIN_Y), configuration.binShape(), size, srid, grid));
        if (configuration.temporalSlicing() != null && window != null) {
            result = SpatialTemporalSupport.addWindows(result,
                    result.col(sourceNames.get(configuration.temporalSlicing().timeColumnName())), window, WINDOW_START, WINDOW_END);
        }
        return result;
    }

    private static Dataset<Row> buildCompleteScope(
            Dataset<Row> points,
            SpatialBinAggregateConfiguration configuration,
            double size,
            int srid,
            PlanarGridSupport.Bounds explicitBounds
    ) {
        Dataset<Row> ranges = points.agg(
                functions.min(points.col(BIN_X)).alias("min_x"),
                functions.max(points.col(BIN_X)).alias("max_x"),
                functions.min(points.col(BIN_Y)).alias("min_y"),
                functions.max(points.col(BIN_Y)).alias("max_y"));
        if (explicitBounds != null) {
            // Aggregate returns one row even for empty input. Only constants are projected;
            // Catalyst can prune the aggregate inputs without a separate Spark action.
            ranges = ranges.select(functions.lit(explicitBounds.minX()).alias("min_x"),
                    functions.lit(explicitBounds.maxX()).alias("max_x"),
                    functions.lit(explicitBounds.minY()).alias("min_y"),
                    functions.lit(explicitBounds.maxY()).alias("max_y"));
        } else if (configuration.planarGrid() != null) {
            Column cells = ranges.col("max_x").cast("double").minus(ranges.col("min_x")).plus(1d)
                    .multiply(ranges.col("max_y").cast("double").minus(ranges.col("min_y")).plus(1d));
            ranges = ranges.withColumn("max_x", functions.when(cells.gt(PlanarGridSupport.MAX_CANDIDATE_CELLS),
                    functions.raise_error(functions.lit("SPATIAL_GRID_CELL_LIMIT_EXCEEDED")).cast("long"))
                    .otherwise(ranges.col("max_x")));
        }
        Dataset<Row> xRows = ranges.withColumn(BIN_X,
                functions.explode(functions.sequence(ranges.col("min_x"), ranges.col("max_x"))));
        Dataset<Row> grid = xRows.withColumn(BIN_Y,
                functions.explode(functions.sequence(xRows.col("min_y"), xRows.col("max_y"))));
        Dataset<Row> scope = grid
                .withColumn(BIN_ID, functions.concat_ws(":", functions.lit(PlanarGridSupport.identity(
                                configuration.planarGrid(), configuration.binShape(), size, srid)),
                        grid.col(BIN_X), grid.col(BIN_Y)))
                .withColumn(BIN_GEOMETRY,
                        PlanarGridSupport.geometry(grid.col(BIN_X), grid.col(BIN_Y), configuration.binShape(), size, srid, configuration.planarGrid()))
                .select(BIN_X, BIN_Y, BIN_ID, BIN_GEOMETRY);
        if (explicitBounds != null && configuration.binShape() == SpatialBinShape.HEXAGON) {
            // A point on the included extent boundary keeps its assigned cell even when a
            // rounding tie puts that cell just outside the positive-area scope. Empty cells
            // touching only the boundary are still excluded. Never change membership to fill gaps.
            String members = "__datascalpel_scope_point_count";
            Dataset<Row> occupied = points.groupBy(points.col(BIN_ID))
                    .agg(functions.count(points.col(BIN_ID)).alias(members));
            scope = scope.join(occupied, BIN_ID, "left_outer");
            scope = scope.filter(st_functions.ST_Area(st_functions.ST_Intersection(scope.col(BIN_GEOMETRY),
                    PlanarGridSupport.extentGeometry(configuration.planarGrid()))).gt(0).or(scope.col(members).gt(0)))
                    .drop(members);
        }
        return scope;
    }


    private static Column qualified(String alias, String columnName) {
        return functions.col(alias + "." + CanvasNodeSupport.quoteIdentifier(columnName));
    }

    private static Dataset<Row> applyGroupIndicators(
            Dataset<Row> aggregate,
            SpatialBinAggregateConfiguration configuration
    ) {
        SpatialGroupSummary group = configuration.groupSummary();
        if (group == null || !group.includeMinorityMajority() && !group.includeGroupPercentage()) return aggregate;
        List<Column> partitions = new ArrayList<>();
        partitions.add(aggregate.col(BIN_ID));
        if (configuration.temporalSlicing() != null) {
            partitions.add(aggregate.col(WINDOW_START));
            partitions.add(aggregate.col(WINDOW_END));
        }
        WindowSpec window = Window.partitionBy(partitions.toArray(Column[]::new));
        Column count = aggregate.col(GROUP_COUNT);
        if (group.includeMinorityMajority()) {
            String min = "__datascalpel_bin_min";
            String max = "__datascalpel_bin_max";
            aggregate = aggregate.withColumn(min, functions.min(count).over(window))
                    .withColumn(max, functions.max(count).over(window));
            count = aggregate.col(GROUP_COUNT);
            aggregate = aggregate
                    .withColumn(MINORITY, count.gt(0).and(count.equalTo(aggregate.col(min))))
                    .withColumn(MAJORITY, count.gt(0).and(count.equalTo(aggregate.col(max))));
        }
        if (group.includeGroupPercentage()) {
            Column total = functions.sum(count).over(window);
            aggregate = aggregate.withColumn(PERCENTAGE,
                    functions.when(total.gt(0), count.multiply(100d).divide(total)));
        }
        return aggregate;
    }

    private static void validateBase(
            SpatialBinAggregateConfiguration configuration,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeIssueSink issues
    ) {
        CanvasNodeSupport.required(configuration.sourceTableName(), "请选择来源表", "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(configuration.pointGeometryColumnName(), "请选择点 Geometry 字段", "configuration.pointGeometryColumnName", issues);
        CanvasNodeSupport.required(configuration.outputTableName(), "请输入输出表名", "configuration.outputTableName", issues);
        CanvasNodeSupport.required(configuration.binIdColumnName(), "请输入格网 ID 字段名", "configuration.binIdColumnName", issues);
        CanvasNodeSupport.required(configuration.binGeometryColumnName(), "请输入格网 Geometry 字段名", "configuration.binGeometryColumnName", issues);
        if (configuration.binShape() == null) issues.error("REQUIRED_CONFIGURATION", "请选择格网形状", "configuration.binShape");
        if (configuration.binShape() != SpatialBinShape.H3 && (!Double.isFinite(configuration.binSize()) || configuration.binSize() <= 0 || configuration.binSizeUnit() == null)) {
            issues.error("INVALID_SPATIAL_BIN_SIZE", "格网大小必须是带单位的有限正数", "configuration.binSize");
        }
        if (!CanvasNodeSupport.blank(configuration.outputTableName()) && inputs.containsKey(configuration.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在：" + configuration.outputTableName(), "configuration.outputTableName");
        }
        if (configuration.statistics() == null || configuration.statistics().isEmpty()) {
            issues.error("SPATIAL_BIN_STATISTICS_REQUIRED", "至少配置一个格网统计项", "configuration.statistics");
        } else if (configuration.statistics().size() > SpatialBinAggregateConfiguration.MAX_STATISTICS) {
            issues.error("SPATIAL_BIN_STATISTIC_COUNT_EXCEEDED", "格网统计项不能超过 32 个", "configuration.statistics");
        } else if (configuration.statistics().stream().noneMatch(item -> item != null
                && item.kind() == SpatialBinStatisticKind.COUNT)) {
            issues.error("SPATIAL_BIN_COUNT_REQUIRED", "格网统计至少包含一个 COUNT", "configuration.statistics");
        }
    }

    private static CanvasColumnSchema validatePoint(
            SparkCanvasTable source,
            SpatialBinAggregateConfiguration configuration,
            Map<String, CanvasColumnSchema> columns,
            CanvasNodeIssueSink issues
    ) {
        if (source.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error("BOUNDED_INPUT_REQUIRED", "格网聚合只支持有界输入", "configuration.sourceTableName");
        }
        CanvasColumnSchema point = CanvasNodeSupport.blank(configuration.pointGeometryColumnName()) ? null : columns.get(configuration.pointGeometryColumnName());
        if (!CanvasNodeSupport.blank(configuration.pointGeometryColumnName()) && point == null) {
            issues.error("COLUMN_NOT_FOUND", "点 Geometry 字段不存在：" + configuration.pointGeometryColumnName(),
                    "configuration.pointGeometryColumnName");
        } else if (point != null && (point.fieldType() != PlatformDataType.GEOMETRY || point.geometry() == null
                || point.geometry().kind() != GeometryKind.POINT || point.geometry().dimension() != CoordinateDimension.XY)) {
            issues.error("SPATIAL_POINT_XY_REQUIRED", "格网聚合需要带完整元数据的 XY Point",
                    "configuration.pointGeometryColumnName");
        }
        if (configuration.binShape() == SpatialBinShape.H3 && point != null && point.geometry() != null
                && (!"EPSG".equals(point.geometry().crs().authority()) || point.geometry().crs().code() != 4326))
            issues.error("SPATIAL_H3_WGS84_REQUIRED", "H3 使用 WGS84 经纬度，请先显式转换为 EPSG:4326", "configuration.pointGeometryColumnName");
        return point;
    }

    private static List<ResolvedStatistic> validateStatistics(
            List<SpatialBinStatistic> configured,
            Map<String, CanvasColumnSchema> columns,
            CanvasNodeIssueSink issues
    ) {
        if (configured == null) return List.of();
        Set<UUID> ids = new HashSet<>();
        List<ResolvedStatistic> result = new ArrayList<>();
        for (int index = 0; index < configured.size(); index++) {
            SpatialBinStatistic statistic = configured.get(index);
            String path = "configuration.statistics[" + index + "]";
            if (statistic == null || statistic.kind() == null) {
                issues.error("INVALID_SPATIAL_BIN_STATISTIC", "格网统计项不完整", path);
                continue;
            }
            try {
                if (!ids.add(UUID.fromString(statistic.statisticId()))) {
                    issues.error("DUPLICATE_SPATIAL_BIN_STATISTIC_ID", "格网统计项 ID 重复", path + ".statisticId");
                }
            } catch (RuntimeException exception) {
                issues.error("INVALID_SPATIAL_BIN_STATISTIC_ID", "格网统计项 ID 必须是 UUID", path + ".statisticId");
            }
            CanvasNodeSupport.required(statistic.outputColumnName(), "请输入统计输出字段名", path + ".outputColumnName", issues);
            CanvasColumnSchema source = null;
            if (statistic.kind() != SpatialBinStatisticKind.COUNT) {
                CanvasNodeSupport.required(statistic.sourceColumnName(), "请选择统计来源字段", path + ".sourceColumnName", issues);
                source = CanvasNodeSupport.blank(statistic.sourceColumnName()) ? null : columns.get(statistic.sourceColumnName());
                if (!CanvasNodeSupport.blank(statistic.sourceColumnName()) && source == null) {
                    issues.error("COLUMN_NOT_FOUND", "统计字段不存在：" + statistic.sourceColumnName(), path + ".sourceColumnName");
                } else if (source != null && statistic.kind() == SpatialBinStatisticKind.ANY
                        && source.fieldType() != PlatformDataType.STRING) {
                    issues.error("STRING_COLUMN_REQUIRED", "ANY 采样要求字符串字段", path + ".sourceColumnName");
                } else if (source != null && statistic.kind() != SpatialBinStatisticKind.COUNT_FIELD
                        && statistic.kind() != SpatialBinStatisticKind.ANY && !numeric(source.fieldType())) {
                    issues.error("NUMERIC_COLUMN_REQUIRED", "当前格网统计类型要求数值字段", path + ".sourceColumnName");
                }
            } else if (statistic.sourceColumnName() != null) {
                issues.error("INVALID_SPATIAL_BIN_STATISTIC", "COUNT 不使用来源字段", path + ".sourceColumnName");
            }
            result.add(new ResolvedStatistic(statistic, source));
        }
        return List.copyOf(result);
    }

    private static CanvasColumnSchema validateGroup(
            SpatialGroupSummary group,
            Map<String, CanvasColumnSchema> columns,
            CanvasNodeIssueSink issues
    ) {
        if (group == null) return null;
        CanvasNodeSupport.required(group.groupByColumnName(), "请选择分组字段", "configuration.groupSummary.groupByColumnName", issues);
        CanvasColumnSchema column = CanvasNodeSupport.blank(group.groupByColumnName()) ? null : columns.get(group.groupByColumnName());
        if (!CanvasNodeSupport.blank(group.groupByColumnName()) && column == null) {
            issues.error("COLUMN_NOT_FOUND", "分组字段不存在：" + group.groupByColumnName(), "configuration.groupSummary.groupByColumnName");
        } else if (column != null && column.fieldType() == PlatformDataType.GEOMETRY) {
            issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED", "Geometry 不能作为分组字段", "configuration.groupSummary.groupByColumnName");
        }
        if (group.includeMinorityMajority()) {
            CanvasNodeSupport.required(group.minorityFlagColumnName(), "请输入少数组标记字段名", "configuration.groupSummary.minorityFlagColumnName", issues);
            CanvasNodeSupport.required(group.majorityFlagColumnName(), "请输入多数标记字段名", "configuration.groupSummary.majorityFlagColumnName", issues);
        }
        if (group.includeGroupPercentage()) {
            CanvasNodeSupport.required(group.groupPercentageColumnName(), "请输入组百分比字段名", "configuration.groupSummary.groupPercentageColumnName", issues);
        }
        return column;
    }

    private static SpatialTemporalSupport.WindowParameters validateTemporal(
            SpatialTemporalSlicing temporal,
            Map<String, CanvasColumnSchema> columns,
            CanvasNodeIssueSink issues
    ) {
        SpatialTemporalSupport.WindowParameters result = SpatialTemporalSupport.validateAndResolve(
                temporal, "configuration.temporalSlicing", issues);
        if (temporal != null && !CanvasNodeSupport.blank(temporal.timeColumnName())) {
            CanvasColumnSchema column = columns.get(temporal.timeColumnName());
            if (column == null) issues.error("COLUMN_NOT_FOUND", "时间字段不存在：" + temporal.timeColumnName(), "configuration.temporalSlicing.timeColumnName");
            else if (column.fieldType() != PlatformDataType.TIMESTAMP) issues.error("TIMESTAMP_COLUMN_REQUIRED", "时间切片字段必须是 TIMESTAMP", "configuration.temporalSlicing.timeColumnName");
        }
        return result;
    }

    private static void validateOutputNames(SpatialBinAggregateConfiguration configuration, CanvasNodeIssueSink issues) {
        Set<String> names = new HashSet<>();
        addName(configuration.binIdColumnName(), "configuration.binIdColumnName", names, issues);
        addName(configuration.binGeometryColumnName(), "configuration.binGeometryColumnName", names, issues);
        if (configuration.temporalSlicing() != null) {
            addName(configuration.temporalSlicing().windowStartColumnName(), "configuration.temporalSlicing.windowStartColumnName", names, issues);
            addName(configuration.temporalSlicing().windowEndColumnName(), "configuration.temporalSlicing.windowEndColumnName", names, issues);
        }
        if (configuration.groupSummary() != null) {
            SpatialGroupSummary group = configuration.groupSummary();
            addName(group.groupByColumnName(), "configuration.groupSummary.groupByColumnName", names, issues);
            if (group.includeMinorityMajority()) {
                addName(group.minorityFlagColumnName(), "configuration.groupSummary.minorityFlagColumnName", names, issues);
                addName(group.majorityFlagColumnName(), "configuration.groupSummary.majorityFlagColumnName", names, issues);
            }
            if (group.includeGroupPercentage()) addName(group.groupPercentageColumnName(), "configuration.groupSummary.groupPercentageColumnName", names, issues);
        }
        if (configuration.statistics() != null) for (SpatialBinStatistic statistic : configuration.statistics()) {
            if (statistic != null) addName(statistic.outputColumnName(), "configuration.statistics", names, issues);
        }
    }

    private static void addName(String name, String path, Set<String> names, CanvasNodeIssueSink issues) {
        if (!CanvasNodeSupport.blank(name) && !names.add(name.toLowerCase(Locale.ROOT))) {
            issues.error("DUPLICATE_COLUMN_NAME", "输出字段名重复：" + name, path);
        }
    }

    private static Column aggregate(Column source, Column matched, SpatialBinStatisticKind kind) {
        return switch (kind) {
            case COUNT -> functions.sum(functions.when(matched, 1L).otherwise(0L));
            case COUNT_FIELD -> functions.count(functions.when(matched, source));
            case ANY -> functions.first(functions.when(matched, source), true);
            case SUM -> functions.sum(source);
            case MEAN -> functions.avg(source);
            case MIN -> functions.min(source);
            case MAX -> functions.max(source);
            case RANGE -> functions.max(source).minus(functions.min(source));
            case STDDEV -> functions.stddev_samp(source);
            case VARIANCE -> functions.var_samp(source);
        };
    }

    private static List<CanvasColumnSchema> outputSchema(
            SpatialBinAggregateConfiguration configuration,
            CanvasColumnSchema point,
            CanvasColumnSchema groupColumn,
            List<ResolvedStatistic> statistics,
            Integer h3Resolution
    ) {
        List<CanvasColumnSchema> columns = new ArrayList<>();
        columns.add(new CanvasColumnSchema(configuration.binIdColumnName(), PlatformDataType.STRING,
                160, null, null, false, null, false, false, h3Resolution == null ? null
                : "H3 分辨率 " + h3Resolution + " · 估算平均对边距离 " + String.format(Locale.ROOT, "%.3f 米", H3GridSupport.averageDiameter(h3Resolution)), null));
        columns.add(new CanvasColumnSchema(configuration.binGeometryColumnName(), PlatformDataType.GEOMETRY,
                null, null, null, false, null, false, false, null,
                new GeometryTypeDefinition(configuration.binShape() == SpatialBinShape.H3 ? GeometryKind.MULTIPOLYGON : GeometryKind.POLYGON,
                        point.geometry().crs(), CoordinateDimension.XY)));
        if (configuration.temporalSlicing() != null) {
            columns.add(TrackNodeSupport.timestampColumn(configuration.temporalSlicing().windowStartColumnName(), false));
            columns.add(TrackNodeSupport.timestampColumn(configuration.temporalSlicing().windowEndColumnName(), false));
        }
        if (groupColumn != null) columns.add(TrackNodeSupport.renamed(groupColumn, groupColumn.name(), true));
        SpatialGroupSummary group = configuration.groupSummary();
        if (group != null && group.includeMinorityMajority()) {
            columns.add(TrackNodeSupport.booleanColumn(group.minorityFlagColumnName(), false));
            columns.add(TrackNodeSupport.booleanColumn(group.majorityFlagColumnName(), false));
        }
        if (group != null && group.includeGroupPercentage()) {
            columns.add(TrackNodeSupport.doubleColumn(group.groupPercentageColumnName(), true));
        }
        for (ResolvedStatistic statistic : statistics) {
            SpatialBinStatistic item = statistic.statistic();
            if (item.kind() == SpatialBinStatisticKind.COUNT || item.kind() == SpatialBinStatisticKind.COUNT_FIELD) {
                columns.add(TrackNodeSupport.longColumn(item.outputColumnName(), false));
            } else if (item.kind() == SpatialBinStatisticKind.MEAN
                    || item.kind() == SpatialBinStatisticKind.STDDEV
                    || item.kind() == SpatialBinStatisticKind.VARIANCE) {
                columns.add(TrackNodeSupport.doubleColumn(item.outputColumnName(), true));
            } else {
                columns.add(TrackNodeSupport.renamed(statistic.source(), item.outputColumnName(), true));
            }
        }
        return List.copyOf(columns);
    }

    private static boolean numeric(PlatformDataType type) {
        return type == PlatformDataType.BYTE || type == PlatformDataType.SHORT
                || type == PlatformDataType.INTEGER || type == PlatformDataType.LONG
                || type == PlatformDataType.FLOAT || type == PlatformDataType.DOUBLE
                || type == PlatformDataType.DECIMAL;
    }

    private static String statisticColumn(int index) {
        return "__datascalpel_bin_stat_" + index;
    }

    private record ResolvedStatistic(SpatialBinStatistic statistic, CanvasColumnSchema source) {
    }
}
