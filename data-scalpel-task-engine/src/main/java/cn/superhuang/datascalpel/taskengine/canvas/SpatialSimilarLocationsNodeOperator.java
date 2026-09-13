package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasFieldPredicate;
import cn.superhuang.data.scalpel.contract.task.CanvasFilterCondition;
import cn.superhuang.data.scalpel.contract.task.CanvasFilterGroup;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SpatialSimilarLocationsAnalysisField;
import cn.superhuang.data.scalpel.contract.task.SpatialSimilarLocationsAppendField;
import cn.superhuang.data.scalpel.contract.task.SpatialSimilarLocationsConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialSimilarLocationsMatchMethod;
import cn.superhuang.data.scalpel.contract.task.SpatialSimilarLocationsNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialSimilarLocationsResultMode;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Attribute similarity search aligned with the ArcGIS GeoAnalytics Find Similar Locations core contract. */
public final class SpatialSimilarLocationsNodeOperator implements CanvasNodeOperator {

    private static final String ID = "__datascalpel_similar_id";
    private static final String GEOMETRY = "__datascalpel_similar_geometry";
    private static final String SCORE = "__datascalpel_similar_score";
    private static final String SIMILARITY_RANK = "__datascalpel_similar_rank";
    private static final String DISSIMILARITY_RANK = "__datascalpel_dissimilar_rank";
    private static final String CANDIDATE_COUNT = "__datascalpel_similar_candidate_count";
    private static final String REFERENCE_COUNT = "__datascalpel_similar_reference_count";

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_SIMILAR_LOCATIONS;
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
        if (!(definition instanceof SpatialSimilarLocationsNodeDefinition node)) {
            throw new IllegalArgumentException("SPATIAL_SIMILAR_LOCATIONS operator received "
                    + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        SpatialSimilarLocationsConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(inputSchemas);
        CanvasNodeIssueSink issues = context.issues();
        validateBase(configuration, inputs, issues);

        SparkCanvasTable reference = CanvasNodeSupport.blank(configuration.referenceTableName())
                ? null : inputs.get(configuration.referenceTableName());
        SparkCanvasTable candidate = CanvasNodeSupport.blank(configuration.candidateTableName())
                ? null : inputs.get(configuration.candidateTableName());
        if (!CanvasNodeSupport.blank(configuration.referenceTableName()) && reference == null) {
            issues.error("TABLE_NOT_FOUND", "参考表不在上游数据中：" + configuration.referenceTableName(),
                    "configuration.referenceTableName");
        }
        if (!CanvasNodeSupport.blank(configuration.candidateTableName()) && candidate == null) {
            issues.error("TABLE_NOT_FOUND", "候选表不在上游数据中：" + configuration.candidateTableName(),
                    "configuration.candidateTableName");
        }
        if (reference == null || candidate == null) return CanvasNodeOperationResult.invalid(inputSchemas);
        if (reference.schema().datasetKind() != CanvasDatasetKind.BOUNDED
                || candidate.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error("BOUNDED_INPUT_REQUIRED", "查找相似位置只支持有界输入", "configuration");
        }

        Map<String, CanvasColumnSchema> referenceColumns = CanvasNodeSupport.columns(reference.schema());
        Map<String, CanvasColumnSchema> candidateColumns = CanvasNodeSupport.columns(candidate.schema());
        CanvasColumnSchema referenceId = validateId(configuration.referenceIdColumnName(), referenceColumns,
                "参考", "configuration.referenceIdColumnName", issues);
        CanvasColumnSchema candidateId = validateId(configuration.candidateIdColumnName(), candidateColumns,
                "候选", "configuration.candidateIdColumnName", issues);
        CanvasColumnSchema referenceGeometry = validateGeometry(
                configuration.referenceGeometryColumnName(), referenceColumns,
                "参考", "configuration.referenceGeometryColumnName", issues);
        CanvasColumnSchema candidateGeometry = validateGeometry(
                configuration.candidateGeometryColumnName(), candidateColumns,
                "候选", "configuration.candidateGeometryColumnName", issues);
        validateGeometryPair(referenceGeometry, candidateGeometry, issues);

        if (configuration.referenceFilter() != null) {
            CanvasPredicateExpressionBuilder.validate(configuration.referenceFilter(), reference, issues,
                    "configuration.referenceFilter");
        }
        if (configuration.candidateFilter() != null) {
            CanvasPredicateExpressionBuilder.validate(configuration.candidateFilter(), candidate, issues,
                    "configuration.candidateFilter");
        }
        List<ResolvedAnalysis> analyses = validateAnalysisFields(
                configuration, referenceColumns, candidateColumns, issues);
        List<ResolvedAppend> appends = validateAppendFields(
                configuration, referenceColumns, candidateColumns, issues);
        validateOutputNames(configuration, issues);
        if (issues.hasErrors() || referenceId == null || candidateId == null
                || referenceGeometry == null || candidateGeometry == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> result = context.runtimeValues().preview()
                ? schemaPlan(reference.dataset(), candidate.dataset(), configuration,
                        referenceGeometry, analyses, appends)
                : buildPlan(reference.dataset(), candidate.dataset(), configuration, analyses, appends);
        CanvasTableSchema outputSchema = new CanvasTableSchema(configuration.outputTableName(), null,
                outputSchema(configuration, referenceGeometry, analyses, appends),
                CanvasDatasetKind.BOUNDED, null, null);
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, result));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateBase(
            SpatialSimilarLocationsConfiguration c,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeIssueSink issues
    ) {
        CanvasNodeSupport.required(c.referenceTableName(), "请选择参考表",
                "configuration.referenceTableName", issues);
        CanvasNodeSupport.required(c.referenceIdColumnName(), "请选择参考唯一字段",
                "configuration.referenceIdColumnName", issues);
        CanvasNodeSupport.required(c.referenceGeometryColumnName(), "请选择参考 Geometry",
                "configuration.referenceGeometryColumnName", issues);
        CanvasNodeSupport.required(c.candidateTableName(), "请选择候选表",
                "configuration.candidateTableName", issues);
        CanvasNodeSupport.required(c.candidateIdColumnName(), "请选择候选唯一字段",
                "configuration.candidateIdColumnName", issues);
        CanvasNodeSupport.required(c.candidateGeometryColumnName(), "请选择候选 Geometry",
                "configuration.candidateGeometryColumnName", issues);
        CanvasNodeSupport.required(c.outputTableName(), "请输入输出表名",
                "configuration.outputTableName", issues);
        if (c.matchMethod() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择匹配方法", "configuration.matchMethod");
        }
        if (c.resultMode() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择返回范围", "configuration.resultMode");
        }
        if (c.numberOfResults() < 1
                || c.numberOfResults() > SpatialSimilarLocationsConfiguration.MAX_NUMBER_OF_RESULTS) {
            issues.error("INVALID_SIMILAR_LOCATIONS_RESULT_COUNT", "每端结果数量必须在 1 到 10000 之间",
                    "configuration.numberOfResults");
        }
        if (c.analysisFields() == null) {
            issues.error("INVALID_SIMILAR_LOCATIONS_ANALYSIS_FIELDS", "分析字段必须是数组",
                    "configuration.analysisFields");
        } else if (c.analysisFields().isEmpty()) {
            issues.error("SIMILAR_LOCATIONS_ANALYSIS_FIELDS_REQUIRED", "至少选择一个分析字段",
                    "configuration.analysisFields");
        } else if (c.analysisFields().size() > SpatialSimilarLocationsConfiguration.MAX_ANALYSIS_FIELDS) {
            issues.error("SIMILAR_LOCATIONS_ANALYSIS_FIELD_COUNT_EXCEEDED", "分析字段不能超过 32 项",
                    "configuration.analysisFields");
        }
        if (c.matchMethod() == SpatialSimilarLocationsMatchMethod.ATTRIBUTE_PROFILES
                && c.analysisFields() != null && c.analysisFields().size() < 2) {
            issues.error("SIMILAR_LOCATIONS_PROFILE_FIELDS_REQUIRED", "属性轮廓至少需要两个分析字段",
                    "configuration.analysisFields");
        }
        if (c.appendFields() == null) {
            issues.error("INVALID_SIMILAR_LOCATIONS_APPEND_FIELDS", "附加字段必须是数组",
                    "configuration.appendFields");
        } else if (c.appendFields().size() > SpatialSimilarLocationsConfiguration.MAX_APPEND_FIELDS) {
            issues.error("SIMILAR_LOCATIONS_APPEND_FIELD_COUNT_EXCEEDED", "附加字段不能超过 64 项",
                    "configuration.appendFields");
        }
        if (!CanvasNodeSupport.blank(c.outputTableName()) && inputs.containsKey(c.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在：" + c.outputTableName(),
                    "configuration.outputTableName");
        }
    }

    private static CanvasColumnSchema validateId(
            String name,
            Map<String, CanvasColumnSchema> columns,
            String label,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (CanvasNodeSupport.blank(name)) return null;
        CanvasColumnSchema column = columns.get(name);
        if (column == null) {
            issues.error("COLUMN_NOT_FOUND", label + "唯一字段不存在：" + name, path);
            return null;
        }
        if (column.fieldType() == PlatformDataType.GEOMETRY
                || column.fieldType() == PlatformDataType.BINARY) {
            issues.error("SIMILAR_LOCATIONS_ID_TYPE_INVALID", label + "唯一字段不能是 Geometry 或 BINARY", path);
            return null;
        }
        return column;
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
            return null;
        }
        if (column.fieldType() != PlatformDataType.GEOMETRY || column.geometry() == null) {
            issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED", label + "字段不是带完整元数据的 Geometry", path);
            return null;
        }
        CanvasNodeSupport.validateSupportedGeometry(List.of(column), path, issues);
        if (column.geometry().dimension() != CoordinateDimension.XY) {
            issues.error("UNSUPPORTED_GEOMETRY_DIMENSION", "查找相似位置只支持 XY Geometry", path);
        }
        return column;
    }

