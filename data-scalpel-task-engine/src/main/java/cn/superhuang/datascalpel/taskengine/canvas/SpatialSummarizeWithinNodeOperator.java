package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.JoinOutputColumnSource;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupSummary;
import cn.superhuang.data.scalpel.contract.task.SpatialSummarizeWithinConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialSummarizeWithinNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialTemporalSlicing;
import cn.superhuang.data.scalpel.contract.task.SpatialWithinStatistic;
import cn.superhuang.data.scalpel.contract.task.SpatialWithinStatisticKind;
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
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.apache.spark.sql.sedona_sql.expressions.st_predicates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SpatialSummarizeWithinNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_SUMMARIZE_WITHIN;
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
        if (!(definition instanceof SpatialSummarizeWithinNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "SPATIAL_SUMMARIZE_WITHIN operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        SpatialSummarizeWithinConfiguration original = node.configuration();
        if (original == null) return CanvasNodeOperationResult.invalid(inputSchemas);
        CanvasNodeIssueSink issues = context.issues();
        if (original.regions() != null && original.regions().mode() == null)
            issues.error("INVALID_SPATIAL_WITHIN_GRID", "请选择区域表或规则格网", "configuration.regions.mode");
        CanvasNodeSupport.required(original.summaryTableName(), "请选择被汇总表", "configuration.summaryTableName", issues);
        SparkCanvasTable summaries = original.summaryTableName() == null ? null : inputs.get(original.summaryTableName());
        if (!CanvasNodeSupport.blank(original.summaryTableName()) && summaries == null)
            issues.error("TABLE_NOT_FOUND", "被汇总表不在上游数据中", "configuration.summaryTableName");
        WithinGridSupport.Plan grid = original.usesGridRegions() && summaries != null ? WithinGridSupport.prepare(original, summaries, issues) : null;
        if (original.usesGridRegions() && grid == null) return CanvasNodeOperationResult.invalid(inputSchemas);
        if (grid != null) summaries = grid.summaries();
        SpatialSummarizeWithinConfiguration configuration = grid == null ? original : grid.configuration();
        validateConfiguration(configuration, inputs, issues);
        SparkCanvasTable areas = grid != null ? grid.areas() : configuration.areaTableName() == null ? null : inputs.get(configuration.areaTableName());
        if (!original.usesGridRegions() && !CanvasNodeSupport.blank(configuration.areaTableName()) && areas == null) {
            issues.error("TABLE_NOT_FOUND", "区域表不在上游数据中：" + configuration.areaTableName(),
                    "configuration.areaTableName");
        }
        if (areas == null || summaries == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        if (areas.schema().datasetKind() != CanvasDatasetKind.BOUNDED
                || summaries.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error("BOUNDED_INPUT_REQUIRED", "区域内汇总只支持有界输入", "configuration");
        }

        Map<String, CanvasColumnSchema> areaColumns = CanvasNodeSupport.columns(areas.schema());
        Map<String, CanvasColumnSchema> summaryColumns = CanvasNodeSupport.columns(summaries.schema());
        CanvasColumnSchema areaGeometryColumn = geometryColumn(
                configuration.areaGeometryColumnName(), areaColumns, "区域",
                "configuration.areaGeometryColumnName", issues);
        CanvasColumnSchema summaryGeometryColumn = geometryColumn(
                configuration.summaryGeometryColumnName(), summaryColumns, "被汇总",
                "configuration.summaryGeometryColumnName", issues);
        GeometryTypeDefinition areaGeometry = areaGeometryColumn == null
                ? null : areaGeometryColumn.geometry();
        GeometryTypeDefinition summaryGeometry = summaryGeometryColumn == null
                ? null : summaryGeometryColumn.geometry();
        validateGeometryPair(configuration, areaGeometry, summaryGeometry, issues);

        List<JoinOutputColumnSupport.ResolvedOutputColumn> areaOutputs =
                JoinOutputColumnSupport.validate(
                        configuration.areaOutputColumns(), areaColumns, Map.of(), issues);
        if (configuration.areaOutputColumns() != null
                && configuration.areaOutputColumns().stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(item -> item.sourceSide() != JoinOutputColumnSource.LEFT)) {
            issues.error("SPATIAL_SUMMARIZE_AREA_OUTPUT_ONLY",
                    "区域输出字段只能来自区域表", "configuration.areaOutputColumns");
        }
        validateStatistics(configuration, summaryColumns, issues);
        if (configuration.statistics() != null && summaryGeometry != null) {
            for (int index = 0; index < configuration.statistics().size(); index++) {
                SpatialWithinStatistic statistic = configuration.statistics().get(index);
                if (statistic != null && statistic.kind() != null) {
                    WithinStatisticSupport.validate(statistic, summaryGeometry.kind(),
                            "configuration.statistics[" + index + "]", issues);
                }
            }
        }
        validateGroup(configuration.groupSummary(), summaryColumns, configuration.usesLinkedGroupResult(), issues);
        SpatialTemporalSupport.WindowParameters window = validateTemporal(
                configuration.temporalSlicing(), summaryColumns, issues);
        validateOutputNames(configuration, areaOutputs, issues);
        WithinGroupResultPlan.validate(configuration, inputs, areaColumns,
                summaryGeometry == null ? null : summaryGeometry.kind(), areaOutputs, issues);
        MeasurementFactors factors = areaGeometry == null || configuration.distanceMethod() == null
                || configuration.lengthUnit() == null || configuration.areaUnit() == null
                ? null : resolveMeasurementFactors(configuration, areaGeometry, issues);
        if (issues.hasErrors() || factors == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        String prefix = internalPrefix(configuration, areas, summaries);
        String AREA_ROW_ID = prefix + "area_row_id";
        String MATCHED = prefix + "matched";
        String GROUP_COUNT = prefix + "group_count";
        String WINDOW_START = prefix + "window_start";
        String WINDOW_END = prefix + "window_end";
        String GROUP_VALUE = prefix + "group_value";
        String GROUP_MINIMUM = prefix + "group_minimum";
        String GROUP_MAXIMUM = prefix + "group_maximum";
        String GROUP_MINIMUM_TIES = prefix + "group_minimum_ties";
        String GROUP_MAXIMUM_TIES = prefix + "group_maximum_ties";
        String fractionName = prefix + "fraction";
        Dataset<Row> areaBase = (configuration.usesLinkedGroupResult()
                ? WithinGroupResultPlan.withAreaKey(areas.dataset(), configuration.groupResult(), AREA_ROW_ID)
                : areas.dataset().withColumn(AREA_ROW_ID, grid != null ? areas.dataset().col(WithinGridSupport.CELL_ID) : functions.monotonically_increasing_id()))
                .alias("within_area");
        Dataset<Row> summaryBase = summaries.dataset().alias("within_summary");
        SpatialTemporalSlicing temporal = configuration.temporalSlicing();
        if (temporal != null && window != null) {
            summaryBase = SpatialTemporalSupport.addWindows(summaryBase,
                    summaryBase.col(CanvasNodeSupport.quoteIdentifier(temporal.timeColumnName())), window, WINDOW_START, WINDOW_END)
                    .alias("within_summary");
        }

        Dataset<Row> areaScope = areaBase;
        if (configuration.includeEmptyAreas() && temporal != null) {
            Dataset<Row> windows = summaryBase.groupBy(
                    summaryBase.col(WINDOW_START), summaryBase.col(WINDOW_END))
                    .agg(functions.count(summaryBase.col(WINDOW_START)).alias("window_count"))
                    .drop("window_count");
            areaScope = areaBase.crossJoin(windows).alias("within_area_scope");
        } else {
            areaScope = areaScope.alias("within_area_scope");
        }
        summaryBase = summaryBase.alias("within_summary_scope");

        Column areaGeometryExpression = qualified("within_area_scope", configuration.areaGeometryColumnName());
        Column summaryGeometryExpression = qualified("within_summary_scope", configuration.summaryGeometryColumnName());
        Column condition = st_predicates.ST_Intersects(areaGeometryExpression, summaryGeometryExpression);
        if (grid != null && summaryGeometry.kind() == GeometryKind.POINT)
            condition = qualified("within_area_scope", WithinGridSupport.CELL_ID).equalTo(
                    WithinGridSupport.pointId(summaryGeometryExpression, original.regions(), grid.side(), summaryGeometry.crs().code()));
        if (configuration.includeEmptyAreas() && temporal != null) {
            condition = condition
                    .and(qualified("within_area_scope", WINDOW_START).equalTo(qualified("within_summary_scope", WINDOW_START)))
                    .and(qualified("within_area_scope", WINDOW_END).equalTo(qualified("within_summary_scope", WINDOW_END)));
        }
        Dataset<Row> joined = areaScope.join(summaryBase, condition,
                configuration.includeEmptyAreas() ? "left_outer" : "inner");

        List<Column> preparedProjection = new ArrayList<>();
        preparedProjection.add(qualified("within_area_scope", AREA_ROW_ID));
        List<CanvasColumnSchema> fallback = new ArrayList<>();
        for (JoinOutputColumnSupport.ResolvedOutputColumn output : areaOutputs) {
            preparedProjection.add(qualified("within_area_scope", output.sourceColumn().name())
                    .alias(output.outputColumnName()));
            fallback.add(JoinOutputColumnSupport.copyWithName(
                    output.sourceColumn(), output.outputColumnName()));
        }
        if (temporal != null) {
            String timeSide = configuration.includeEmptyAreas() ? "within_area_scope" : "within_summary_scope";
            preparedProjection.add(qualified(timeSide, WINDOW_START));
            preparedProjection.add(qualified(timeSide, WINDOW_END));
            fallback.add(timestampColumn(temporal.windowStartColumnName()));
            fallback.add(timestampColumn(temporal.windowEndColumnName()));
        }
        SpatialGroupSummary groupSummary = configuration.groupSummary();
        if (groupSummary != null) {
            preparedProjection.add(qualified("within_summary_scope", groupSummary.groupByColumnName())
                    .alias(GROUP_VALUE));
            fallback.add(JoinOutputColumnSupport.copyWithName(
                    summaryColumns.get(groupSummary.groupByColumnName()),
                    groupSummary.groupByColumnName()));
        }
        preparedProjection.add(summaryGeometryExpression.isNotNull().alias(MATCHED));
        Column intersection = st_functions.ST_Intersection(
                summaryGeometryExpression, areaGeometryExpression);
        if (configuration.usesLinkedGroupResult()) preparedProjection.add(WithinGroupResultPlan.shapeMeasure(
                intersection, summaryGeometry.kind(), configuration.distanceMethod()).alias(prefix + "shape_measure"));
        boolean needsFraction = configuration.statistics().stream()
                .anyMatch(item -> item.apportionsTotal() || item.usesGeographicWeight());
        Column fraction = needsFraction ? WithinStatisticSupport.fraction(
                summaryGeometryExpression, intersection, summaryGeometry.kind(), configuration.distanceMethod())
                : functions.lit(null).cast("double");
        preparedProjection.add(fraction.alias(fractionName));
        Map<String, String> statisticSources = new HashMap<>();
        for (int index = 0; index < configuration.statistics().size(); index++) {
            SpatialWithinStatistic statistic = configuration.statistics().get(index);
            String internal = prefix + "stat_" + index;
            Column value = statisticValue(
                    statistic, intersection, configuration, factors);
            if (statistic.apportionsTotal()) {
                value = WithinStatisticSupport.finiteValue(value).multiply(fraction);
            } else if (statistic.usesGeographicWeight()) {
                value = WithinStatisticSupport.finiteValue(value);
            }
            preparedProjection.add(value.alias(internal));
            statisticSources.put(statistic.statisticId(), internal);
        }
        Dataset<Row> prepared = joined.select(preparedProjection.toArray(Column[]::new));

        List<Column> groupExpressions = new ArrayList<>();
        groupExpressions.add(prepared.col(AREA_ROW_ID));
        areaOutputs.forEach(output -> groupExpressions.add(
                prepared.col(CanvasNodeSupport.quoteIdentifier(output.outputColumnName()))));
        if (temporal != null) {
            groupExpressions.add(prepared.col(WINDOW_START));
            groupExpressions.add(prepared.col(WINDOW_END));
        }
        if (groupSummary != null) groupExpressions.add(prepared.col(GROUP_VALUE));

        Column groupCount = functions.sum(functions.when(prepared.col(MATCHED), 1L).otherwise(0L))
                .alias(GROUP_COUNT);
        List<Column> aggregateExpressions = new ArrayList<>();
        aggregateExpressions.add(groupCount);
        for (SpatialWithinStatistic statistic : configuration.statistics()) {
            aggregateExpressions.add(aggregateExpression(
                    prepared.col(statisticSources.get(statistic.statisticId())),
                    prepared.col(MATCHED), prepared.col(fractionName), statistic).alias(statistic.outputColumnName()));
        }
        if (configuration.usesLinkedGroupResult()) return WithinGroupResultPlan.build(configuration,
                prepared, aggregateExpressions, areaOutputs, areaColumns, summaryColumns, inputs, prefix);
        RelationalGroupedDataset grouped = prepared.groupBy(groupExpressions.toArray(Column[]::new));
        Dataset<Row> aggregate = grouped.agg(
                aggregateExpressions.getFirst(),
                aggregateExpressions.subList(1, aggregateExpressions.size()).toArray(Column[]::new));

        if (groupSummary != null && (groupSummary.includeMinorityMajority()
                || groupSummary.includeGroupPercentage())) {
            List<Column> partition = new ArrayList<>();
            partition.add(aggregate.col(AREA_ROW_ID));
            if (temporal != null) {
                partition.add(aggregate.col(WINDOW_START));
                partition.add(aggregate.col(WINDOW_END));
            }
            WindowSpec groupWindow = Window.partitionBy(partition.toArray(Column[]::new));
            Column count = aggregate.col(GROUP_COUNT);
            if (groupSummary.includeMinorityMajority()) {
                aggregate = aggregate
                        .withColumn(GROUP_MINIMUM, functions.min(count).over(groupWindow))
                        .withColumn(GROUP_MAXIMUM, functions.max(count).over(groupWindow));
                count = aggregate.col(GROUP_COUNT);
                aggregate = aggregate
                        .withColumn(GROUP_MINIMUM_TIES, functions.sum(functions.when(
                                count.equalTo(aggregate.col(GROUP_MINIMUM)), 1).otherwise(0))
                                .over(groupWindow))
                        .withColumn(GROUP_MAXIMUM_TIES, functions.sum(functions.when(
                                count.equalTo(aggregate.col(GROUP_MAXIMUM)), 1).otherwise(0))
                                .over(groupWindow));
                aggregate = aggregate
                        .withColumn(groupSummary.minorityFlagColumnName(),
                                count.gt(0).and(count.equalTo(aggregate.col(GROUP_MINIMUM)))
                                        .and(aggregate.col(GROUP_MINIMUM_TIES).equalTo(1)))
                        .withColumn(groupSummary.majorityFlagColumnName(),
                                count.gt(0).and(count.equalTo(aggregate.col(GROUP_MAXIMUM)))
                                        .and(aggregate.col(GROUP_MAXIMUM_TIES).equalTo(1)));
            }
            if (groupSummary.includeGroupPercentage()) {
                Column total = functions.sum(count).over(groupWindow);
                aggregate = aggregate.withColumn(
                        groupSummary.groupPercentageColumnName(),
                        functions.when(total.gt(0), count.multiply(100d).divide(total)));
            }
        }

        List<Column> finalProjection = new ArrayList<>();
        for (JoinOutputColumnSupport.ResolvedOutputColumn output : areaOutputs) {
            finalProjection.add(aggregate.col(
                    CanvasNodeSupport.quoteIdentifier(output.outputColumnName())));
        }
        if (temporal != null) {
            finalProjection.add(aggregate.col(WINDOW_START).alias(temporal.windowStartColumnName()));
            finalProjection.add(aggregate.col(WINDOW_END).alias(temporal.windowEndColumnName()));
        }
        if (groupSummary != null) {
            finalProjection.add(aggregate.col(GROUP_VALUE).alias(groupSummary.groupByColumnName()));
        }
        for (SpatialWithinStatistic statistic : configuration.statistics()) {
            finalProjection.add(aggregate.col(
                    CanvasNodeSupport.quoteIdentifier(statistic.outputColumnName())));
        }
        if (groupSummary != null && groupSummary.includeMinorityMajority()) {
            finalProjection.add(aggregate.col(groupSummary.minorityFlagColumnName()));
            finalProjection.add(aggregate.col(groupSummary.majorityFlagColumnName()));
        }
        if (groupSummary != null && groupSummary.includeGroupPercentage()) {
            finalProjection.add(aggregate.col(groupSummary.groupPercentageColumnName()));
        }
        Dataset<Row> result = aggregate.select(finalProjection.toArray(Column[]::new));
        List<CanvasColumnSchema> outputColumns = SparkTypeMapper.fromStructType(result.schema(), fallback);
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(), null, outputColumns,
                CanvasDatasetKind.BOUNDED, null, null);
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, result));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateConfiguration(
            SpatialSummarizeWithinConfiguration configuration,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeIssueSink issues
    ) {
        CanvasNodeSupport.required(configuration.areaTableName(), "请选择区域表",
                "configuration.areaTableName", issues);
        CanvasNodeSupport.required(configuration.areaGeometryColumnName(), "请选择区域 Geometry 字段",
                "configuration.areaGeometryColumnName", issues);
        CanvasNodeSupport.required(configuration.summaryTableName(), "请选择被汇总表",
                "configuration.summaryTableName", issues);
        CanvasNodeSupport.required(configuration.summaryGeometryColumnName(), "请选择被汇总 Geometry 字段",
                "configuration.summaryGeometryColumnName", issues);
        CanvasNodeSupport.required(configuration.outputTableName(), "请输入输出表名",
                "configuration.outputTableName", issues);
        if (!CanvasNodeSupport.blank(configuration.areaTableName())
                && configuration.areaTableName().equals(configuration.summaryTableName())) {
            issues.error("INVALID_JOIN_TABLE", "区域表和被汇总表不能相同",
                    "configuration.summaryTableName");
        }
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在："
                    + configuration.outputTableName(), "configuration.outputTableName");
        }
        if (configuration.distanceMethod() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择距离方法", "configuration.distanceMethod");
        }
        if (configuration.lengthUnit() == null || configuration.areaUnit() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择长度和面积单位", "configuration");
        }
        if (configuration.statistics() == null) {
            issues.error("SPATIAL_WITHIN_STATISTICS_REQUIRED", "统计项必须是数组",
                    "configuration.statistics");
        } else if (configuration.statistics().isEmpty()) {
            issues.error("SPATIAL_WITHIN_STATISTICS_REQUIRED", "至少配置一个统计项",
                    "configuration.statistics");
        } else if (configuration.statistics().size()
                > SpatialSummarizeWithinConfiguration.MAX_STATISTICS) {
            issues.error("SPATIAL_WITHIN_STATISTIC_COUNT_EXCEEDED", "统计项不能超过 32 个",
                    "configuration.statistics");
        }
    }

    private static CanvasColumnSchema geometryColumn(
            String name,
            Map<String, CanvasColumnSchema> columns,
            String label,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (CanvasNodeSupport.blank(name)) return null;
        CanvasColumnSchema column = columns.get(name);
        if (column == null) {
            issues.error("COLUMN_NOT_FOUND", label + " Geometry 字段不存在：" + name, path);
        } else if (column.fieldType() != PlatformDataType.GEOMETRY) {
            issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED", label + "字段不是 Geometry：" + name, path);
        } else {
            CanvasNodeSupport.validateSupportedGeometry(List.of(column), path, issues);
        }
        return column;
    }

    private static void validateGeometryPair(
            SpatialSummarizeWithinConfiguration configuration,
            GeometryTypeDefinition area,
            GeometryTypeDefinition summary,
            CanvasNodeIssueSink issues
    ) {
        if (area == null || summary == null) return;
        if (area.kind() != GeometryKind.POLYGON && area.kind() != GeometryKind.MULTIPOLYGON) {
            issues.error("SPATIAL_AREA_GEOMETRY_REQUIRED",
                    "区域 Geometry 必须是 Polygon 或 MultiPolygon",
                    "configuration.areaGeometryColumnName");
        }
        if (!area.crs().equals(summary.crs())) {
            issues.error("SPATIAL_CRS_MISMATCH", "两侧 Geometry 的 CRS 不一致",
                    "configuration.summaryGeometryColumnName");
        }
        if (area.dimension() != summary.dimension()) {
            issues.error("SPATIAL_DIMENSION_MISMATCH", "两侧 Geometry 的坐标维度不一致",
                    "configuration.summaryGeometryColumnName");
        }
        if (configuration.distanceMethod() == SpatialDistanceMethod.GEODESIC
                && (!("EPSG".equals(area.crs().authority()) && area.crs().code() == 4326)
                || !("EPSG".equals(summary.crs().authority()) && summary.crs().code() == 4326))) {
            issues.error("GEODESIC_DISTANCE_REQUIRES_WGS84",
                    "测地线测量仅支持 EPSG:4326 XY", "configuration.distanceMethod");
        }
    }

    private static void validateStatistics(
            SpatialSummarizeWithinConfiguration configuration,
            Map<String, CanvasColumnSchema> sourceColumns,
            CanvasNodeIssueSink issues
    ) {
        if (configuration.statistics() == null) return;
        Set<UUID> ids = new HashSet<>();
        for (int index = 0; index < configuration.statistics().size(); index++) {
            SpatialWithinStatistic statistic = configuration.statistics().get(index);
            String path = "configuration.statistics[" + index + "]";
            if (statistic == null) {
                issues.error("INVALID_SPATIAL_WITHIN_STATISTIC", "统计项不能为空", path);
                continue;
            }
            try {
                UUID id = UUID.fromString(statistic.statisticId());
                if (!ids.add(id)) {
                    issues.error("DUPLICATE_SPATIAL_WITHIN_STATISTIC_ID", "统计项 ID 必须唯一",
                            path + ".statisticId");
                }
            } catch (RuntimeException exception) {
                issues.error("INVALID_SPATIAL_WITHIN_STATISTIC_ID", "统计项 ID 必须是 UUID",
                        path + ".statisticId");
            }
            if (statistic.kind() == null) {
                issues.error("INVALID_SPATIAL_WITHIN_STATISTIC", "请选择统计类型", path + ".kind");
                continue;
            }
            CanvasNodeSupport.required(statistic.outputColumnName(), "请输入统计输出字段名",
                    path + ".outputColumnName", issues);
            boolean noSource = statistic.kind() == SpatialWithinStatisticKind.COUNT
                    || statistic.kind() == SpatialWithinStatisticKind.LENGTH_WITHIN
                    || statistic.kind() == SpatialWithinStatisticKind.AREA_WITHIN;
            if (noSource) {
                if (statistic.sourceColumnName() != null) {
                    issues.error("INVALID_SPATIAL_WITHIN_STATISTIC",
                            statistic.kind() + " 不使用来源字段", path + ".sourceColumnName");
                }
                continue;
            }
            CanvasNodeSupport.required(statistic.sourceColumnName(), "请选择统计来源字段",
                    path + ".sourceColumnName", issues);
            CanvasColumnSchema source = statistic.sourceColumnName() == null ? null : sourceColumns.get(statistic.sourceColumnName());
            if (!CanvasNodeSupport.blank(statistic.sourceColumnName()) && source == null) {
                issues.error("COLUMN_NOT_FOUND", "统计字段不存在：" + statistic.sourceColumnName(),
                        path + ".sourceColumnName");
            } else if (source != null && source.fieldType() == PlatformDataType.GEOMETRY) {
                issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED", "Geometry 不能作为标量统计字段",
                        path + ".sourceColumnName");
            } else if (source != null && statistic.kind() == SpatialWithinStatisticKind.ANY
                    && source.fieldType() != PlatformDataType.STRING) {
                issues.error("STRING_COLUMN_REQUIRED", "ANY 要求字符串字段", path + ".sourceColumnName");
            } else if (source != null && (numericStatistic(statistic.kind()) || statistic.apportionsTotal())
                    && !numeric(source.fieldType())) {
                issues.error("NUMERIC_COLUMN_REQUIRED", "当前统计类型要求数值字段",
                        path + ".sourceColumnName");
            }
        }
    }

    private static void validateGroup(
            SpatialGroupSummary group,
            Map<String, CanvasColumnSchema> columns,
            boolean linked,
            CanvasNodeIssueSink issues
    ) {
        if (group == null) return;
        CanvasNodeSupport.required(group.groupByColumnName(), "请选择分组字段",
                "configuration.groupSummary.groupByColumnName", issues);
        CanvasColumnSchema column = group.groupByColumnName() == null ? null : columns.get(group.groupByColumnName());
        if (!CanvasNodeSupport.blank(group.groupByColumnName()) && column == null) {
            issues.error("COLUMN_NOT_FOUND", "分组字段不存在：" + group.groupByColumnName(),
                    "configuration.groupSummary.groupByColumnName");
        } else if (column != null && column.fieldType() == PlatformDataType.GEOMETRY) {
            issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED", "Geometry 不能作为分组字段",
                    "configuration.groupSummary.groupByColumnName");
        }
        if (group.includeMinorityMajority() && !linked) {
            CanvasNodeSupport.required(group.minorityFlagColumnName(), "请输入少数组标记字段名",
                    "configuration.groupSummary.minorityFlagColumnName", issues);
            CanvasNodeSupport.required(group.majorityFlagColumnName(), "请输入多数标记字段名",
                    "configuration.groupSummary.majorityFlagColumnName", issues);
            issues.warning("MINORITY_MAJORITY_TIES_RESOLVE_FALSE",
                    "少数或多数出现并列时，所有并列组的标记均为 false",
                    "configuration.groupSummary.includeMinorityMajority");
        }
        if (group.includeGroupPercentage()) {
            CanvasNodeSupport.required(group.groupPercentageColumnName(), "请输入组百分比字段名",
                    "configuration.groupSummary.groupPercentageColumnName", issues);
        }
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
            if (column == null) {
                issues.error("COLUMN_NOT_FOUND", "时间字段不存在：" + temporal.timeColumnName(),
                        "configuration.temporalSlicing.timeColumnName");
            } else if (column.fieldType() != PlatformDataType.TIMESTAMP) {
                issues.error("TIMESTAMP_COLUMN_REQUIRED", "时间切片字段必须是 TIMESTAMP",
                        "configuration.temporalSlicing.timeColumnName");
            }
        }
        return result;
    }

    private static void validateOutputNames(
            SpatialSummarizeWithinConfiguration configuration,
            List<JoinOutputColumnSupport.ResolvedOutputColumn> areaOutputs,
            CanvasNodeIssueSink issues
    ) {
        Set<String> names = new HashSet<>();
        areaOutputs.forEach(output -> names.add(output.outputColumnName().toLowerCase(Locale.ROOT)));
        if (configuration.statistics() != null) {
            configuration.statistics().stream().filter(java.util.Objects::nonNull).forEach(statistic ->
                    addOutputName(statistic.outputColumnName(), "configuration.statistics", names, issues));
        }
        SpatialGroupSummary group = configuration.groupSummary();
        if (group != null && !configuration.usesLinkedGroupResult()) {
            addOutputName(group.groupByColumnName(), "configuration.groupSummary.groupByColumnName", names, issues);
            if (group.includeMinorityMajority()) {
                addOutputName(group.minorityFlagColumnName(),
                        "configuration.groupSummary.minorityFlagColumnName", names, issues);
                addOutputName(group.majorityFlagColumnName(),
                        "configuration.groupSummary.majorityFlagColumnName", names, issues);
            }
            if (group.includeGroupPercentage()) {
                addOutputName(group.groupPercentageColumnName(),
                        "configuration.groupSummary.groupPercentageColumnName", names, issues);
            }
        }
        SpatialTemporalSlicing temporal = configuration.temporalSlicing();
        if (temporal != null) {
            addOutputName(temporal.windowStartColumnName(),
                    "configuration.temporalSlicing.windowStartColumnName", names, issues);
            addOutputName(temporal.windowEndColumnName(),
                    "configuration.temporalSlicing.windowEndColumnName", names, issues);
        }
    }

    private static void addOutputName(
            String value,
            String path,
            Set<String> names,
            CanvasNodeIssueSink issues
    ) {
        if (!CanvasNodeSupport.blank(value) && !names.add(value.toLowerCase(Locale.ROOT))) {
            issues.error("DUPLICATE_COLUMN_NAME", "输出字段名重复：" + value, path);
        }
    }

    private static MeasurementFactors resolveMeasurementFactors(
            SpatialSummarizeWithinConfiguration configuration,
            GeometryTypeDefinition geometry,
            CanvasNodeIssueSink issues
    ) {
        if (configuration.distanceMethod() == SpatialDistanceMethod.GEODESIC) {
            double length = SpatialDistanceSupport.metresPerConfiguredUnit(configuration.lengthUnit());
            double area = SpatialDistanceSupport.squareMetresPerConfiguredUnit(configuration.areaUnit());
            if (!Double.isFinite(length)) {
                issues.error("SPATIAL_DISTANCE_UNIT_UNSUPPORTED",
                        "测地线长度不能使用来源 CRS 单位", "configuration.lengthUnit");
            }
            return new MeasurementFactors(length, area);
        }
        SpatialDistanceSupport.Resolution axisPerLength =
                SpatialDistanceSupport.sourceUnitsPerConfiguredUnit(
                        configuration.lengthUnit(), geometry.crs());
        SpatialDistanceSupport.Resolution axisPerMetre =
                SpatialDistanceSupport.sourceUnitsPerConfiguredUnit(
                        cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit.METERS,
                        geometry.crs());
        if (!axisPerLength.valid()) {
            issues.error("SPATIAL_DISTANCE_UNIT_UNSUPPORTED", axisPerLength.error(),
                    "configuration.lengthUnit");
        }
        double areaFactor = Double.NaN;
        if (axisPerMetre.valid()) {
            areaFactor = axisPerMetre.sourceCrsValue() * axisPerMetre.sourceCrsValue()
                    * SpatialDistanceSupport.squareMetresPerConfiguredUnit(configuration.areaUnit());
        } else if (configuration.statistics() != null && configuration.statistics().stream()
                .anyMatch(item -> item.kind() == SpatialWithinStatisticKind.AREA_WITHIN)) {
            issues.error("SPATIAL_DISTANCE_UNIT_UNSUPPORTED",
                    "来源 CRS 轴单位无法换算面积单位", "configuration.areaUnit");
        }
        if (axisPerLength.angular()) {
            issues.warning("PLANAR_DISTANCE_USES_ANGULAR_UNITS",
                    "地理 CRS 的平面长度使用角度，结果随纬度变化", "configuration.lengthUnit");
        }
        return new MeasurementFactors(axisPerLength.sourceCrsValue(), areaFactor);
    }

    private static Column statisticValue(
            SpatialWithinStatistic statistic,
            Column intersection,
            SpatialSummarizeWithinConfiguration configuration,
            MeasurementFactors factors
    ) {
        return switch (statistic.kind()) {
            case COUNT -> functions.lit(1L);
            case LENGTH_WITHIN -> (configuration.distanceMethod() == SpatialDistanceMethod.GEODESIC
                    ? st_functions.ST_LengthSpheroid(intersection)
                    : st_functions.ST_Length(intersection)).divide(factors.lengthFactor());
            case AREA_WITHIN -> (configuration.distanceMethod() == SpatialDistanceMethod.GEODESIC
                    ? st_functions.ST_AreaSpheroid(intersection)
                    : st_functions.ST_Area(intersection)).divide(factors.areaFactor());
            default -> qualified("within_summary_scope", statistic.sourceColumnName());
        };
    }

    private static Column qualified(String alias, String field) {
        return functions.col(alias + "." + CanvasNodeSupport.quoteIdentifier(field));
    }

    private static Column aggregateExpression(
            Column source,
            Column matched,
            Column fraction,
            SpatialWithinStatistic statistic
    ) {
        if (statistic.requiresWeightedDispersionVersion()) {
            Column variance = WithinWeightedVariance.expression(source, fraction);
            return statistic.kind() == SpatialWithinStatisticKind.STDDEV ? functions.sqrt(variance) : variance;
        }
        if (statistic.usesGeographicWeight()) return WithinStatisticSupport.weightedMean(source, fraction);
        return switch (statistic.kind()) {
            case COUNT -> functions.sum(functions.when(matched, 1L).otherwise(0L));
            case COUNT_FIELD -> functions.count(functions.when(matched, source));
            case ANY -> functions.first(functions.when(matched, source), true);
            case SUM, LENGTH_WITHIN, AREA_WITHIN -> functions.sum(source);
            case MEAN -> functions.avg(source);
            case MIN -> functions.min(source);
            case MAX -> functions.max(source);
            case RANGE -> functions.max(source).minus(functions.min(source));
            case STDDEV -> functions.stddev_samp(source);
            case VARIANCE -> functions.variance(source);
        };
    }

    private static String internalPrefix(SpatialSummarizeWithinConfiguration configuration,
                                         SparkCanvasTable areas, SparkCanvasTable summaries) {
        Set<String> names = new HashSet<>();
        java.util.Collections.addAll(names, areas.dataset().columns());
        java.util.Collections.addAll(names, summaries.dataset().columns());
        configuration.areaOutputColumns().forEach(item -> names.add(item.outputColumnName()));
        configuration.statistics().forEach(item -> names.add(item.outputColumnName()));
        var linked = configuration.groupResult();
        if (linked != null) {
            names.add(linked.areaKeyOutputColumnName()); names.add(linked.groupValueColumnName());
            names.add(linked.minorityValueColumnName()); names.add(linked.majorityValueColumnName());
            names.add(linked.minorityPercentageColumnName()); names.add(linked.majorityPercentageColumnName());
        }
        SpatialGroupSummary group = configuration.groupSummary();
        if (group != null) {
            names.add(group.groupByColumnName());
            names.add(group.minorityFlagColumnName());
            names.add(group.majorityFlagColumnName());
            names.add(group.groupPercentageColumnName());
        }
        SpatialTemporalSlicing time = configuration.temporalSlicing();
        if (time != null) {
            names.add(time.windowStartColumnName());
            names.add(time.windowEndColumnName());
        }
        String prefix = "__datascalpel_within_";
        while (startsWithPrefix(names, prefix)) prefix += "_";
        return prefix;
    }

    private static boolean startsWithPrefix(Set<String> names, String prefix) {
        return names.stream().filter(java.util.Objects::nonNull)
                .anyMatch(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix));
    }

    private static boolean numericStatistic(SpatialWithinStatisticKind kind) {
        return kind == SpatialWithinStatisticKind.SUM
                || kind == SpatialWithinStatisticKind.MEAN
                || kind == SpatialWithinStatisticKind.RANGE
                || kind == SpatialWithinStatisticKind.STDDEV
                || kind == SpatialWithinStatisticKind.VARIANCE;
    }

    private static boolean numeric(PlatformDataType type) {
        return type == PlatformDataType.BYTE || type == PlatformDataType.SHORT
                || type == PlatformDataType.INTEGER || type == PlatformDataType.LONG
                || type == PlatformDataType.FLOAT || type == PlatformDataType.DOUBLE
                || type == PlatformDataType.DECIMAL;
    }

    private static CanvasColumnSchema timestampColumn(String name) {
        return new CanvasColumnSchema(name, PlatformDataType.TIMESTAMP,
                null, null, null, false, null, false, false, null, null);
    }

    private record MeasurementFactors(double lengthFactor, double areaFactor) {
    }
}
