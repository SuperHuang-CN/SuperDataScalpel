package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SpatialBinShape;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialHotSpotAnalysisSource;
import cn.superhuang.data.scalpel.contract.task.SpatialHotSpotMultipleTesting;
import cn.superhuang.data.scalpel.contract.task.SpatialHotSpotsConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialHotSpotsNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialTemporalSlicing;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
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
import org.apache.spark.sql.sedona_sql.expressions.st_functions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Square-bin Getis-Ord Gi* hot/cold spot analysis for bounded projected points. */
public final class SpatialHotSpotsNodeOperator implements CanvasNodeOperator {

    private static final String Q = "__datascalpel_hot_q";
    private static final String R = "__datascalpel_hot_r";
    private static final String X = "__datascalpel_hot_x";
    private static final String Y = "__datascalpel_hot_y";
    private static final String VALUE = "__datascalpel_hot_value";
    private static final String POINT_COUNT = "__datascalpel_hot_point_count";
    private static final String ANALYSIS_VALUE = "__datascalpel_hot_analysis_value";
    private static final String WINDOW_START = "__datascalpel_hot_window_start";
    private static final String WINDOW_END = "__datascalpel_hot_window_end";
    private static final String MIN_Q = "__datascalpel_hot_min_q";
    private static final String MAX_Q = "__datascalpel_hot_max_q";
    private static final String MIN_R = "__datascalpel_hot_min_r";
    private static final String MAX_R = "__datascalpel_hot_max_r";
    private static final String WINDOW_COUNT = "__datascalpel_hot_window_count";
    private static final String FOCAL_Q = "__datascalpel_hot_focal_q";
    private static final String FOCAL_R = "__datascalpel_hot_focal_r";
    private static final String NEIGHBOR_Q = "__datascalpel_hot_neighbor_q";
    private static final String NEIGHBOR_R = "__datascalpel_hot_neighbor_r";
    private static final String NEIGHBOR_VALUE = "__datascalpel_hot_neighbor_value";
    private static final String LOCAL_SUM = "__datascalpel_hot_local_sum";
    private static final String WEIGHT_SUM = "__datascalpel_hot_weight_sum";
    private static final String GLOBAL_COUNT = "__datascalpel_hot_global_count";
    private static final String GLOBAL_SUM = "__datascalpel_hot_global_sum";
    private static final String GLOBAL_SQUARE_SUM = "__datascalpel_hot_global_square_sum";
    private static final String Z_SCORE = "__datascalpel_hot_z_score";
    private static final String P_VALUE = "__datascalpel_hot_p_value";
    private static final String P_RANK = "__datascalpel_hot_p_rank";
    private static final String ADJUSTED_P_VALUE = "__datascalpel_hot_adjusted_p_value";
    private static final String CONFIDENCE_BIN = "__datascalpel_hot_confidence_bin";

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_HOT_SPOTS;
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
        if (!(definition instanceof SpatialHotSpotsNodeDefinition node)) {
            throw new IllegalArgumentException("SPATIAL_HOT_SPOTS operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        SpatialHotSpotsConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(inputSchemas);
        CanvasNodeIssueSink issues = context.issues();
        validateBase(configuration, inputs, issues);
        SparkCanvasTable source = CanvasNodeSupport.blank(configuration.sourceTableName())
                ? null : inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error("TABLE_NOT_FOUND", "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName");
        }
        if (source == null) return CanvasNodeOperationResult.invalid(inputSchemas);

        Map<String, CanvasColumnSchema> columns = CanvasNodeSupport.columns(source.schema());
        CanvasColumnSchema point = validatePoint(source, configuration, columns, issues);
        CanvasColumnSchema analysis = validateAnalysis(configuration, columns, issues);
        SpatialTemporalSupport.WindowParameters window = validateTemporal(
                configuration.temporalSlicing(), columns, issues);
        validateOutputNames(configuration, issues);

        SpatialDistanceSupport.Resolution bin = point == null || point.geometry() == null ? null
                : SpatialDistanceSupport.resolve(configuration.binSize(), configuration.binSizeUnit(), point.geometry().crs());
        SpatialDistanceSupport.Resolution neighborhood = point == null || point.geometry() == null ? null
                : SpatialDistanceSupport.resolve(configuration.neighborhoodDistance(),
                configuration.neighborhoodDistanceUnit(), point.geometry().crs());
        validateResolvedDistances(configuration, bin, neighborhood, issues);
        if (issues.hasErrors() || point == null || bin == null || neighborhood == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> result = buildPlan(source.dataset(), configuration, analysis, window,
                bin.sourceCrsValue(), neighborhood.sourceCrsValue(), point.geometry().crs().code());
        CanvasTableSchema outputSchema = new CanvasTableSchema(configuration.outputTableName(), null,
                outputSchema(configuration, point), CanvasDatasetKind.BOUNDED, null, null);
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, result));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static Dataset<Row> buildPlan(
            Dataset<Row> source,
            SpatialHotSpotsConfiguration configuration,
            CanvasColumnSchema analysis,
            SpatialTemporalSupport.WindowParameters window,
            double side,
            double neighborhood,
            int srid
    ) {
        Column geometry = source.col(CanvasNodeSupport.quoteIdentifier(configuration.pointGeometryColumnName()));
        List<Column> projection = new ArrayList<>();
        projection.add(geometry.alias("__datascalpel_hot_geometry"));
        projection.add(st_functions.ST_X(geometry).alias(X));
        projection.add(st_functions.ST_Y(geometry).alias(Y));
        if (analysis != null) {
            Column value = source.col(CanvasNodeSupport.quoteIdentifier(analysis.name())).cast("double");
            Column checked = functions.when(value.isNull(), functions.lit(0d))
                    .when(functions.isnan(value).or(functions.abs(value).gt(Double.MAX_VALUE)),
                            functions.raise_error(functions.lit("SPATIAL_HOT_SPOT_VALUE_NOT_FINITE")).cast("double"))
                    .otherwise(value);
            projection.add(functions.when(geometry.isNotNull(), checked)
                    .otherwise(functions.lit(0d)).alias(VALUE));
        }
        if (configuration.temporalSlicing() != null) {
            projection.add(source.col(CanvasNodeSupport.quoteIdentifier(
                    configuration.temporalSlicing().timeColumnName())).alias("__datascalpel_hot_time"));
        }
        Dataset<Row> points = source.select(projection.toArray(Column[]::new))
                .filter(functions.col("__datascalpel_hot_geometry").isNotNull()
                        .and(st_functions.ST_IsEmpty(functions.col("__datascalpel_hot_geometry")).equalTo(false)));
        if (configuration.temporalSlicing() != null && window != null) {
            points = SpatialTemporalSupport.addWindows(points, points.col("__datascalpel_hot_time"),
                    window, WINDOW_START, WINDOW_END);
        }
        PlanarGridSupport.Cell home = PlanarGridSupport.pointIndices(
                PlanarGridSupport.checkedCoordinate(points.col(X), side),
                PlanarGridSupport.checkedCoordinate(points.col(Y), side), side, SpatialBinShape.SQUARE);
        points = points.withColumn(Q, home.q()).withColumn(R, home.r());

        List<Column> groups = grouping(points, Q, R, configuration.temporalSlicing() != null);
        Column pointCount = functions.count(points.col("__datascalpel_hot_geometry")).alias(POINT_COUNT);
        Column analysisValue = analysis == null
                ? functions.count(points.col("__datascalpel_hot_geometry")).cast("double").alias(ANALYSIS_VALUE)
                : functions.sum(points.col(VALUE)).alias(ANALYSIS_VALUE);
        Dataset<Row> observed = points.groupBy(groups.toArray(Column[]::new)).agg(pointCount, analysisValue)
                .withColumn(ANALYSIS_VALUE, checkedFinite(functions.col(ANALYSIS_VALUE)));

        Dataset<Row> bounds = observed.agg(
                functions.min(observed.col(Q)).alias(MIN_Q), functions.max(observed.col(Q)).alias(MAX_Q),
                functions.min(observed.col(R)).alias(MIN_R), functions.max(observed.col(R)).alias(MAX_R))
                .filter(functions.col(MIN_Q).isNotNull());
        Dataset<Row> scaffolds;
        if (configuration.temporalSlicing() == null) {
            scaffolds = bounds.withColumn(WINDOW_COUNT, functions.lit(1L));
        } else {
            Dataset<Row> windows = observed.select(WINDOW_START, WINDOW_END).distinct();
            Dataset<Row> windowCount = windows.agg(functions.count(windows.col(WINDOW_START)).alias(WINDOW_COUNT));
            scaffolds = bounds.crossJoin(windowCount).crossJoin(windows);
        }
        Column width = scaffolds.col(MAX_Q).minus(scaffolds.col(MIN_Q)).plus(1L);
        Column height = scaffolds.col(MAX_R).minus(scaffolds.col(MIN_R)).plus(1L);
        Column total = width.multiply(height).multiply(scaffolds.col(WINDOW_COUNT));
        Column qSequence = functions.when(total.leq(SpatialHotSpotsConfiguration.MAX_OUTPUT_CELLS),
                        functions.sequence(scaffolds.col(MIN_Q), scaffolds.col(MAX_Q)))
                .otherwise(functions.array(functions.raise_error(
                        functions.lit("SPATIAL_HOT_SPOT_CELL_LIMIT_EXCEEDED")).cast("long")));
        Dataset<Row> coordinates = scaffolds.withColumn(Q, functions.explode(qSequence))
                .withColumn(R, functions.explode(functions.sequence(scaffolds.col(MIN_R), scaffolds.col(MAX_R))));
        List<Column> coordinateProjection = new ArrayList<>(List.of(coordinates.col(Q), coordinates.col(R)));
        if (configuration.temporalSlicing() != null) {
            coordinateProjection.add(coordinates.col(WINDOW_START));
            coordinateProjection.add(coordinates.col(WINDOW_END));
        }
        coordinates = coordinates.select(coordinateProjection.toArray(Column[]::new));

        Dataset<Row> grid = joinObserved(coordinates, observed, configuration.temporalSlicing() != null);
        Dataset<Row> local = localNeighborhood(grid, configuration.temporalSlicing() != null, side, neighborhood);
        Dataset<Row> statistics = joinLocal(grid, local, configuration.temporalSlicing() != null);
        WindowSpec global = partitionWindow(configuration.temporalSlicing() != null);
        statistics = statistics
                .withColumn(GLOBAL_COUNT, functions.count(statistics.col(Q)).over(global))
                .withColumn(GLOBAL_SUM, functions.sum(statistics.col(ANALYSIS_VALUE)).over(global))
                .withColumn(GLOBAL_SQUARE_SUM,
                        functions.sum(functions.pow(statistics.col(ANALYSIS_VALUE), 2d)).over(global));
        Column n = statistics.col(GLOBAL_COUNT).cast("double");
        Column mean = statistics.col(GLOBAL_SUM).divide(n);
        Column variance = functions.greatest(functions.lit(0d),
                statistics.col(GLOBAL_SQUARE_SUM).divide(n).minus(functions.pow(mean, 2d)));
        Column standardDeviation = functions.sqrt(variance);
        Column weight = statistics.col(WEIGHT_SUM).cast("double");
        Column denominator = standardDeviation.multiply(functions.sqrt(
                n.multiply(weight).minus(functions.pow(weight, 2d)).divide(n.minus(1d))));
        Column z = functions.when(n.lt(2d).or(standardDeviation.leq(0d)).or(denominator.leq(0d)), functions.lit(0d))
                .otherwise(statistics.col(LOCAL_SUM).minus(mean.multiply(weight)).divide(denominator));
        statistics = statistics.withColumn(Z_SCORE, checkedFinite(z))
                .withColumn(P_VALUE, normalTwoSidedP(functions.col(Z_SCORE)));

        WindowSpec rankWindow = orderedWindow(configuration.temporalSlicing() != null, true);
        WindowSpec reverseWindow = orderedWindow(configuration.temporalSlicing() != null, false)
                .rowsBetween(Window.unboundedPreceding(), Window.currentRow());
        statistics = statistics.withColumn(P_RANK, functions.row_number().over(rankWindow));
        Column bhCandidate = statistics.col(P_VALUE).multiply(statistics.col(GLOBAL_COUNT))
                .divide(statistics.col(P_RANK));
        Column adjusted = configuration.multipleTesting() == SpatialHotSpotMultipleTesting.FDR_BH
                ? functions.least(functions.lit(1d), functions.min(bhCandidate).over(reverseWindow))
                : statistics.col(P_VALUE);
        statistics = statistics.withColumn(ADJUSTED_P_VALUE, adjusted);
        Column level = functions.when(statistics.col(ADJUSTED_P_VALUE).leq(0.01d), functions.lit(3))
                .when(statistics.col(ADJUSTED_P_VALUE).leq(0.05d), functions.lit(2))
                .when(statistics.col(ADJUSTED_P_VALUE).leq(0.10d), functions.lit(1))
                .otherwise(functions.lit(0));
        Column direction = functions.when(statistics.col(Z_SCORE).gt(0d), functions.lit(1))
                .when(statistics.col(Z_SCORE).lt(0d), functions.lit(-1)).otherwise(functions.lit(0));
        statistics = statistics.withColumn(CONFIDENCE_BIN, level.multiply(direction));

        List<Column> result = new ArrayList<>();
        Column identity = functions.concat_ws(":", functions.lit("HOT_SPOTS:SQUARE:" + srid + ":"
                        + Double.toHexString(side)), statistics.col(Q), statistics.col(R));
        if (configuration.temporalSlicing() != null) {
            identity = functions.concat_ws(":", identity, statistics.col(WINDOW_START).cast("long"));
        }
        result.add(identity.alias(configuration.binIdColumnName()));
        result.add(PlanarGridSupport.geometry(statistics.col(Q), statistics.col(R), SpatialBinShape.SQUARE,
                side, srid, null).alias(configuration.binGeometryColumnName()));
        SpatialTemporalSlicing temporal = configuration.temporalSlicing();
        if (temporal != null) {
            result.add(statistics.col(WINDOW_START).alias(temporal.windowStartColumnName()));
            result.add(statistics.col(WINDOW_END).alias(temporal.windowEndColumnName()));
        }
        result.add(statistics.col(POINT_COUNT).alias(configuration.pointCountColumnName()));
        result.add(statistics.col(ANALYSIS_VALUE).alias(configuration.analysisValueColumnName()));
        result.add(statistics.col(Z_SCORE).alias(configuration.zScoreColumnName()));
        result.add(statistics.col(P_VALUE).alias(configuration.pValueColumnName()));
        result.add(statistics.col(ADJUSTED_P_VALUE).alias(configuration.adjustedPValueColumnName()));
        result.add(statistics.col(CONFIDENCE_BIN).cast("integer").alias(configuration.confidenceBinColumnName()));
        return statistics.select(result.toArray(Column[]::new));
    }