    private static void validateGeometryPair(
            CanvasColumnSchema reference,
            CanvasColumnSchema candidate,
            CanvasNodeIssueSink issues
    ) {
        if (reference == null || candidate == null
                || reference.geometry() == null || candidate.geometry() == null) return;
        GeometryTypeDefinition left = reference.geometry();
        GeometryTypeDefinition right = candidate.geometry();
        if (left.kind() != right.kind()) {
            issues.error("SPATIAL_GEOMETRY_KIND_MISMATCH", "参考与候选 Geometry 类型必须一致",
                    "configuration.candidateGeometryColumnName");
        }
        if (!left.crs().equals(right.crs())) {
            issues.error("SPATIAL_CRS_MISMATCH", "参考与候选 Geometry 的 CRS 必须一致",
                    "configuration.candidateGeometryColumnName");
        }
        if (left.dimension() != right.dimension()) {
            issues.error("SPATIAL_DIMENSION_MISMATCH", "参考与候选 Geometry 的坐标维度必须一致",
                    "configuration.candidateGeometryColumnName");
        }
    }

    private static List<ResolvedAnalysis> validateAnalysisFields(
            SpatialSimilarLocationsConfiguration c,
            Map<String, CanvasColumnSchema> referenceColumns,
            Map<String, CanvasColumnSchema> candidateColumns,
            CanvasNodeIssueSink issues
    ) {
        if (c.analysisFields() == null) return List.of();
        List<ResolvedAnalysis> result = new ArrayList<>();
        Set<String> sources = new HashSet<>();
        for (int index = 0; index < c.analysisFields().size(); index++) {
            SpatialSimilarLocationsAnalysisField field = c.analysisFields().get(index);
            String path = "configuration.analysisFields[" + index + "]";
            if (field == null) {
                issues.error("INVALID_SIMILAR_LOCATIONS_ANALYSIS_FIELD", "分析字段不能为空", path);
                continue;
            }
            CanvasNodeSupport.required(field.columnName(), "请选择分析字段", path + ".columnName", issues);
            CanvasNodeSupport.required(field.outputColumnName(), "请输入分析结果字段名",
                    path + ".outputColumnName", issues);
            String sourceKey = normalized(field.columnName());
            if (!CanvasNodeSupport.blank(field.columnName()) && !sources.add(sourceKey)) {
                issues.error("DUPLICATE_SIMILAR_LOCATIONS_FIELD", "分析字段不能重复：" + field.columnName(),
                        path + ".columnName");
            }
            CanvasColumnSchema reference = referenceColumns.get(field.columnName());
            CanvasColumnSchema candidate = candidateColumns.get(field.columnName());
            if (!CanvasNodeSupport.blank(field.columnName()) && reference == null) {
                issues.error("COLUMN_NOT_FOUND", "参考表缺少分析字段：" + field.columnName(), path + ".columnName");
            }
            if (!CanvasNodeSupport.blank(field.columnName()) && candidate == null) {
                issues.error("COLUMN_NOT_FOUND", "候选表缺少同名分析字段：" + field.columnName(), path + ".columnName");
            }
            if (reference == null || candidate == null) continue;
            if (!numeric(reference.fieldType()) || !numeric(candidate.fieldType())) {
                issues.error("NUMERIC_COLUMN_REQUIRED", "分析字段必须是数值类型：" + field.columnName(),
                        path + ".columnName");
                continue;
            }
            if (!sameType(reference, candidate)) {
                issues.error("SIMILAR_LOCATIONS_FIELD_TYPE_MISMATCH",
                        "参考表与候选表的同名分析字段类型必须一致：" + field.columnName(),
                        path + ".columnName");
                continue;
            }
            result.add(new ResolvedAnalysis(field, reference, candidate, result.size()));
        }
        return result;
    }

