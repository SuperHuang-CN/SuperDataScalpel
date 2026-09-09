package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SpatialCenterDispersionAnalysis;
import cn.superhuang.data.scalpel.contract.task.SpatialCenterDispersionConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialCenterDispersionKind;
import cn.superhuang.data.scalpel.contract.task.SpatialCenterDispersionNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.RelationalGroupedDataset;
import org.apache.spark.sql.Row;
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

public final class SpatialCenterDispersionNodeOperator implements CanvasNodeOperator {

    private static final String POINTS = "__datascalpel_center_points";
    private static final String TOTAL_WEIGHT = "__datascalpel_center_total_weight";
    private static final String MEAN_X = "__datascalpel_center_mean_x";
    private static final String MEAN_Y = "__datascalpel_center_mean_y";
    private static final String MEDIAN_X = "__datascalpel_center_median_x";
    private static final String MEDIAN_Y = "__datascalpel_center_median_y";
    private static final String VAR_X = "__datascalpel_center_var_x";
    private static final String VAR_Y = "__datascalpel_center_var_y";
    private static final String COV_XY = "__datascalpel_center_cov_xy";
    private static final String CENTRAL_POINT = "__datascalpel_center_central_point";

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_CENTER_DISPERSION;
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
        if (!(definition instanceof SpatialCenterDispersionNodeDefinition node)) {
            throw new IllegalArgumentException("SPATIAL_CENTER_DISPERSION operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        SpatialCenterDispersionConfiguration configuration = node.configuration();
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
        CanvasColumnSchema point = validateSource(source, configuration, columns, issues);
        List<CanvasColumnSchema> groupSchemas = validateGroups(configuration.groupByColumns(), columns, issues);
        CanvasColumnSchema weight = validateScalar(configuration.weightColumnName(), columns, true,
                "权重字段", "configuration.weightColumnName", issues);
        boolean requiresId = configuration.analyses() != null && configuration.analyses().stream().anyMatch(a -> a != null && a.kind() == SpatialCenterDispersionKind.CENTRAL_FEATURE);
        CanvasColumnSchema featureId = validateScalar(configuration.separateResults() && !requiresId ? null : configuration.featureIdColumnName(), columns, false,
                "要素唯一字段", "configuration.featureIdColumnName", issues);
        List<SpatialCenterDispersionAnalysis> analyses = validateAnalyses(configuration, point, featureId, issues);
        if (configuration.separateResults()) validateFeatureProjection(configuration.analyses(), columns, issues);
        if (issues.hasErrors() || point == null) return CanvasNodeOperationResult.invalid(inputSchemas);
        if (configuration.separateResults()) {
            issues.warning("SPATIAL_CENTER_GROUP_CAPACITY", requiresId
                    ? "中央要素按组进行二次距离计算，单组最多 5000 个有效要素、100 万总顶点；超限时安全失败"
                    : "中心分析在 Executor 内处理分组，单组最多 100000 个有效要素、100 万总顶点；超限时安全失败", "configuration.groupByColumns");
            return CenterAnalysisPlan.apply(configuration, source, groupSchemas, point, inputs);
        }

        Dataset<Row> base = prepareBase(source.dataset(), configuration, groupSchemas, weight);
        Dataset<Row> statistics = aggregateBase(base, groupSchemas);
        statistics = calculateMedianCenter(statistics);
        statistics = calculateDispersion(statistics);
        if (analyses.stream().anyMatch(item -> item.kind() == SpatialCenterDispersionKind.CENTRAL_FEATURE)) {
            statistics = joinCentralFeature(statistics, groupSchemas);
        }

        int srid = point.geometry().crs().code();
        Column meanPoint = point(statistics.col(MEAN_X), statistics.col(MEAN_Y), srid);
        Column medianPoint = point(statistics.col(MEDIAN_X), statistics.col(MEDIAN_Y), srid);
        List<Column> projection = new ArrayList<>();
        for (CanvasColumnSchema group : groupSchemas) {
            projection.add(statistics.col(CanvasNodeSupport.quoteIdentifier(group.name())));
        }
        for (SpatialCenterDispersionAnalysis analysis : analyses) {
            Column geometry = switch (analysis.kind()) {
                case MEAN_CENTER -> meanPoint;
                case MEDIAN_CENTER -> medianPoint;
                case CENTRAL_FEATURE -> statistics.col(CENTRAL_POINT);
                case STANDARD_DISTANCE -> standardDistance(statistics, meanPoint, analysis.standardDeviations());
                case DIRECTIONAL_ELLIPSE -> directionalEllipse(statistics, analysis.standardDeviations(), srid);
            };
            projection.add(geometry.alias(analysis.outputColumnName()));
        }
        Dataset<Row> result = statistics.select(projection.toArray(Column[]::new));

        List<CanvasColumnSchema> outputColumns = new ArrayList<>();
        outputColumns.addAll(groupSchemas);
        for (SpatialCenterDispersionAnalysis analysis : analyses) {
            GeometryKind kind = switch (analysis.kind()) {
                case MEAN_CENTER, MEDIAN_CENTER, CENTRAL_FEATURE -> GeometryKind.POINT;
                case STANDARD_DISTANCE, DIRECTIONAL_ELLIPSE -> GeometryKind.POLYGON;
            };
            outputColumns.add(new CanvasColumnSchema(
                    analysis.outputColumnName(), PlatformDataType.GEOMETRY,
                    null, null, null, true, null, false, false, null,
                    new GeometryTypeDefinition(kind, point.geometry().crs(), CoordinateDimension.XY)));
        }
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(), null, outputColumns, CanvasDatasetKind.BOUNDED, null, null);
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, result));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static Dataset<Row> prepareBase(
            Dataset<Row> source,
            SpatialCenterDispersionConfiguration configuration,
            List<CanvasColumnSchema> groups,
            CanvasColumnSchema weightSchema
    ) {
        Column geometry = source.col(CanvasNodeSupport.quoteIdentifier(configuration.pointGeometryColumnName()));
        Column weight = weightSchema == null
                ? functions.lit(1d)
                : source.col(CanvasNodeSupport.quoteIdentifier(weightSchema.name())).cast("double");
        weight = functions.when(weight.lt(0), functions.raise_error(
                functions.lit("SPATIAL_CENTER_NEGATIVE_WEIGHT"))).otherwise(weight);
        List<Column> projection = new ArrayList<>();
        groups.forEach(group -> projection.add(source.col(CanvasNodeSupport.quoteIdentifier(group.name()))));
        Column featureId = CanvasNodeSupport.blank(configuration.featureIdColumnName())
                ? functions.lit(null).cast("string")
                : source.col(CanvasNodeSupport.quoteIdentifier(configuration.featureIdColumnName())).cast("string");
        projection.add(functions.struct(
                st_functions.ST_X(geometry).alias("x"),
                st_functions.ST_Y(geometry).alias("y"),
                weight.alias("weight"),
                geometry.alias("geometry"),
                featureId.alias("feature_id")).alias("point"));
        return source.filter(geometry.isNotNull().and(weight.isNotNull())).select(projection.toArray(Column[]::new));
    }

    private static Dataset<Row> aggregateBase(Dataset<Row> base, List<CanvasColumnSchema> groups) {
        Column point = base.col("point");
        Column weight = point.getField("weight");
        Column weightedX = point.getField("x").multiply(weight);
        Column weightedY = point.getField("y").multiply(weight);
        List<Column> aggregations = List.of(
                functions.collect_list(point).alias(POINTS),
                functions.sum(weight).alias(TOTAL_WEIGHT),
                functions.sum(weightedX).divide(functions.sum(weight)).alias(MEAN_X),
                functions.sum(weightedY).divide(functions.sum(weight)).alias(MEAN_Y));
        if (groups.isEmpty()) {
            return base.agg(aggregations.getFirst(), aggregations.subList(1, aggregations.size()).toArray(Column[]::new));
        }
        Column[] groupColumns = groups.stream()
                .map(group -> base.col(CanvasNodeSupport.quoteIdentifier(group.name())))
                .toArray(Column[]::new);
        RelationalGroupedDataset grouped = base.groupBy(groupColumns);
        return grouped.agg(aggregations.getFirst(), aggregations.subList(1, aggregations.size()).toArray(Column[]::new));
    }

    private static Dataset<Row> calculateMedianCenter(Dataset<Row> statistics) {
        Dataset<Row> result = statistics.withColumn(MEDIAN_X, statistics.col(MEAN_X))
                .withColumn(MEDIAN_Y, statistics.col(MEAN_Y));
        for (int iteration = 0; iteration < 16; iteration++) {
            Column points = result.col(POINTS);
            Column currentX = result.col(MEDIAN_X);
            Column currentY = result.col(MEDIAN_Y);
            Column denominator = functions.aggregate(points, functions.lit(0d), (sum, point) -> {
                Column distance = functions.greatest(functions.sqrt(
                        functions.pow(point.getField("x").minus(currentX), 2d)
                                .plus(functions.pow(point.getField("y").minus(currentY), 2d))),
                        functions.lit(1e-9));
                return sum.plus(point.getField("weight").divide(distance));
            });
            Column nextX = functions.aggregate(points, functions.lit(0d), (sum, point) -> {
                Column distance = functions.greatest(functions.sqrt(
                        functions.pow(point.getField("x").minus(currentX), 2d)
                                .plus(functions.pow(point.getField("y").minus(currentY), 2d))),
                        functions.lit(1e-9));
                return sum.plus(point.getField("x").multiply(point.getField("weight")).divide(distance));
            }).divide(denominator);
            Column nextY = functions.aggregate(points, functions.lit(0d), (sum, point) -> {
                Column distance = functions.greatest(functions.sqrt(
                        functions.pow(point.getField("x").minus(currentX), 2d)
                                .plus(functions.pow(point.getField("y").minus(currentY), 2d))),
                        functions.lit(1e-9));
                return sum.plus(point.getField("y").multiply(point.getField("weight")).divide(distance));
            }).divide(denominator);
            Dataset<Row> staged = result.withColumn("__median_next_x", nextX)
                    .withColumn("__median_next_y", nextY);
            result = staged
                    .withColumn(MEDIAN_X, staged.col("__median_next_x"))
                    .withColumn(MEDIAN_Y, staged.col("__median_next_y"))
                    .drop("__median_next_x", "__median_next_y");
        }
        return result;
    }

    private static Dataset<Row> calculateDispersion(Dataset<Row> statistics) {
        Column points = statistics.col(POINTS);
        Column meanX = statistics.col(MEAN_X);
        Column meanY = statistics.col(MEAN_Y);
        Column totalWeight = statistics.col(TOTAL_WEIGHT);
        Column varX = functions.aggregate(points, functions.lit(0d), (sum, point) -> sum.plus(
                point.getField("weight").multiply(functions.pow(point.getField("x").minus(meanX), 2d))))
                .divide(totalWeight);
        Column varY = functions.aggregate(points, functions.lit(0d), (sum, point) -> sum.plus(
                point.getField("weight").multiply(functions.pow(point.getField("y").minus(meanY), 2d))))
                .divide(totalWeight);
        Column covariance = functions.aggregate(points, functions.lit(0d), (sum, point) -> sum.plus(
                point.getField("weight")
                        .multiply(point.getField("x").minus(meanX))
                        .multiply(point.getField("y").minus(meanY))))
                .divide(totalWeight);
        return statistics.withColumn(VAR_X, varX).withColumn(VAR_Y, varY).withColumn(COV_XY, covariance);
    }

    private static Dataset<Row> joinCentralFeature(Dataset<Row> statistics, List<CanvasColumnSchema> groups) {
        Dataset<Row> exploded = statistics.withColumn("__candidate", functions.explode(statistics.col(POINTS)));
        Column candidate = exploded.col("__candidate");
        Column score = functions.aggregate(exploded.col(POINTS), functions.lit(0d), (sum, point) -> sum.plus(
                functions.sqrt(functions.pow(point.getField("x").minus(candidate.getField("x")), 2d)
                        .plus(functions.pow(point.getField("y").minus(candidate.getField("y")), 2d)))
                        .multiply(point.getField("weight"))));
        Column[] partitions = groups.stream()
                .map(group -> exploded.col(CanvasNodeSupport.quoteIdentifier(group.name())))
                .toArray(Column[]::new);
        WindowSpec rankWindow = Window.partitionBy(partitions)
                .orderBy(score.asc(), candidate.getField("feature_id").asc_nulls_last());
        Dataset<Row> central = exploded.withColumn("__central_rank", functions.row_number().over(rankWindow))
                .filter(functions.col("__central_rank").equalTo(1));
        List<Column> select = new ArrayList<>();
        for (CanvasColumnSchema group : groups) {
            select.add(central.col(CanvasNodeSupport.quoteIdentifier(group.name())));
        }
        select.add(central.col("__candidate").getField("geometry").alias(CENTRAL_POINT));
        central = central.select(select.toArray(Column[]::new)).alias("central");
        Dataset<Row> stats = statistics.alias("stats");
        if (groups.isEmpty()) {
            return stats.join(central, functions.lit(true), "left")
                    .select("stats.*", "central." + CENTRAL_POINT);
        }
        Column condition = null;
        for (CanvasColumnSchema group : groups) {
            Column equality = stats.col(CanvasNodeSupport.quoteIdentifier(group.name()))
                    .eqNullSafe(central.col(CanvasNodeSupport.quoteIdentifier(group.name())));
            condition = condition == null ? equality : condition.and(equality);
        }
        return stats.join(central, condition, "left").select("stats.*", "central." + CENTRAL_POINT);
    }

    private static Column point(Column x, Column y, int srid) {
        return st_functions.ST_SetSRID(st_constructors.ST_Point(x, y), functions.lit(srid));
    }

    private static Column standardDistance(Dataset<Row> statistics, Column meanPoint, int deviations) {
        Column radius = functions.sqrt(statistics.col(VAR_X).plus(statistics.col(VAR_Y))).multiply(deviations);
        return st_functions.ST_Buffer(meanPoint, radius);
    }

    private static Column directionalEllipse(Dataset<Row> statistics, int deviations, int srid) {
        Column varX = statistics.col(VAR_X);
        Column varY = statistics.col(VAR_Y);
        Column covariance = statistics.col(COV_XY);
        Column root = functions.sqrt(functions.pow(varX.minus(varY), 2d)
                .plus(functions.lit(4d).multiply(functions.pow(covariance, 2d))));
        Column major = functions.sqrt(varX.plus(varY).plus(root)).multiply(deviations);
        Column minor = functions.sqrt(varX.plus(varY).minus(root)).multiply(deviations);
        Column angle = functions.lit(.5d).multiply(functions.atan2(covariance.multiply(2d), varX.minus(varY)));
        Column unitCircle = st_functions.ST_Buffer(st_constructors.ST_Point(functions.lit(0d), functions.lit(0d)), functions.lit(1d));
        Column scaled = st_functions.ST_Scale(unitCircle, major, minor);
        Column rotated = st_functions.ST_Rotate(scaled, angle);
        Column translated = st_functions.ST_Translate(rotated, statistics.col(MEAN_X), statistics.col(MEAN_Y));
        return st_functions.ST_SetSRID(translated, functions.lit(srid));
    }

    private static void validateBase(
            SpatialCenterDispersionConfiguration configuration,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeIssueSink issues
    ) {
        CanvasNodeSupport.required(configuration.sourceTableName(), "请选择来源表", "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(configuration.pointGeometryColumnName(), "请选择点 Geometry 字段", "configuration.pointGeometryColumnName", issues);
        if (!configuration.separateResults()) CanvasNodeSupport.required(configuration.outputTableName(), "请输入输出表名", "configuration.outputTableName", issues);
        if (configuration.groupByColumns() == null) issues.error("INVALID_CENTER_GROUP_COLUMNS", "分组字段必须是数组", "configuration.groupByColumns");
        if (configuration.analyses() == null || configuration.analyses().isEmpty()) {
            issues.error("SPATIAL_CENTER_ANALYSES_REQUIRED", "至少配置一个中心或离散分析项", "configuration.analyses");
        } else if (configuration.analyses().size() > SpatialCenterDispersionConfiguration.MAX_ANALYSES) {
            issues.error("SPATIAL_CENTER_ANALYSIS_COUNT_EXCEEDED", "分析项不能超过 16 个", "configuration.analyses");
        }
        if (!configuration.separateResults() && !CanvasNodeSupport.blank(configuration.outputTableName()) && inputs.containsKey(configuration.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在：" + configuration.outputTableName(), "configuration.outputTableName");
        }
        if (configuration.separateResults() && configuration.analyses() != null) {
            Set<String> names = new HashSet<>(inputs.keySet());
            for (int i = 0; i < configuration.analyses().size(); i++) {
                var a = configuration.analyses().get(i); if (a == null) continue;
                String path = "configuration.analyses[" + i + "].outputTableName";
                CanvasNodeSupport.required(a.outputTableName(), "请输入独立分析结果表名", path, issues);
                if (!CanvasNodeSupport.blank(a.outputTableName()) && !names.add(a.outputTableName()))
                    issues.error("DUPLICATE_TABLE_NAME", "分析结果表名与入口表或其他分析结果重复", path);
            }
        }
    }

    private static CanvasColumnSchema validateSource(
            SparkCanvasTable source,
            SpatialCenterDispersionConfiguration configuration,
            Map<String, CanvasColumnSchema> columns,
            CanvasNodeIssueSink issues
    ) {
        if (source.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error("BOUNDED_INPUT_REQUIRED", "中心与离散统计只支持有界输入", "configuration.sourceTableName");
        }
        CanvasColumnSchema point = CanvasNodeSupport.blank(configuration.pointGeometryColumnName()) ? null : columns.get(configuration.pointGeometryColumnName());
        if (!CanvasNodeSupport.blank(configuration.pointGeometryColumnName()) && point == null) {
            issues.error("COLUMN_NOT_FOUND", "点 Geometry 字段不存在：" + configuration.pointGeometryColumnName(), "configuration.pointGeometryColumnName");
        } else if (point != null && (point.fieldType() != PlatformDataType.GEOMETRY || point.geometry() == null
                || !configuration.separateResults() && point.geometry().kind() != GeometryKind.POINT || point.geometry().dimension() != CoordinateDimension.XY)) {
            issues.error("SPATIAL_POINT_XY_REQUIRED", configuration.separateResults() ? "中心分析需要带完整元数据的 XY Geometry" : "中心与离散统计需要带完整元数据的 XY Point", "configuration.pointGeometryColumnName");
        }
        if (point != null && point.geometry() != null) {
            if (configuration.separateResults() && point.geometry().kind() == GeometryKind.GEOMETRYCOLLECTION)
                issues.error("SPATIAL_CENTER_GEOMETRY_INVALID", "中心分析不支持混合 GeometryCollection，请先提取点、线或面", "configuration.pointGeometryColumnName");
            SpatialDistanceSupport.Resolution projected = SpatialDistanceSupport.resolve(
                    1d, SpatialDistanceUnit.METERS, point.geometry().crs());
            if (!projected.valid() || projected.angular()) {
                issues.error("SPATIAL_PROJECTED_CRS_REQUIRED", "中心与离散统计需要投影 CRS", "configuration.pointGeometryColumnName");
            }
        }
        return point;
    }

    private static List<CanvasColumnSchema> validateGroups(
            List<String> configured,
            Map<String, CanvasColumnSchema> columns,
            CanvasNodeIssueSink issues
    ) {
        if (configured == null) return List.of();
        if (configured.size() > SpatialCenterDispersionConfiguration.MAX_GROUP_COLUMNS) {
            issues.error("SPATIAL_CENTER_GROUP_COUNT_EXCEEDED", "分组字段不能超过 8 个", "configuration.groupByColumns");
        }
        Set<String> names = new HashSet<>();
        List<CanvasColumnSchema> result = new ArrayList<>();
        for (int index = 0; index < configured.size(); index++) {
            String name = configured.get(index);
            String path = "configuration.groupByColumns[" + index + "]";
            if (CanvasNodeSupport.blank(name)) {
                issues.error("REQUIRED_CONFIGURATION", "分组字段不能为空", path);
                continue;
            }
            if (!names.add(name.toLowerCase(Locale.ROOT))) issues.error("DUPLICATE_COLUMN_NAME", "分组字段重复：" + name, path);
            CanvasColumnSchema column = columns.get(name);
            if (column == null) issues.error("COLUMN_NOT_FOUND", "分组字段不存在：" + name, path);
            else if (column.fieldType() == PlatformDataType.GEOMETRY) issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED", "Geometry 不能作为分组字段", path);
            else result.add(column);
        }
        return List.copyOf(result);
    }

    private static CanvasColumnSchema validateScalar(
            String name,
            Map<String, CanvasColumnSchema> columns,
            boolean numeric,
            String label,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (CanvasNodeSupport.blank(name)) return null;
        CanvasColumnSchema column = columns.get(name);
        if (column == null) issues.error("COLUMN_NOT_FOUND", label + "不存在：" + name, path);
        else if (column.fieldType() == PlatformDataType.GEOMETRY) issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED", label + "不能是 Geometry", path);
        else if (numeric && !numeric(column.fieldType())) issues.error("NUMERIC_COLUMN_REQUIRED", label + "必须是数值字段", path);
        return column;
    }

    private static List<SpatialCenterDispersionAnalysis> validateAnalyses(
            SpatialCenterDispersionConfiguration configuration,
            CanvasColumnSchema point,
            CanvasColumnSchema featureId,
            CanvasNodeIssueSink issues
    ) {
        if (configuration.analyses() == null) return List.of();
        Set<UUID> ids = new HashSet<>();
        Set<SpatialCenterDispersionKind> kinds = new HashSet<>();
        Set<String> names = new HashSet<>();
        if (configuration.groupByColumns() != null) configuration.groupByColumns().stream()
                .filter(java.util.Objects::nonNull).forEach(name -> names.add(name.toLowerCase(Locale.ROOT)));
        List<SpatialCenterDispersionAnalysis> result = new ArrayList<>();
        for (int index = 0; index < configuration.analyses().size(); index++) {
            SpatialCenterDispersionAnalysis analysis = configuration.analyses().get(index);
            String path = "configuration.analyses[" + index + "]";
            if (analysis == null || analysis.kind() == null) {
                issues.error("INVALID_SPATIAL_CENTER_ANALYSIS", "分析项不完整", path);
                continue;
            }
            try {
                if (!ids.add(UUID.fromString(analysis.analysisId()))) issues.error("DUPLICATE_SPATIAL_CENTER_ANALYSIS_ID", "分析项 ID 重复", path + ".analysisId");
            } catch (RuntimeException exception) {
                issues.error("INVALID_SPATIAL_CENTER_ANALYSIS_ID", "分析项 ID 必须是 UUID", path + ".analysisId");
            }
            if (!kinds.add(analysis.kind())) issues.error("DUPLICATE_SPATIAL_CENTER_KIND", "同一分析类型只能配置一次", path + ".kind");
            if (configuration.separateResults()) {
                names.clear();
                boolean projected = analysis.kind() == SpatialCenterDispersionKind.CENTRAL_FEATURE && analysis.centralFeatureColumns() != null;
                if (!projected && configuration.groupByColumns() != null) configuration.groupByColumns().stream().filter(java.util.Objects::nonNull).forEach(name -> names.add(name.toLowerCase(Locale.ROOT)));
                if (!projected && analysis.kind() == SpatialCenterDispersionKind.CENTRAL_FEATURE && featureId != null) names.add(featureId.name().toLowerCase(Locale.ROOT));
            }
            CanvasNodeSupport.required(analysis.outputColumnName(), "请输入分析输出字段名", path + ".outputColumnName", issues);
            if (!CanvasNodeSupport.blank(analysis.outputColumnName()) && !names.add(analysis.outputColumnName().toLowerCase(Locale.ROOT))) {
                issues.error("DUPLICATE_COLUMN_NAME", "输出字段名重复：" + analysis.outputColumnName(), path + ".outputColumnName");
            }
            boolean deviation = analysis.kind() == SpatialCenterDispersionKind.STANDARD_DISTANCE
                    || analysis.kind() == SpatialCenterDispersionKind.DIRECTIONAL_ELLIPSE;
            if (deviation && (analysis.standardDeviations() == null || analysis.standardDeviations() < 1
                    || analysis.standardDeviations() > 3)) {
                issues.error("INVALID_STANDARD_DEVIATIONS", "标准差倍数必须为 1、2 或 3", path + ".standardDeviations");
            }
            if (!deviation && analysis.standardDeviations() != null) {
                issues.error("INVALID_STANDARD_DEVIATIONS", "当前分析类型不能配置标准差倍数", path + ".standardDeviations");
            }
            if (analysis.kind() == SpatialCenterDispersionKind.CENTRAL_FEATURE
                    && CanvasNodeSupport.blank(configuration.featureIdColumnName())) {
                issues.error("CENTER_FEATURE_ID_REQUIRED", "中央要素分析需要要素唯一字段", "configuration.featureIdColumnName");
            }
            result.add(analysis);
        }
        return List.copyOf(result);
    }

    private static void validateFeatureProjection(List<SpatialCenterDispersionAnalysis> analyses,
            Map<String, CanvasColumnSchema> columns, CanvasNodeIssueSink issues) {
        if (analyses == null) return;
        for (int i = 0; i < analyses.size(); i++) {
            var analysis = analyses.get(i);
            if (analysis == null || analysis.kind() != SpatialCenterDispersionKind.CENTRAL_FEATURE || analysis.centralFeatureColumns() == null) continue;
            Set<String> sources = new HashSet<>();
            Set<String> outputs = new HashSet<>();
            if (analysis.outputColumnName() != null) outputs.add(analysis.outputColumnName().toLowerCase(Locale.ROOT));
            for (int j = 0; j < analysis.centralFeatureColumns().size(); j++) {
                var field = analysis.centralFeatureColumns().get(j);
                String path = "configuration.analyses[" + i + "].centralFeatureColumns[" + j + "]";
                if (field == null) { issues.error("INVALID_CENTER_FEATURE_COLUMN", "字段投影不能为空", path); continue; }
                if (!field.included()) continue;
                CanvasNodeSupport.required(field.sourceColumnName(), "请选择原要素字段", path + ".sourceColumnName", issues);
                CanvasNodeSupport.required(field.outputColumnName(), "请输入输出字段名", path + ".outputColumnName", issues);
                if (!CanvasNodeSupport.blank(field.sourceColumnName())) {
                    if (!columns.containsKey(field.sourceColumnName())) issues.error("COLUMN_NOT_FOUND", "原要素字段不存在", path + ".sourceColumnName");
                    if (!sources.add(field.sourceColumnName().toLowerCase(Locale.ROOT))) issues.error("DUPLICATE_COLUMN_NAME", "同一原要素字段不能重复投影", path + ".sourceColumnName");
                }
                if (!CanvasNodeSupport.blank(field.outputColumnName()) && !outputs.add(field.outputColumnName().toLowerCase(Locale.ROOT)))
                    issues.error("DUPLICATE_COLUMN_NAME", "输出字段与其他投影或结果 Geometry 重名", path + ".outputColumnName");
            }
        }
    }

    private static boolean numeric(PlatformDataType type) {
        return type == PlatformDataType.BYTE || type == PlatformDataType.SHORT
                || type == PlatformDataType.INTEGER || type == PlatformDataType.LONG
                || type == PlatformDataType.FLOAT || type == PlatformDataType.DOUBLE
                || type == PlatformDataType.DECIMAL;
    }
}