    private static Dataset<Row> joinObserved(Dataset<Row> coordinates, Dataset<Row> observed, boolean temporal) {
        Dataset<Row> grid = coordinates.alias("g");
        Dataset<Row> values = observed.alias("v");
        Column condition = qualified("g", Q).equalTo(qualified("v", Q))
                .and(qualified("g", R).equalTo(qualified("v", R)));
        if (temporal) condition = condition.and(qualified("g", WINDOW_START).equalTo(qualified("v", WINDOW_START)))
                .and(qualified("g", WINDOW_END).equalTo(qualified("v", WINDOW_END)));
        List<Column> projection = new ArrayList<>(List.of(qualified("g", Q), qualified("g", R)));
        if (temporal) {
            projection.add(qualified("g", WINDOW_START));
            projection.add(qualified("g", WINDOW_END));
        }
        projection.add(functions.coalesce(qualified("v", POINT_COUNT), functions.lit(0L)).alias(POINT_COUNT));
        projection.add(functions.coalesce(qualified("v", ANALYSIS_VALUE), functions.lit(0d)).alias(ANALYSIS_VALUE));
        return grid.join(values, condition, "left").select(projection.toArray(Column[]::new));
    }

    private static Dataset<Row> localNeighborhood(
            Dataset<Row> grid,
            boolean temporal,
            double side,
            double neighborhood
    ) {
        long range = (long) Math.ceil(neighborhood / side);
        List<Column> focalColumns = new ArrayList<>(List.of(
                grid.col(Q).alias(FOCAL_Q), grid.col(R).alias(FOCAL_R)));
        if (temporal) {
            focalColumns.add(grid.col(WINDOW_START));
            focalColumns.add(grid.col(WINDOW_END));
        }
        Dataset<Row> focal = grid.select(focalColumns.toArray(Column[]::new))
                .withColumn(NEIGHBOR_Q, functions.explode(functions.sequence(
                        functions.col(FOCAL_Q).minus(range), functions.col(FOCAL_Q).plus(range))))
                .withColumn(NEIGHBOR_R, functions.explode(functions.sequence(
                        functions.col(FOCAL_R).minus(range), functions.col(FOCAL_R).plus(range))))
                .filter(functions.pow(functions.col(NEIGHBOR_Q).minus(functions.col(FOCAL_Q)).multiply(side), 2d)
                        .plus(functions.pow(functions.col(NEIGHBOR_R).minus(functions.col(FOCAL_R)).multiply(side), 2d))
                        .leq(neighborhood * neighborhood)).alias("f");
        List<Column> neighborColumns = new ArrayList<>(List.of(
                grid.col(Q).alias(NEIGHBOR_Q), grid.col(R).alias(NEIGHBOR_R),
                grid.col(ANALYSIS_VALUE).alias(NEIGHBOR_VALUE)));
        if (temporal) {
            neighborColumns.add(grid.col(WINDOW_START));
            neighborColumns.add(grid.col(WINDOW_END));
        }
        Dataset<Row> neighbor = grid.select(neighborColumns.toArray(Column[]::new)).alias("n");
        Column condition = qualified("f", NEIGHBOR_Q).equalTo(qualified("n", NEIGHBOR_Q))
                .and(qualified("f", NEIGHBOR_R).equalTo(qualified("n", NEIGHBOR_R)));
        if (temporal) condition = condition.and(qualified("f", WINDOW_START).equalTo(qualified("n", WINDOW_START)))
                .and(qualified("f", WINDOW_END).equalTo(qualified("n", WINDOW_END)));
        Dataset<Row> joined = focal.join(neighbor, condition, "inner");
        List<Column> groups = new ArrayList<>(List.of(qualified("f", FOCAL_Q), qualified("f", FOCAL_R)));
        if (temporal) {
            groups.add(qualified("f", WINDOW_START));
            groups.add(qualified("f", WINDOW_END));
        }
        return joined.groupBy(groups.toArray(Column[]::new)).agg(
                functions.sum(qualified("n", NEIGHBOR_VALUE)).alias(LOCAL_SUM),
                functions.count(qualified("n", NEIGHBOR_Q)).alias(WEIGHT_SUM));
    }