    private static List<ResolvedAppend> validateAppendFields(
            SpatialSimilarLocationsConfiguration c,
            Map<String, CanvasColumnSchema> referenceColumns,
            Map<String, CanvasColumnSchema> candidateColumns,
            CanvasNodeIssueSink issues
    ) {
        if (c.appendFields() == null) return List.of();
        Set<String> sources = new HashSet<>();
        if (c.analysisFields() != null) c.analysisFields().stream().filter(java.util.Objects::nonNull)
                .map(SpatialSimilarLocationsAnalysisField::columnName)
                .filter(name -> !CanvasNodeSupport.blank(name)).map(SpatialSimilarLocationsNodeOperator::normalized)
                .forEach(sources::add);
        List<ResolvedAppend> result = new ArrayList<>();
        for (int index = 0; index < c.appendFields().size(); index++) {
            SpatialSimilarLocationsAppendField field = c.appendFields().get(index);
            String path = "configuration.appendFields[" + index + "]";
            if (field == null) {
                issues.error("INVALID_SIMILAR_LOCATIONS_APPEND_FIELD", "附加字段不能为空", path);
                continue;
            }
            CanvasNodeSupport.required(field.sourceColumnName(), "请选择候选附加字段",
                    path + ".sourceColumnName", issues);
            CanvasNodeSupport.required(field.outputColumnName(), "请输入附加结果字段名",
                    path + ".outputColumnName", issues);
            String sourceKey = normalized(field.sourceColumnName());
            if (!CanvasNodeSupport.blank(field.sourceColumnName()) && !sources.add(sourceKey)) {
                issues.error("DUPLICATE_SIMILAR_LOCATIONS_FIELD", "分析和附加字段不能重复："
                        + field.sourceColumnName(), path + ".sourceColumnName");
            }
            CanvasColumnSchema candidate = candidateColumns.get(field.sourceColumnName());
            if (!CanvasNodeSupport.blank(field.sourceColumnName()) && candidate == null) {
                issues.error("COLUMN_NOT_FOUND", "候选附加字段不存在：" + field.sourceColumnName(),
                        path + ".sourceColumnName");
                continue;
            }
            if (candidate == null) continue;
            if (candidate.fieldType() == PlatformDataType.GEOMETRY) {
                issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED", "附加字段不能是 Geometry",
                        path + ".sourceColumnName");
                continue;
            }
            CanvasColumnSchema reference = referenceColumns.get(field.sourceColumnName());
            if (reference != null && !sameType(reference, candidate)) reference = null;
            result.add(new ResolvedAppend(field, reference, candidate, result.size()));
        }
        return result;
    }

    private static void validateOutputNames(
            SpatialSimilarLocationsConfiguration c,
            CanvasNodeIssueSink issues
    ) {
        List<NamedPath> names = new ArrayList<>(List.of(
                new NamedPath(c.outputGeometryColumnName(), "configuration.outputGeometryColumnName"),
                new NamedPath(c.locationTypeColumnName(), "configuration.locationTypeColumnName"),
                new NamedPath(c.similarityRankColumnName(), "configuration.similarityRankColumnName"),
                new NamedPath(c.dissimilarityRankColumnName(), "configuration.dissimilarityRankColumnName"),
                new NamedPath(c.similarityIndexColumnName(), "configuration.similarityIndexColumnName"),
                new NamedPath(c.cosineIndexColumnName(), "configuration.cosineIndexColumnName"),
                new NamedPath(c.labelRankColumnName(), "configuration.labelRankColumnName"),
                new NamedPath(c.referenceIdOutputColumnName(), "configuration.referenceIdOutputColumnName"),
                new NamedPath(c.searchIdOutputColumnName(), "configuration.searchIdOutputColumnName")
        ));
        if (c.analysisFields() != null) for (int index = 0; index < c.analysisFields().size(); index++) {
            SpatialSimilarLocationsAnalysisField field = c.analysisFields().get(index);
            if (field != null) names.add(new NamedPath(field.outputColumnName(),
                    "configuration.analysisFields[" + index + "].outputColumnName"));
        }
        if (c.appendFields() != null) for (int index = 0; index < c.appendFields().size(); index++) {
            SpatialSimilarLocationsAppendField field = c.appendFields().get(index);
            if (field != null) names.add(new NamedPath(field.outputColumnName(),
                    "configuration.appendFields[" + index + "].outputColumnName"));
        }
        Set<String> unique = new HashSet<>();
        for (NamedPath named : names) {
            if (CanvasNodeSupport.blank(named.name())) {
                issues.error("INVALID_COLUMN_NAME", "结果字段名不能为空", named.path());
            } else if (!unique.add(normalized(named.name()))) {
                issues.error("DUPLICATE_COLUMN_NAME", "结果字段名重复：" + named.name(), named.path());
            }
        }
    }

    private static Dataset<Row> buildPlan(
            Dataset<Row> referenceSource,
            Dataset<Row> candidateSource,
            SpatialSimilarLocationsConfiguration c,
            List<ResolvedAnalysis> analyses,
            List<ResolvedAppend> appends
    ) {
        Dataset<Row> references = prepare(referenceSource, c.referenceFilter(), c.referenceIdColumnName(),
                c.referenceGeometryColumnName(), analyses, appends, true);
        Dataset<Row> candidates = prepare(candidateSource, c.candidateFilter(), c.candidateIdColumnName(),
                c.candidateGeometryColumnName(), analyses, appends, false);

        List<Column> analysisColumns = analyses.stream()
                .map(value -> functions.col(analysisName(value.index()))).toList();
        Dataset<Row> values = references.select(analysisColumns.toArray(Column[]::new))
                .unionByName(candidates.select(analysisColumns.toArray(Column[]::new)));
        List<Column> statistics = new ArrayList<>();
        for (ResolvedAnalysis analysis : analyses) {
            Column value = values.col(analysisName(analysis.index()));
            statistics.add(functions.avg(value).alias(meanName(analysis.index())));
            statistics.add(functions.stddev_pop(value).alias(stddevName(analysis.index())));
        }
        Dataset<Row> stats = values.agg(statistics.getFirst(), statistics.subList(1, statistics.size()).toArray(Column[]::new));
        Dataset<Row> standardizedReferences = standardize(references, stats, analyses);
        Dataset<Row> standardizedCandidates = standardize(candidates, stats, analyses);

        List<Column> targetAggregates = new ArrayList<>();
        targetAggregates.add(functions.count(functions.lit(1)).alias(REFERENCE_COUNT));
        for (ResolvedAnalysis analysis : analyses) {
            targetAggregates.add(functions.avg(functions.col(zName(analysis.index())))
                    .alias(targetName(analysis.index())));
        }
        Dataset<Row> target = standardizedReferences.agg(
                targetAggregates.getFirst(), targetAggregates.subList(1, targetAggregates.size()).toArray(Column[]::new));

        Dataset<Row> scored = standardizedCandidates.crossJoin(target);
        Column squaredDifference = functions.lit(0d);
        Column dot = functions.lit(0d);
        Column candidateNorm = functions.lit(0d);
        Column targetNorm = functions.lit(0d);
        for (ResolvedAnalysis analysis : analyses) {
            Column candidateValue = scored.col(zName(analysis.index()));
            Column targetValue = scored.col(targetName(analysis.index()));
            squaredDifference = squaredDifference.plus(functions.pow(candidateValue.minus(targetValue), 2d));
            dot = dot.plus(candidateValue.multiply(targetValue));
            candidateNorm = candidateNorm.plus(functions.pow(candidateValue, 2d));
            targetNorm = targetNorm.plus(functions.pow(targetValue, 2d));
        }
        Column calculatedScore = c.matchMethod() == SpatialSimilarLocationsMatchMethod.ATTRIBUTE_PROFILES
                ? functions.when(candidateNorm.leq(0d).or(targetNorm.leq(0d)),
                        functions.raise_error(functions.lit("SPATIAL_SIMILAR_LOCATIONS_ZERO_PROFILE")).cast("double"))
                .otherwise(functions.lit(1d).minus(dot.divide(functions.sqrt(candidateNorm.multiply(targetNorm)))))
                : squaredDifference;
        Column score = functions.when(scored.col(REFERENCE_COUNT).equalTo(0),
                        functions.raise_error(functions.lit(
                                "SPATIAL_SIMILAR_LOCATIONS_REFERENCE_REQUIRED")).cast("double"))
                .otherwise(calculatedScore);
        scored = scored.withColumn(SCORE, checkedFinite(score));
        WindowSpec similar = Window.orderBy(scored.col(SCORE).asc(), scored.col(ID).asc());
        WindowSpec dissimilar = Window.orderBy(scored.col(SCORE).desc(), scored.col(ID).asc());
        WindowSpec allCandidates = Window.partitionBy();
        scored = scored
                .withColumn(SIMILARITY_RANK, functions.row_number().over(similar))
                .withColumn(DISSIMILARITY_RANK, functions.row_number().over(dissimilar).multiply(-1))
                .withColumn(CANDIDATE_COUNT, functions.count(functions.lit(1)).over(allCandidates));
        Column perSide = c.resultMode() == SpatialSimilarLocationsResultMode.BOTH
                ? functions.least(functions.lit(c.numberOfResults()),
                functions.floor(scored.col(CANDIDATE_COUNT).divide(2d)).cast("int"))
                : functions.least(functions.lit(c.numberOfResults()), scored.col(CANDIDATE_COUNT));
        Column most = scored.col(SIMILARITY_RANK).leq(perSide);
        Column least = scored.col(DISSIMILARITY_RANK).multiply(-1).leq(perSide);
        Column selected = switch (c.resultMode()) {
            case MOST_SIMILAR -> most;
            case LEAST_SIMILAR -> least;
            case BOTH -> most.or(least);
        };
        Dataset<Row> selectedCandidates = scored.filter(selected);

        List<Column> candidateProjection = resultProjection(selectedCandidates, c, analyses, appends, false, perSide);
        Dataset<Row> candidateResult = selectedCandidates.select(candidateProjection.toArray(Column[]::new));
        List<Column> referenceProjection = resultProjection(
                standardizedReferences, c, analyses, appends, true, functions.lit(c.numberOfResults()));
        Dataset<Row> referenceResult = standardizedReferences.select(referenceProjection.toArray(Column[]::new));

        Dataset<Row> guard = missingReferenceGuard(target, candidateResult.schema(), c.locationTypeColumnName());
        return referenceResult.unionByName(candidateResult).unionByName(guard);
    }

    private static Dataset<Row> schemaPlan(
            Dataset<Row> reference,
            Dataset<Row> candidate,
            SpatialSimilarLocationsConfiguration c,
            CanvasColumnSchema referenceGeometry,
            List<ResolvedAnalysis> analyses,
            List<ResolvedAppend> appends
    ) {
        Set<String> occupied = new HashSet<>(List.of(reference.columns()));
        occupied.addAll(List.of(candidate.columns()));
        String referenceMembersName = previewName(occupied, "__datascalpel_similar_reference_members");
        String candidateMembersName = previewName(occupied, "__datascalpel_similar_candidate_members");

        List<Column> referenceDependencies = new ArrayList<>();
        referenceDependencies.add(reference.col(CanvasNodeSupport.quoteIdentifier(c.referenceIdColumnName())));
        referenceDependencies.add(reference.col(CanvasNodeSupport.quoteIdentifier(c.referenceGeometryColumnName())));
        for (ResolvedAnalysis analysis : analyses) {
            referenceDependencies.add(reference.col(CanvasNodeSupport.quoteIdentifier(
                    analysis.field().columnName())));
        }
        for (ResolvedAppend append : appends) {
            if (append.reference() != null) {
                referenceDependencies.add(reference.col(CanvasNodeSupport.quoteIdentifier(
                        append.field().sourceColumnName())));
            }
        }
        addFilterDependencies(c.referenceFilter(), reference, referenceDependencies);
        Column referenceMembers = functions.collect_list(functions.struct(
                referenceDependencies.toArray(Column[]::new))).over(Window.partitionBy());
        Dataset<Row> base = reference.filter(functions.lit(false))
                .withColumn(referenceMembersName, referenceMembers);

        List<Column> candidateDependencies = new ArrayList<>();
        candidateDependencies.add(candidate.col(CanvasNodeSupport.quoteIdentifier(c.candidateIdColumnName())));
        candidateDependencies.add(candidate.col(CanvasNodeSupport.quoteIdentifier(c.candidateGeometryColumnName())));
        for (ResolvedAnalysis analysis : analyses) {
            candidateDependencies.add(candidate.col(CanvasNodeSupport.quoteIdentifier(
                    analysis.field().columnName())));
        }
        for (ResolvedAppend append : appends) {
            candidateDependencies.add(candidate.col(CanvasNodeSupport.quoteIdentifier(
                    append.field().sourceColumnName())));
        }
        addFilterDependencies(c.candidateFilter(), candidate, candidateDependencies);
        Dataset<Row> candidateSummary = candidate.filter(functions.lit(false)).agg(
                functions.collect_list(functions.struct(candidateDependencies.toArray(Column[]::new)))
                        .alias(candidateMembersName));
        base = base.crossJoin(candidateSummary);

        Column referenceDependency = base.col(CanvasNodeSupport.quoteIdentifier(referenceMembersName));
        Column candidateDependency = base.col(CanvasNodeSupport.quoteIdentifier(candidateMembersName));
        Column jointDependency = functions.struct(referenceDependency, candidateDependency);
        Set<String> candidateOnly = new HashSet<>();
        for (ResolvedAppend append : appends) {
            if (append.reference() == null) candidateOnly.add(append.field().outputColumnName());
        }
        List<Column> projection = new ArrayList<>();
        for (CanvasColumnSchema output : outputSchema(c, referenceGeometry, analyses, appends)) {
            Column dependency;
            if (output.name().equals(c.referenceIdOutputColumnName())) {
                dependency = referenceDependency;
            } else if (output.name().equals(c.searchIdOutputColumnName()) || candidateOnly.contains(output.name())) {
                dependency = candidateDependency;
            } else {
                dependency = jointDependency;
            }
            projection.add(previewValue(dependency, SparkTypeMapper.toDataType(output)).alias(output.name()));
        }
        return base.select(projection.toArray(Column[]::new));
    }

    private static void addFilterDependencies(
            CanvasFilterCondition condition,
            Dataset<Row> source,
            List<Column> dependencies
    ) {
        if (condition == null) return;
        switch (condition) {
            case CanvasFilterGroup group -> group.children().forEach(
                    child -> addFilterDependencies(child, source, dependencies));
            case CanvasFieldPredicate predicate -> dependencies.add(source.col(
                    CanvasNodeSupport.quoteIdentifier(predicate.columnName())));
        }
    }

    private static Column previewValue(Column dependency, DataType type) {
        return functions.udf((UDF1<Object, Object>) ignored -> {
            throw new IllegalArgumentException("SPATIAL_SIMILAR_LOCATIONS_PREVIEW_NOT_EXECUTABLE");
        }, type).apply(dependency);
    }

    private static String previewName(Set<String> occupied, String base) {
        String value = base;
        while (!occupied.add(value)) value += "_";
        return value;
    }

    private static Dataset<Row> prepare(
            Dataset<Row> source,
            CanvasFilterCondition filter,
            String idColumnName,
            String geometryColumnName,
            List<ResolvedAnalysis> analyses,
            List<ResolvedAppend> appends,
            boolean reference
    ) {
        Dataset<Row> filtered = filter == null ? source
                : source.filter(CanvasPredicateExpressionBuilder.expression(filter, source));
        Column geometry = filtered.col(CanvasNodeSupport.quoteIdentifier(geometryColumnName));
        filtered = filtered.filter(geometry.isNotNull().and(st_functions.ST_IsEmpty(geometry).equalTo(false)));
        Column rawId = filtered.col(CanvasNodeSupport.quoteIdentifier(idColumnName)).cast("string");
        WindowSpec duplicates = Window.partitionBy(rawId);
        Column id = functions.when(rawId.isNull(), functions.raise_error(functions.lit(
                        reference ? "SPATIAL_SIMILAR_LOCATIONS_REFERENCE_ID_INVALID"
                                : "SPATIAL_SIMILAR_LOCATIONS_CANDIDATE_ID_INVALID")).cast("string"))
                .when(functions.count(functions.lit(1)).over(duplicates).gt(1),
                        functions.raise_error(functions.lit(reference
                                ? "SPATIAL_SIMILAR_LOCATIONS_REFERENCE_ID_DUPLICATE"
                                : "SPATIAL_SIMILAR_LOCATIONS_CANDIDATE_ID_DUPLICATE")).cast("string"))
                .otherwise(rawId);
        List<Column> projection = new ArrayList<>();
        projection.add(id.alias(ID));
        projection.add(geometry.alias(GEOMETRY));
        for (ResolvedAnalysis analysis : analyses) {
            Column value = filtered.col(CanvasNodeSupport.quoteIdentifier(analysis.field().columnName())).cast("double");
            projection.add(checkedValue(value).alias(analysisName(analysis.index())));
            projection.add(filtered.col(CanvasNodeSupport.quoteIdentifier(analysis.field().columnName()))
                    .alias(rawAnalysisName(analysis.index())));
        }
        for (ResolvedAppend append : appends) {
            Column value;
            if (!reference) {
                value = filtered.col(CanvasNodeSupport.quoteIdentifier(append.field().sourceColumnName()));
            } else if (append.reference() != null) {
                value = filtered.col(CanvasNodeSupport.quoteIdentifier(append.field().sourceColumnName()));
            } else {
                value = functions.lit(null).cast(SparkTypeMapper.toDataType(append.candidate()));
            }
            projection.add(value.alias(appendName(append.index())));
        }
        return filtered.select(projection.toArray(Column[]::new));
    }

    private static Dataset<Row> standardize(
            Dataset<Row> source,
            Dataset<Row> statistics,
            List<ResolvedAnalysis> analyses
    ) {
        Dataset<Row> result = source.crossJoin(statistics);
        for (ResolvedAnalysis analysis : analyses) {
            Column standardDeviation = result.col(stddevName(analysis.index()));
            Column z = functions.when(standardDeviation.isNull().or(standardDeviation.leq(0d)), functions.lit(0d))
                    .otherwise(result.col(analysisName(analysis.index()))
                            .minus(result.col(meanName(analysis.index()))).divide(standardDeviation));
            result = result.withColumn(zName(analysis.index()), checkedFinite(z));
        }
        return result;
    }

    private static List<Column> resultProjection(
            Dataset<Row> source,
            SpatialSimilarLocationsConfiguration c,
            List<ResolvedAnalysis> analyses,
            List<ResolvedAppend> appends,
            boolean reference,
            Column perSide
    ) {
        List<Column> result = new ArrayList<>();
        result.add(source.col(GEOMETRY).alias(c.outputGeometryColumnName()));
        result.add(functions.lit(reference ? "REFERENCE" : "CANDIDATE").alias(c.locationTypeColumnName()));
        result.add(reference ? source.col(ID).alias(c.referenceIdOutputColumnName())
                : functions.lit(null).cast("string").alias(c.referenceIdOutputColumnName()));
        result.add(reference ? functions.lit(null).cast("string").alias(c.searchIdOutputColumnName())
                : source.col(ID).alias(c.searchIdOutputColumnName()));
        for (ResolvedAnalysis analysis : analyses) {
            result.add(source.col(rawAnalysisName(analysis.index())).alias(analysis.field().outputColumnName()));
        }
        for (ResolvedAppend append : appends) {
            result.add(source.col(appendName(append.index())).alias(append.field().outputColumnName()));
        }
        result.add(reference ? functions.lit(0).alias(c.similarityRankColumnName())
                : source.col(SIMILARITY_RANK).alias(c.similarityRankColumnName()));
        result.add(reference ? functions.lit(0).alias(c.dissimilarityRankColumnName())
                : source.col(DISSIMILARITY_RANK).alias(c.dissimilarityRankColumnName()));
        boolean values = c.matchMethod() == SpatialSimilarLocationsMatchMethod.ATTRIBUTE_VALUES;
        result.add(values
                ? (reference ? functions.lit(0d) : source.col(SCORE)).alias(c.similarityIndexColumnName())
                : functions.lit(null).cast("double").alias(c.similarityIndexColumnName()));
        result.add(!values
                ? (reference ? functions.lit(0d) : source.col(SCORE)).alias(c.cosineIndexColumnName())
                : functions.lit(null).cast("double").alias(c.cosineIndexColumnName()));
        Column label;
        if (reference) {
            label = functions.lit(0);
        } else {
            label = switch (c.resultMode()) {
                case MOST_SIMILAR -> perSide.minus(source.col(SIMILARITY_RANK)).plus(1).cast("int");
                case LEAST_SIMILAR -> source.col(DISSIMILARITY_RANK);
                case BOTH -> functions.when(source.col(SIMILARITY_RANK).leq(perSide),
                                perSide.minus(source.col(SIMILARITY_RANK)).plus(1).cast("int"))
                        .otherwise(source.col(DISSIMILARITY_RANK));
            };
        }
        result.add(label.alias(c.labelRankColumnName()));
        return result;
    }

    private static Dataset<Row> missingReferenceGuard(
            Dataset<Row> target,
            StructType schema,
            String locationTypeColumnName
    ) {
        List<Column> projection = new ArrayList<>();
        for (org.apache.spark.sql.types.StructField field : schema.fields()) {
            DataType type = field.dataType();
            if (field.name().equals(locationTypeColumnName)) {
                projection.add(functions.raise_error(functions.lit(
                        "SPATIAL_SIMILAR_LOCATIONS_REFERENCE_REQUIRED")).cast(type).alias(field.name()));
            } else {
                projection.add(functions.lit(null).cast(type).alias(field.name()));
            }
        }
        return target.filter(target.col(REFERENCE_COUNT).equalTo(0))
                .select(projection.toArray(Column[]::new));
    }

    private static List<CanvasColumnSchema> outputSchema(
            SpatialSimilarLocationsConfiguration c,
            CanvasColumnSchema referenceGeometry,
            List<ResolvedAnalysis> analyses,
            List<ResolvedAppend> appends
    ) {
        List<CanvasColumnSchema> columns = new ArrayList<>();
        columns.add(copy(referenceGeometry, c.outputGeometryColumnName(), false));
        columns.add(scalar(c.locationTypeColumnName(), PlatformDataType.STRING, false));
        columns.add(scalar(c.referenceIdOutputColumnName(), PlatformDataType.STRING, true));
        columns.add(scalar(c.searchIdOutputColumnName(), PlatformDataType.STRING, true));
        for (ResolvedAnalysis analysis : analyses) {
            columns.add(copy(analysis.reference(), analysis.field().outputColumnName(), false));
        }
        for (ResolvedAppend append : appends) {
            columns.add(copy(append.candidate(), append.field().outputColumnName(), true));
        }
        columns.add(scalar(c.similarityRankColumnName(), PlatformDataType.INTEGER, false));
        columns.add(scalar(c.dissimilarityRankColumnName(), PlatformDataType.INTEGER, false));
        columns.add(scalar(c.similarityIndexColumnName(), PlatformDataType.DOUBLE, true));
        columns.add(scalar(c.cosineIndexColumnName(), PlatformDataType.DOUBLE, true));
        columns.add(scalar(c.labelRankColumnName(), PlatformDataType.INTEGER, false));
        return List.copyOf(columns);
    }

    private static CanvasColumnSchema copy(CanvasColumnSchema source, String name, boolean nullable) {
        return new CanvasColumnSchema(name, source.fieldType(), source.length(), source.precision(), source.scale(),
                nullable || source.nullable(), null, false, false, source.comment(), source.geometry());
    }

    private static CanvasColumnSchema scalar(String name, PlatformDataType type, boolean nullable) {
        return new CanvasColumnSchema(name, type, null, null, null, nullable,
                null, false, false, null, null);
    }

    private static Column checkedValue(Column value) {
        return functions.when(value.isNull().or(functions.isnan(value))
                        .or(functions.abs(value).gt(Double.MAX_VALUE)),
                functions.raise_error(functions.lit(
                        "SPATIAL_SIMILAR_LOCATIONS_VALUE_INVALID")).cast("double"))
                .otherwise(value);
    }

    private static Column checkedFinite(Column value) {
        return functions.when(value.isNull().or(functions.isnan(value))
                        .or(functions.abs(value).gt(Double.MAX_VALUE)),
                functions.raise_error(functions.lit(
                        "SPATIAL_SIMILAR_LOCATIONS_SCORE_INVALID")).cast("double"))
                .otherwise(value);
    }

    private static boolean numeric(PlatformDataType type) {
        return switch (type) {
            case BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, DECIMAL -> true;
            default -> false;
        };
    }

    private static boolean sameType(CanvasColumnSchema left, CanvasColumnSchema right) {
        return left.fieldType() == right.fieldType()
                && (left.fieldType() != PlatformDataType.DECIMAL
                || java.util.Objects.equals(left.precision(), right.precision())
                && java.util.Objects.equals(left.scale(), right.scale()));
    }

    private static String normalized(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String analysisName(int index) { return "__datascalpel_similar_a_" + index; }
    private static String rawAnalysisName(int index) { return "__datascalpel_similar_raw_a_" + index; }
    private static String appendName(int index) { return "__datascalpel_similar_append_" + index; }
    private static String meanName(int index) { return "__datascalpel_similar_mean_" + index; }
    private static String stddevName(int index) { return "__datascalpel_similar_stddev_" + index; }
    private static String zName(int index) { return "__datascalpel_similar_z_" + index; }
    private static String targetName(int index) { return "__datascalpel_similar_target_" + index; }

    private record ResolvedAnalysis(
            SpatialSimilarLocationsAnalysisField field,
            CanvasColumnSchema reference,
            CanvasColumnSchema candidate,
            int index
    ) {
    }

    private record ResolvedAppend(
            SpatialSimilarLocationsAppendField field,
            CanvasColumnSchema reference,
            CanvasColumnSchema candidate,
            int index
    ) {
    }

    private record NamedPath(String name, String path) {
    }
}
