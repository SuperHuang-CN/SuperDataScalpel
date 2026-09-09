package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.JoinOutputColumnSource;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialNearestConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialNearestNodeDefinition;
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
import org.apache.spark.sql.sedona_sql.expressions.st_predicates;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SpatialNearestNodeOperator implements CanvasNodeOperator {

    private static final String SOURCE_ROW_ID = "__datascalpel_nearest_source_row_id";
    private static final String INTERNAL_DISTANCE = "__datascalpel_nearest_distance";
    private static final String INTERNAL_RANK = "__datascalpel_nearest_rank";

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_NEAREST;
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
        if (!(definition instanceof SpatialNearestNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "SPATIAL_NEAREST operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        SpatialNearestConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        CanvasNodeIssueSink issues = context.issues();
        requireConfiguration(configuration, inputs, issues);

        SparkCanvasTable source = CanvasNodeSupport.blank(configuration.sourceTableName()) ? null : inputs.get(configuration.sourceTableName());
        SparkCanvasTable candidate = CanvasNodeSupport.blank(configuration.candidateTableName()) ? null : inputs.get(configuration.candidateTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error("TABLE_NOT_FOUND", "来源表不在上游数据中："
                    + configuration.sourceTableName(), "configuration.sourceTableName");
        }
        if (!CanvasNodeSupport.blank(configuration.candidateTableName()) && candidate == null) {
            issues.error("TABLE_NOT_FOUND", "候选表不在上游数据中："
                    + configuration.candidateTableName(), "configuration.candidateTableName");
        }
        if (source == null || candidate == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        if (source.schema().datasetKind() != CanvasDatasetKind.BOUNDED
                || candidate.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error("BOUNDED_INPUT_REQUIRED", "空间最近邻只支持有界输入", "configuration");
        }

        Map<String, CanvasColumnSchema> sourceColumns = CanvasNodeSupport.columns(source.schema());
        Map<String, CanvasColumnSchema> candidateColumns = CanvasNodeSupport.columns(candidate.schema());
        CanvasColumnSchema sourceGeometryColumn = validateGeometry(
                configuration.sourceGeometryColumnName(), sourceColumns,
                "来源", "configuration.sourceGeometryColumnName", issues);
        CanvasColumnSchema candidateGeometryColumn = validateGeometry(
                configuration.candidateGeometryColumnName(), candidateColumns,
                "候选", "configuration.candidateGeometryColumnName", issues);
        CanvasColumnSchema candidateId = CanvasNodeSupport.blank(configuration.candidateIdColumnName()) ? null : candidateColumns.get(configuration.candidateIdColumnName());
        if (!CanvasNodeSupport.blank(configuration.candidateIdColumnName()) && candidateId == null) {
            issues.error("COLUMN_NOT_FOUND", "候选唯一字段不存在："
                    + configuration.candidateIdColumnName(), "configuration.candidateIdColumnName");
        } else if (candidateId != null && candidateId.fieldType() == PlatformDataType.GEOMETRY) {
            issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                    "候选唯一字段不能是 Geometry", "configuration.candidateIdColumnName");
        } else if (candidateId != null && !configuration.usesExactMatching()) {
            issues.warning("CANDIDATE_ID_UNIQUENESS_NOT_VERIFIED",
                    "候选字段的唯一性由运行数据保证；相同距离和相同 ID 仍可能没有稳定顺序",
                    "configuration.candidateIdColumnName");
        }

        GeometryTypeDefinition sourceGeometry = sourceGeometryColumn == null
                ? null : sourceGeometryColumn.geometry();
        GeometryTypeDefinition candidateGeometry = candidateGeometryColumn == null
                ? null : candidateGeometryColumn.geometry();
        validateGeometryPair(configuration, sourceGeometry, candidateGeometry, issues);
        List<JoinOutputColumnSupport.ResolvedOutputColumn> resolvedOutputs =
                JoinOutputColumnSupport.validate(
                        configuration.outputColumns(), sourceColumns, candidateColumns, issues);
        validateResultColumnNames(configuration, resolvedOutputs, issues);
        NearestExactPlan.validate(configuration, source, candidate, resolvedOutputs, inputs, issues);

        DistanceResolution distances = sourceGeometry == null
                ? null : resolveDistances(configuration, sourceGeometry, issues);
        if (issues.hasErrors() || distances == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        if (configuration.usesExactMatching()) return NearestExactPlan.apply(configuration, source, candidate, inputs, resolvedOutputs,
                distances.maximumSourceValue(), distances.outputUnitFactor());

        Dataset<Row> sourceDataset = source.dataset()
                .withColumn(SOURCE_ROW_ID, functions.monotonically_increasing_id())
                .alias("nearest_source");
        Dataset<Row> candidateDataset = candidate.dataset().alias("nearest_candidate");
        Column sourceGeometryExpression = sourceDataset.col(
                CanvasNodeSupport.quoteIdentifier(configuration.sourceGeometryColumnName()));
        Column candidateGeometryExpression = candidateDataset.col(
                CanvasNodeSupport.quoteIdentifier(configuration.candidateGeometryColumnName()));
        boolean geodesic = configuration.distanceMethod() == SpatialDistanceMethod.GEODESIC;
        Column joinCondition = st_predicates.ST_KNN(
                sourceGeometryExpression,
                candidateGeometryExpression,
                functions.lit(configuration.nearestCount()),
                functions.lit(geodesic)
        );
        if (distances.maximumSourceValue() != null) {
            joinCondition = joinCondition.and(st_predicates.ST_DWithin(
                    sourceGeometryExpression,
                    candidateGeometryExpression,
                    functions.lit(distances.maximumSourceValue()),
                    functions.lit(geodesic)
            ));
        }
        Dataset<Row> joined = sourceDataset.join(
                candidateDataset,
                joinCondition,
                configuration.includeUnmatched() ? "left_outer" : "inner"
        );
        Column rawDistance = geodesic
                ? st_functions.ST_DistanceSpheroid(sourceGeometryExpression, candidateGeometryExpression)
                : st_functions.ST_Distance(sourceGeometryExpression, candidateGeometryExpression);
        Column outputDistance = rawDistance.divide(functions.lit(distances.outputUnitFactor()));
        WindowSpec rankingWindow = Window.partitionBy(sourceDataset.col(SOURCE_ROW_ID))
                .orderBy(outputDistance.asc_nulls_last(), candidateDataset.col(
                        CanvasNodeSupport.quoteIdentifier(configuration.candidateIdColumnName())).asc_nulls_last());
        Column matched = candidateGeometryExpression.isNotNull();
        Dataset<Row> ranked = joined
                .withColumn(INTERNAL_DISTANCE, functions.when(matched, outputDistance))
                .withColumn(INTERNAL_RANK, functions.when(matched, functions.row_number().over(rankingWindow)));

        List<Column> projection = new ArrayList<>(resolvedOutputs.size() + 2);
        List<CanvasColumnSchema> outputColumns = new ArrayList<>(resolvedOutputs.size() + 2);
        for (JoinOutputColumnSupport.ResolvedOutputColumn output : resolvedOutputs) {
            Dataset<Row> side = output.sourceSide() == JoinOutputColumnSource.LEFT
                    ? sourceDataset : candidateDataset;
            projection.add(side.col(CanvasNodeSupport.quoteIdentifier(output.sourceColumn().name()))
                    .alias(output.outputColumnName()));
            CanvasColumnSchema schema = JoinOutputColumnSupport.copyWithName(
                    output.sourceColumn(), output.outputColumnName());
            outputColumns.add(output.sourceSide() == JoinOutputColumnSource.RIGHT
                    && configuration.includeUnmatched() ? nullable(schema) : schema);
        }
        projection.add(ranked.col(INTERNAL_DISTANCE).alias(configuration.distanceColumnName()));
        outputColumns.add(decimalColumn(configuration.distanceColumnName(),
                configuration.includeUnmatched()));
        if (!CanvasNodeSupport.blank(configuration.rankColumnName())) {
            projection.add(ranked.col(INTERNAL_RANK).alias(configuration.rankColumnName()));
            outputColumns.add(integerColumn(configuration.rankColumnName(),
                    configuration.includeUnmatched()));
        }

        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(), null, outputColumns,
                CanvasDatasetKind.BOUNDED, null, null);
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(
                outputSchema, ranked.select(projection.toArray(Column[]::new))));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void requireConfiguration(
            SpatialNearestConfiguration configuration,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeIssueSink issues
    ) {
        CanvasNodeSupport.required(configuration.sourceTableName(), "请选择来源表",
                "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(configuration.sourceGeometryColumnName(), "请选择来源 Geometry 字段",
                "configuration.sourceGeometryColumnName", issues);
        CanvasNodeSupport.required(configuration.candidateTableName(), "请选择候选表",
                "configuration.candidateTableName", issues);
        CanvasNodeSupport.required(configuration.candidateGeometryColumnName(), "请选择候选 Geometry 字段",
                "configuration.candidateGeometryColumnName", issues);
        CanvasNodeSupport.required(configuration.candidateIdColumnName(), "请选择候选唯一字段",
                "configuration.candidateIdColumnName", issues);
        CanvasNodeSupport.required(configuration.outputTableName(), "请输入输出表名",
                "configuration.outputTableName", issues);
        CanvasNodeSupport.required(configuration.distanceColumnName(), "请输入距离字段名",
                "configuration.distanceColumnName", issues);
        if (configuration.distanceMethod() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择距离方法", "configuration.distanceMethod");
        }
        if (configuration.nearestCount() < 1
                || configuration.nearestCount() > SpatialNearestConfiguration.MAX_NEAREST_COUNT) {
            issues.error("INVALID_SPATIAL_NEAREST_COUNT", "最近数量必须在 1 到 100 之间",
                    "configuration.nearestCount");
        }
        if (configuration.maximumDistance() == null
                && configuration.maximumDistanceUnit() != null) {
            issues.error("INVALID_SPATIAL_DISTANCE_CONFIGURATION",
                    "未设置最大距离时单位必须为空", "configuration.maximumDistanceUnit");
        } else if (configuration.maximumDistance() != null) {
            if (!Double.isFinite(configuration.maximumDistance())
                    || configuration.maximumDistance() <= 0) {
                issues.error("INVALID_SPATIAL_DISTANCE_CONFIGURATION", "最大距离必须是有限正数",
                        "configuration.maximumDistance");
            }
            if (configuration.maximumDistanceUnit() == null) {
                issues.error("INVALID_SPATIAL_DISTANCE_CONFIGURATION", "设置最大距离后必须选择单位",
                        "configuration.maximumDistanceUnit");
            }
        }
        if (configuration.distanceOutputUnit() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择距离输出单位",
                    "configuration.distanceOutputUnit");
        }
        if (configuration.rankColumnName() != null
                && configuration.rankColumnName().isBlank()) {
            issues.error("INVALID_COLUMN_NAME", "排名字段名不能为空白",
                    "configuration.rankColumnName");
        }
        if (!CanvasNodeSupport.blank(configuration.sourceTableName())
                && configuration.sourceTableName().equals(configuration.candidateTableName())) {
            issues.error("INVALID_JOIN_TABLE", "来源表和候选表不能相同",
                    "configuration.candidateTableName");
        }
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在："
                    + configuration.outputTableName(), "configuration.outputTableName");
        }
    }

    private static CanvasColumnSchema validateGeometry(
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
            SpatialNearestConfiguration configuration,
            GeometryTypeDefinition source,
            GeometryTypeDefinition candidate,
            CanvasNodeIssueSink issues
    ) {
        if (source == null || candidate == null) return;
        if (!source.crs().equals(candidate.crs())) {
            issues.error("SPATIAL_CRS_MISMATCH", "来源与候选 Geometry 的 CRS 不一致",
                    "configuration.candidateGeometryColumnName");
        }
        if (source.dimension() != candidate.dimension()) {
            issues.error("SPATIAL_DIMENSION_MISMATCH", "来源与候选 Geometry 的坐标维度不一致",
                    "configuration.candidateGeometryColumnName");
        }
        if (configuration.distanceMethod() == SpatialDistanceMethod.GEODESIC
                && (!("EPSG".equals(source.crs().authority()) && source.crs().code() == 4326)
                || !("EPSG".equals(candidate.crs().authority()) && candidate.crs().code() == 4326))) {
            issues.error("GEODESIC_DISTANCE_REQUIRES_WGS84",
                    "测地线距离仅支持 EPSG:4326 XY", "configuration.distanceMethod");
        }
    }

    private static DistanceResolution resolveDistances(
            SpatialNearestConfiguration configuration,
            GeometryTypeDefinition geometry,
            CanvasNodeIssueSink issues
    ) {
        boolean geodesic = configuration.distanceMethod() == SpatialDistanceMethod.GEODESIC;
        Double maximum = null;
        if (configuration.maximumDistance() != null && configuration.maximumDistanceUnit() != null) {
            if (geodesic) {
                maximum = configuration.maximumDistance()
                        * SpatialDistanceSupport.metresPerConfiguredUnit(
                        configuration.maximumDistanceUnit());
                if (!Double.isFinite(maximum)) issues.error("SPATIAL_DISTANCE_UNIT_UNSUPPORTED", "最大距离换算后不是有限数值", "configuration.maximumDistanceUnit");
            } else {
                SpatialDistanceSupport.Resolution resolved = SpatialDistanceSupport.resolve(
                        configuration.maximumDistance(), configuration.maximumDistanceUnit(), geometry.crs());
                if (!resolved.valid()) {
                    issues.error("SPATIAL_DISTANCE_UNIT_UNSUPPORTED", resolved.error() == null ? "最大距离换算后不是有限数值" : resolved.error(),
                            "configuration.maximumDistanceUnit");
                } else {
                    maximum = resolved.sourceCrsValue();
                }
            }
        }
        double outputFactor;
        if (geodesic) {
            outputFactor = SpatialDistanceSupport.metresPerConfiguredUnit(
                    configuration.distanceOutputUnit());
            if (!Double.isFinite(outputFactor)) {
                issues.error("SPATIAL_DISTANCE_UNIT_UNSUPPORTED",
                        "测地线距离输出不能使用来源 CRS 单位",
                        "configuration.distanceOutputUnit");
            }
        } else {
            SpatialDistanceSupport.Resolution resolved =
                    SpatialDistanceSupport.sourceUnitsPerConfiguredUnit(
                            configuration.distanceOutputUnit(), geometry.crs());
            outputFactor = resolved.sourceCrsValue();
            if (!resolved.valid()) {
                issues.error("SPATIAL_DISTANCE_UNIT_UNSUPPORTED", resolved.error(),
                        "configuration.distanceOutputUnit");
            } else if (resolved.angular()) {
                issues.warning("PLANAR_DISTANCE_USES_ANGULAR_UNITS",
                        "地理 CRS 的平面距离使用度，结果随纬度变化",
                        "configuration.distanceOutputUnit");
            }
        }
        if (geodesic && configuration.maximumDistanceUnit() == SpatialDistanceUnit.SOURCE_CRS_UNIT) {
            issues.error("SPATIAL_DISTANCE_UNIT_UNSUPPORTED",
                    "测地线最大距离不能使用来源 CRS 单位",
                    "configuration.maximumDistanceUnit");
        }
        return new DistanceResolution(maximum, outputFactor);
    }

    private static void validateResultColumnNames(
            SpatialNearestConfiguration configuration,
            List<JoinOutputColumnSupport.ResolvedOutputColumn> outputs,
            CanvasNodeIssueSink issues
    ) {
        Set<String> names = new HashSet<>();
        outputs.stream().map(JoinOutputColumnSupport.ResolvedOutputColumn::outputColumnName)
                .map(name -> name.toLowerCase(Locale.ROOT)).forEach(names::add);
        addResultName(configuration.distanceColumnName(), "configuration.distanceColumnName", names, issues);
        if (configuration.rankColumnName() != null) {
            addResultName(configuration.rankColumnName(), "configuration.rankColumnName", names, issues);
        }
    }

    private static void addResultName(
            String name,
            String path,
            Set<String> names,
            CanvasNodeIssueSink issues
    ) {
        if (!CanvasNodeSupport.blank(name) && !names.add(name.toLowerCase(Locale.ROOT))) {
            issues.error("DUPLICATE_COLUMN_NAME", "输出字段名重复：" + name, path);
        }
    }

    static CanvasColumnSchema nullable(CanvasColumnSchema source) {
        return new CanvasColumnSchema(
                source.name(), source.fieldType(), source.length(), source.precision(), source.scale(),
                true, source.defaultValue(), source.autoIncrement(), source.generated(), source.comment(),
                source.geometry());
    }

    static CanvasColumnSchema decimalColumn(String name, boolean nullable) {
        return new CanvasColumnSchema(
                name, PlatformDataType.DECIMAL, null, 38, 12, nullable,
                null, false, false, null, null);
    }

    static CanvasColumnSchema integerColumn(String name, boolean nullable) {
        return new CanvasColumnSchema(
                name, PlatformDataType.INTEGER, null, null, null, nullable,
                null, false, false, null, null);
    }

    private record DistanceResolution(Double maximumSourceValue, double outputUnitFactor) {
    }
}