    private static Dataset<Row> joinLocal(Dataset<Row> grid, Dataset<Row> local, boolean temporal) {
        Dataset<Row> values = grid.alias("v");
        Dataset<Row> neighborhood = local.alias("l");
        Column condition = qualified("v", Q).equalTo(qualified("l", FOCAL_Q))
                .and(qualified("v", R).equalTo(qualified("l", FOCAL_R)));
        if (temporal) condition = condition.and(qualified("v", WINDOW_START).equalTo(qualified("l", WINDOW_START)))
                .and(qualified("v", WINDOW_END).equalTo(qualified("l", WINDOW_END)));
        List<Column> projection = new ArrayList<>(List.of(qualified("v", Q), qualified("v", R)));
        if (temporal) {
            projection.add(qualified("v", WINDOW_START));
            projection.add(qualified("v", WINDOW_END));
        }
        projection.add(qualified("v", POINT_COUNT));
        projection.add(qualified("v", ANALYSIS_VALUE));
        projection.add(qualified("l", LOCAL_SUM));
        projection.add(qualified("l", WEIGHT_SUM));
        return values.join(neighborhood, condition, "inner").select(projection.toArray(Column[]::new));
    }

    private static Column qualified(String alias, String columnName) {
        return functions.col(alias + ".`" + columnName.replace("`", "``") + "`");
    }

    private static List<Column> grouping(Dataset<Row> dataset, String first, String second, boolean temporal) {
        List<Column> result = new ArrayList<>(List.of(dataset.col(first), dataset.col(second)));
        if (temporal) {
            result.add(dataset.col(WINDOW_START));
            result.add(dataset.col(WINDOW_END));
        }
        return result;
    }

    private static WindowSpec partitionWindow(boolean temporal) {
        return temporal ? Window.partitionBy(WINDOW_START, WINDOW_END) : Window.partitionBy();
    }

    private static WindowSpec orderedWindow(boolean temporal, boolean ascending) {
        Column p = ascending ? functions.col(P_VALUE).asc() : functions.col(P_VALUE).desc();
        Column q = ascending ? functions.col(Q).asc() : functions.col(Q).desc();
        Column r = ascending ? functions.col(R).asc() : functions.col(R).desc();
        return temporal ? Window.partitionBy(WINDOW_START, WINDOW_END).orderBy(p, q, r) : Window.orderBy(p, q, r);
    }

    private static Column checkedFinite(Column value) {
        return functions.when(functions.isnan(value).or(functions.abs(value).gt(Double.MAX_VALUE)),
                functions.raise_error(functions.lit("SPATIAL_HOT_SPOT_VALUE_NOT_FINITE")).cast("double"))
                .otherwise(value);
    }

    /** Two-sided standard-normal p-value, using a deterministic ~7.5e-8 A&S CDF approximation. */
    private static Column normalTwoSidedP(Column z) {
        Column x = functions.abs(z);
        Column t = functions.lit(1d).divide(functions.lit(1d).plus(x.multiply(0.2316419d)));
        Column polynomial = functions.lit(1.330274429d).multiply(t).minus(1.821255978d)
                .multiply(t).plus(1.781477937d).multiply(t).minus(0.356563782d)
                .multiply(t).plus(0.319381530d).multiply(t);
        Column tail = functions.exp(functions.pow(x, 2d).multiply(-0.5d))
                .multiply(0.3989422804014327d).multiply(polynomial);
        return functions.least(functions.lit(1d), functions.greatest(functions.lit(0d), tail.multiply(2d)));
    }

    private static void validateBase(
            SpatialHotSpotsConfiguration c,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeIssueSink issues
    ) {
        CanvasNodeSupport.required(c.sourceTableName(), "请选择来源表", "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(c.pointGeometryColumnName(), "请选择 Point Geometry 字段", "configuration.pointGeometryColumnName", issues);
        if (c.analysisSource() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择热点分析值", "configuration.analysisSource");
        }
        if (!Double.isFinite(c.binSize()) || c.binSize() <= 0 || c.binSizeUnit() == null) {
            issues.error("INVALID_SPATIAL_HOT_SPOT_BIN_SIZE", "方格大小必须是带单位的有限正数", "configuration.binSize");
        }
        if (!Double.isFinite(c.neighborhoodDistance()) || c.neighborhoodDistance() <= 0
                || c.neighborhoodDistanceUnit() == null) {
            issues.error("INVALID_SPATIAL_HOT_SPOT_NEIGHBORHOOD", "邻域距离必须是带单位的有限正数",
                    "configuration.neighborhoodDistance");
        }
        if (c.multipleTesting() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择多重检验策略", "configuration.multipleTesting");
        }
        CanvasNodeSupport.required(c.outputTableName(), "请输入输出表名", "configuration.outputTableName", issues);
        CanvasNodeSupport.required(c.binIdColumnName(), "请输入格网 ID 字段名", "configuration.binIdColumnName", issues);
        CanvasNodeSupport.required(c.binGeometryColumnName(), "请输入格网 Geometry 字段名", "configuration.binGeometryColumnName", issues);
        CanvasNodeSupport.required(c.pointCountColumnName(), "请输入点数字段名", "configuration.pointCountColumnName", issues);
        CanvasNodeSupport.required(c.analysisValueColumnName(), "请输入分析值字段名", "configuration.analysisValueColumnName", issues);
        CanvasNodeSupport.required(c.zScoreColumnName(), "请输入 z-score 字段名", "configuration.zScoreColumnName", issues);
        CanvasNodeSupport.required(c.pValueColumnName(), "请输入 p-value 字段名", "configuration.pValueColumnName", issues);
        CanvasNodeSupport.required(c.adjustedPValueColumnName(), "请输入调整后 p-value 字段名", "configuration.adjustedPValueColumnName", issues);
        CanvasNodeSupport.required(c.confidenceBinColumnName(), "请输入置信分级字段名", "configuration.confidenceBinColumnName", issues);
        if (!CanvasNodeSupport.blank(c.outputTableName()) && inputs.containsKey(c.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在：" + c.outputTableName(), "configuration.outputTableName");
        }
    }

    private static CanvasColumnSchema validatePoint(
            SparkCanvasTable source,
            SpatialHotSpotsConfiguration c,
            Map<String, CanvasColumnSchema> columns,
            CanvasNodeIssueSink issues
    ) {
        if (source.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error("BOUNDED_INPUT_REQUIRED", "热点分析只支持有界输入", "configuration.sourceTableName");
        }
        CanvasColumnSchema point = CanvasNodeSupport.blank(c.pointGeometryColumnName())
                ? null : columns.get(c.pointGeometryColumnName());
        if (!CanvasNodeSupport.blank(c.pointGeometryColumnName()) && point == null) {
            issues.error("COLUMN_NOT_FOUND", "Point Geometry 字段不存在：" + c.pointGeometryColumnName(),
                    "configuration.pointGeometryColumnName");
        } else if (point != null && (point.fieldType() != PlatformDataType.GEOMETRY || point.geometry() == null
                || point.geometry().kind() != GeometryKind.POINT
                || point.geometry().dimension() != CoordinateDimension.XY)) {
            issues.error("SPATIAL_POINT_XY_REQUIRED", "热点分析需要带完整元数据的 XY Point",
                    "configuration.pointGeometryColumnName");
        }
        return point;
    }

    private static CanvasColumnSchema validateAnalysis(
            SpatialHotSpotsConfiguration c,
            Map<String, CanvasColumnSchema> columns,
            CanvasNodeIssueSink issues
    ) {
        if (c.analysisSource() == SpatialHotSpotAnalysisSource.POINT_COUNT) {
            return null;
        }
        if (c.analysisSource() != SpatialHotSpotAnalysisSource.FIELD_SUM) return null;
        CanvasNodeSupport.required(c.analysisColumnName(), "请选择数值分析字段", "configuration.analysisColumnName", issues);
        CanvasColumnSchema analysis = CanvasNodeSupport.blank(c.analysisColumnName())
                ? null : columns.get(c.analysisColumnName());
        if (!CanvasNodeSupport.blank(c.analysisColumnName()) && analysis == null) {
            issues.error("COLUMN_NOT_FOUND", "热点分析字段不存在：" + c.analysisColumnName(),
                    "configuration.analysisColumnName");
        } else if (analysis != null && !numeric(analysis.fieldType())) {
            issues.error("NUMERIC_COLUMN_REQUIRED", "热点分析字段必须是数值类型", "configuration.analysisColumnName");
        }
        return analysis;
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
            if (column == null) issues.error("COLUMN_NOT_FOUND", "时间字段不存在：" + temporal.timeColumnName(),
                    "configuration.temporalSlicing.timeColumnName");
            else if (column.fieldType() != PlatformDataType.TIMESTAMP) issues.error("TIMESTAMP_COLUMN_REQUIRED",
                    "时间切片字段必须是 TIMESTAMP", "configuration.temporalSlicing.timeColumnName");
        }
        return result;
    }

    private static void validateResolvedDistances(
            SpatialHotSpotsConfiguration c,
            SpatialDistanceSupport.Resolution bin,
            SpatialDistanceSupport.Resolution neighborhood,
            CanvasNodeIssueSink issues
    ) {
        if (bin != null && (!bin.valid() || bin.angular())) {
            issues.error("SPATIAL_HOT_SPOT_PROJECTED_CRS_REQUIRED",
                    bin.valid() ? "热点分析需要投影 CRS" : bin.error(), "configuration.binSizeUnit");
        }
        if (neighborhood != null && (!neighborhood.valid() || neighborhood.angular())) {
            issues.error("SPATIAL_HOT_SPOT_PROJECTED_CRS_REQUIRED",
                    neighborhood.valid() ? "热点分析需要投影 CRS" : neighborhood.error(),
                    "configuration.neighborhoodDistanceUnit");
        }
        if (bin == null || neighborhood == null || !bin.valid() || !neighborhood.valid()) return;
        if (neighborhood.sourceCrsValue() <= bin.sourceCrsValue()) {
            issues.error("SPATIAL_HOT_SPOT_NEIGHBORHOOD_TOO_SMALL", "邻域距离必须严格大于方格大小",
                    "configuration.neighborhoodDistance");
            return;
        }
        double ratio = neighborhood.sourceCrsValue() / bin.sourceCrsValue();
        if (!Double.isFinite(ratio) || ratio > SpatialHotSpotsConfiguration.MAX_NEIGHBORHOOD_TO_BIN_RATIO) {
            issues.error("SPATIAL_HOT_SPOT_NEIGHBORHOOD_RATIO_EXCEEDED", "邻域距离与方格大小之比不能超过 64",
                    "configuration.neighborhoodDistance");
        }
    }

    private static void validateOutputNames(SpatialHotSpotsConfiguration c, CanvasNodeIssueSink issues) {
        Set<String> names = new HashSet<>();
        addName(c.binIdColumnName(), "configuration.binIdColumnName", names, issues);
        addName(c.binGeometryColumnName(), "configuration.binGeometryColumnName", names, issues);
        addName(c.pointCountColumnName(), "configuration.pointCountColumnName", names, issues);
        addName(c.analysisValueColumnName(), "configuration.analysisValueColumnName", names, issues);
        addName(c.zScoreColumnName(), "configuration.zScoreColumnName", names, issues);
        addName(c.pValueColumnName(), "configuration.pValueColumnName", names, issues);
        addName(c.adjustedPValueColumnName(), "configuration.adjustedPValueColumnName", names, issues);
        addName(c.confidenceBinColumnName(), "configuration.confidenceBinColumnName", names, issues);
        if (c.temporalSlicing() != null) {
            addName(c.temporalSlicing().windowStartColumnName(), "configuration.temporalSlicing.windowStartColumnName", names, issues);
            addName(c.temporalSlicing().windowEndColumnName(), "configuration.temporalSlicing.windowEndColumnName", names, issues);
        }
    }

    private static void addName(String name, String path, Set<String> names, CanvasNodeIssueSink issues) {
        if (!CanvasNodeSupport.blank(name) && !names.add(name.toLowerCase(Locale.ROOT))) {
            issues.error("DUPLICATE_COLUMN_NAME", "输出字段名重复：" + name, path);
        }
    }

    private static List<CanvasColumnSchema> outputSchema(
            SpatialHotSpotsConfiguration c,
            CanvasColumnSchema point
    ) {
        List<CanvasColumnSchema> columns = new ArrayList<>();
        columns.add(new CanvasColumnSchema(c.binIdColumnName(), PlatformDataType.STRING,
                224, null, null, false, null, false, false, "确定性热点格网标识", null));
        columns.add(new CanvasColumnSchema(c.binGeometryColumnName(), PlatformDataType.GEOMETRY,
                null, null, null, false, null, false, false, null,
                new GeometryTypeDefinition(GeometryKind.POLYGON, point.geometry().crs(), CoordinateDimension.XY)));
        if (c.temporalSlicing() != null) {
            columns.add(TrackNodeSupport.timestampColumn(c.temporalSlicing().windowStartColumnName(), false));
            columns.add(TrackNodeSupport.timestampColumn(c.temporalSlicing().windowEndColumnName(), false));
        }
        columns.add(TrackNodeSupport.longColumn(c.pointCountColumnName(), false));
        columns.add(TrackNodeSupport.doubleColumn(c.analysisValueColumnName(), false));
        columns.add(TrackNodeSupport.doubleColumn(c.zScoreColumnName(), false));
        columns.add(TrackNodeSupport.doubleColumn(c.pValueColumnName(), false));
        columns.add(TrackNodeSupport.doubleColumn(c.adjustedPValueColumnName(), false));
        columns.add(new CanvasColumnSchema(c.confidenceBinColumnName(), PlatformDataType.INTEGER,
                null, null, null, false, null, false, false, "-3 至 3 的热点/冷点置信分级", null));
        return List.copyOf(columns);
    }

    private static boolean numeric(PlatformDataType type) {
        return switch (type) {
            case BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, DECIMAL -> true;
            default -> false;
        };
    }
}
