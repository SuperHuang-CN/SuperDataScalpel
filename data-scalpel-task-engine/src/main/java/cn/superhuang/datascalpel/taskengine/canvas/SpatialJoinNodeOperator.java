package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.JoinType;
import cn.superhuang.data.scalpel.contract.task.JoinCondition;
import cn.superhuang.data.scalpel.contract.task.JoinOutputColumnSource;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinCondition;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinDistanceOutput;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinKeepRule;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinKeepStrategy;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinOneToOneMode;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinOneToOneOptions;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinOperation;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinSummaryStatistic;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialPredicate;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_predicates;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SpatialJoinNodeOperator implements CanvasNodeOperator {
    private static final String LEFT_ALIAS = "left_spatial_input";
    private static final String RIGHT_ALIAS = "right_spatial_input";

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_JOIN;
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
        if (!(definition instanceof SpatialJoinNodeDefinition node)) {
            throw new IllegalArgumentException("SPATIAL_JOIN operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        SpatialJoinConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        CanvasNodeIssueSink issues = context.issues();
        CanvasNodeSupport.required(
                configuration.leftTableName(), "请选择左表",
                "configuration.leftTableName", issues);
        CanvasNodeSupport.required(
                configuration.rightTableName(), "请选择右表",
                "configuration.rightTableName", issues);
        CanvasNodeSupport.required(
                configuration.outputTableName(), "请输入输出表名",
                "configuration.outputTableName", issues);
        if (configuration.joinType() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择空间 Join 类型", "configuration.joinType");
        } else if (configuration.joinType() != JoinType.INNER
                && configuration.joinType() != JoinType.LEFT) {
            issues.error(
                    "SPATIAL_JOIN_TYPE_UNSUPPORTED",
                    "空间 Join 只支持 INNER 或 LEFT",
                    "configuration.joinType"
            );
        }
        if (configuration.conditions() == null) {
            issues.error("REQUIRED_CONFIGURATION", "空间拓扑条件必须是数组", "configuration.conditions");
        } else if (configuration.conditions().size() > SpatialJoinConfiguration.MAX_CONDITIONS) {
            issues.error(
                    "SPATIAL_JOIN_CONDITION_LIMIT_EXCEEDED",
                    "空间条件不能超过 " + SpatialJoinConfiguration.MAX_CONDITIONS + " 个",
                    "configuration.conditions"
            );
        }
        if ((configuration.conditions() == null || configuration.conditions().isEmpty())
                && configuration.spatialNear() == null) {
            issues.error("SPATIAL_JOIN_SPATIAL_CONDITION_REQUIRED",
                    "至少配置一个空间拓扑条件或空间 Near",
                    "configuration.conditions");
        }
        if (configuration.attributeConditions() != null
                && configuration.attributeConditions().size()
                > SpatialJoinConfiguration.MAX_ATTRIBUTE_CONDITIONS) {
            issues.error(
                    "SPATIAL_JOIN_ATTRIBUTE_CONDITION_LIMIT_EXCEEDED",
                    "属性匹配条件不能超过 "
                            + SpatialJoinConfiguration.MAX_ATTRIBUTE_CONDITIONS + " 个",
                    "configuration.attributeConditions"
            );
        }
        if (!CanvasNodeSupport.blank(configuration.leftTableName())
                && configuration.leftTableName().equals(configuration.rightTableName())) {
            issues.error("INVALID_JOIN_TABLE", "空间 Join 左右表不能相同", "configuration.rightTableName");
        }
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error(
                    "DUPLICATE_TABLE_NAME",
                    "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName"
            );
        }

        SparkCanvasTable left = inputs.get(configuration.leftTableName());
        SparkCanvasTable right = inputs.get(configuration.rightTableName());
        if (!CanvasNodeSupport.blank(configuration.leftTableName()) && left == null) {
            issues.error("TABLE_NOT_FOUND", "左表不在上游数据中：" + configuration.leftTableName(),
                    "configuration.leftTableName");
        }
        if (!CanvasNodeSupport.blank(configuration.rightTableName()) && right == null) {
            issues.error("TABLE_NOT_FOUND", "右表不在上游数据中：" + configuration.rightTableName(),
                    "configuration.rightTableName");
        }
        if (left == null || right == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        Map<String, CanvasColumnSchema> leftColumns = CanvasNodeSupport.columns(left.schema());
        Map<String, CanvasColumnSchema> rightColumns = CanvasNodeSupport.columns(right.schema());
        validateConditions(configuration, leftColumns, rightColumns, issues);
        JoinConditionSupport.validate(
                configuration.attributeConditions(),
                leftColumns,
                rightColumns,
                "configuration.attributeConditions",
                "空间 Join 属性条件",
                "空间 Join 属性条件不能使用 Geometry 字段",
                true,
                issues
        );
        SpatialJoinTemporalSupport.validate(
                configuration.temporalCondition(), left.schema(), right.schema(), issues);
        SpatialJoinNearSupport.validate(
                configuration.spatialNear(), left.schema(), right.schema(), issues);
        List<JoinOutputColumnSupport.ResolvedOutputColumn> outputColumns = null;
        boolean summarizeMatches = configuration.effectiveJoinOperation()
                == SpatialJoinOperation.JOIN_ONE_TO_ONE
                && configuration.oneToOne() != null
                && configuration.oneToOne().mode() == SpatialJoinOneToOneMode.SUMMARIZE_MATCHES;
        if (configuration.outputColumns() == null && summarizeMatches) {
            issues.error(
                    "JOIN_OUTPUT_COLUMNS_REQUIRED",
                    "一对一汇总必须显式选择目标表输出字段",
                    "configuration.outputColumns"
            );
        } else if (configuration.outputColumns() == null) {
            leftColumns.keySet().stream().filter(rightColumns::containsKey).forEach(columnName ->
                    issues.error(
                            "DUPLICATE_COLUMN_NAME",
                            "空间 Join 结果包含同名字段：" + columnName,
                            "configuration"
                    ));
        } else {
            outputColumns = JoinOutputColumnSupport.validate(
                    configuration.outputColumns(), leftColumns, rightColumns, issues);
        }
        validateDistanceOutput(configuration, left, right, outputColumns, issues);
        validateOneToOne(configuration, right, outputColumns, issues);
        if (issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        ProjectedJoin result = configuration.effectiveJoinOperation()
                == SpatialJoinOperation.JOIN_ONE_TO_ONE
                ? oneToOne(configuration, left, right, outputColumns)
                : oneToMany(configuration, left, right, outputColumns);
        Dataset<Row> projected = result.dataset();
        CanvasTableSchema joinedSchema = new CanvasTableSchema(
                configuration.outputTableName(),
                null,
                SparkTypeMapper.fromStructType(projected.schema(), result.fallback())
        );
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(joinedSchema.name(), new SparkCanvasTable(joinedSchema, projected));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateOneToOne(
            SpatialJoinConfiguration configuration,
            SparkCanvasTable right,
            List<JoinOutputColumnSupport.ResolvedOutputColumn> outputColumns,
            CanvasNodeIssueSink issues
    ) {
        if (configuration.effectiveJoinOperation() != SpatialJoinOperation.JOIN_ONE_TO_ONE) {
            return;
        }
        SpatialJoinOneToOneOptions options = configuration.oneToOne();
        if (options == null) {
            issues.error(
                    "SPATIAL_JOIN_ONE_TO_ONE_OPTIONS_REQUIRED",
                    "请配置一对一汇总或保留记录规则",
                    "configuration.oneToOne"
            );
            return;
        }
        if (options.mode() == null) {
            issues.error(
                    "REQUIRED_CONFIGURATION",
                    "请选择一对一处理方式",
                    "configuration.oneToOne.mode"
            );
            return;
        }
        if (options.mode() == SpatialJoinOneToOneMode.SUMMARIZE_MATCHES) {
            validateSummaryOptions(options, outputColumns, right, issues);
        } else {
            validateKeepRule(options.keepRule(), right, issues);
        }
    }

    private static void validateDistanceOutput(
            SpatialJoinConfiguration configuration,
            SparkCanvasTable left,
            SparkCanvasTable right,
            List<JoinOutputColumnSupport.ResolvedOutputColumn> outputColumns,
            CanvasNodeIssueSink issues
    ) {
        SpatialJoinDistanceOutput output = configuration.distanceOutput();
        if (output == null || !output.enabled()) return;
        if (configuration.effectiveJoinOperation() != SpatialJoinOperation.JOIN_ONE_TO_MANY) {
            issues.error("SPATIAL_JOIN_DISTANCE_OUTPUT_REQUIRES_ONE_TO_MANY",
                    "距离输出只支持一对多空间连接",
                    "configuration.distanceOutput.enabled");
        }
        boolean spatial = configuration.spatialNear() != null;
        boolean temporal = configuration.temporalCondition() != null
                && configuration.temporalCondition().usesNearDistance();
        if (!spatial && !temporal) {
            issues.error("SPATIAL_JOIN_DISTANCE_OUTPUT_REQUIRES_NEAR",
                    "距离输出要求启用空间 Near 或时间 Near",
                    "configuration.distanceOutput.enabled");
            return;
        }
        Set<String> names = new HashSet<>();
        if (outputColumns == null) {
            left.schema().columns().forEach(column -> names.add(normalize(column.name())));
            right.schema().columns().forEach(column -> names.add(normalize(column.name())));
        } else {
            outputColumns.forEach(column -> names.add(normalize(column.outputColumnName())));
        }
        if (spatial) {
            CanvasNodeSupport.required(output.spatialDistanceColumnName(),
                    "请输入空间距离输出字段名",
                    "configuration.distanceOutput.spatialDistanceColumnName", issues);
            addOutputName(output.spatialDistanceColumnName(),
                    "configuration.distanceOutput.spatialDistanceColumnName", names, issues);
            if (output.spatialDistanceUnit() == null) {
                issues.error("REQUIRED_CONFIGURATION", "请选择空间距离输出单位",
                        "configuration.distanceOutput.spatialDistanceUnit");
            } else {
                CanvasColumnSchema geometry = CanvasNodeSupport.columns(left.schema())
                        .get(configuration.spatialNear().leftGeometryColumnName());
                if (geometry != null && geometry.geometry() != null) {
                    if (configuration.spatialNear().distanceMethod()
                            == SpatialDistanceMethod.GEODESIC) {
                        if (output.spatialDistanceUnit() == SpatialDistanceUnit.SOURCE_CRS_UNIT) {
                            issues.error("SPATIAL_DISTANCE_UNIT_UNSUPPORTED",
                                    "Near Geodesic 距离输出不能使用来源 CRS 单位",
                                    "configuration.distanceOutput.spatialDistanceUnit");
                        }
                    } else if (configuration.spatialNear().distanceMethod()
                            == SpatialDistanceMethod.PLANAR) {
                        SpatialDistanceSupport.Resolution resolved =
                                SpatialDistanceSupport.sourceUnitsPerConfiguredUnit(
                                        output.spatialDistanceUnit(), geometry.geometry().crs());
                        if (!resolved.valid()) {
                            issues.error("SPATIAL_DISTANCE_UNIT_UNSUPPORTED", resolved.error(),
                                    "configuration.distanceOutput.spatialDistanceUnit");
                        } else if (resolved.angular()) {
                            issues.warning("PLANAR_DISTANCE_USES_ANGULAR_UNITS",
                                    "地理 CRS 的平面距离输出使用角度，结果随纬度变化",
                                    "configuration.distanceOutput.spatialDistanceUnit");
                        }
                    }
                }
            }
        }
        if (temporal) {
            CanvasNodeSupport.required(output.temporalDifferenceColumnName(),
                    "请输入时间差输出字段名",
                    "configuration.distanceOutput.temporalDifferenceColumnName", issues);
            addOutputName(output.temporalDifferenceColumnName(),
                    "configuration.distanceOutput.temporalDifferenceColumnName", names, issues);
            if (output.temporalDifferenceUnit() == null) {
                issues.error("REQUIRED_CONFIGURATION", "请选择时间差输出单位",
                        "configuration.distanceOutput.temporalDifferenceUnit");
            }
        }
    }

    private static void validateSummaryOptions(
            SpatialJoinOneToOneOptions options,
            List<JoinOutputColumnSupport.ResolvedOutputColumn> outputColumns,
            SparkCanvasTable right,
            CanvasNodeIssueSink issues
    ) {
        Set<String> outputNames = new HashSet<>();
        if (outputColumns != null) {
            for (JoinOutputColumnSupport.ResolvedOutputColumn outputColumn : outputColumns) {
                outputNames.add(normalize(outputColumn.outputColumnName()));
                if (outputColumn.sourceSide() == JoinOutputColumnSource.RIGHT) {
                    issues.error(
                            "SPATIAL_JOIN_SUMMARY_RIGHT_FIELD_UNSUPPORTED",
                            "一对一汇总不能直接输出连接表原始字段，请改为统计项",
                            "configuration.outputColumns"
                    );
                }
            }
        }
        CanvasNodeSupport.required(
                options.joinCountColumnName(),
                "请输入 Join Count 字段名",
                "configuration.oneToOne.joinCountColumnName",
                issues
        );
        addOutputName(
                options.joinCountColumnName(),
                "configuration.oneToOne.joinCountColumnName",
                outputNames,
                issues
        );
        if (options.summaryStatistics() == null) {
            issues.error(
                    "REQUIRED_CONFIGURATION",
                    "一对一汇总统计必须是数组",
                    "configuration.oneToOne.summaryStatistics"
            );
            return;
        }
        if (options.summaryStatistics().size()
                > SpatialJoinOneToOneOptions.MAX_SUMMARY_STATISTICS) {
            issues.error(
                    "SPATIAL_JOIN_SUMMARY_STATISTIC_LIMIT_EXCEEDED",
                    "一对一汇总统计不能超过 "
                            + SpatialJoinOneToOneOptions.MAX_SUMMARY_STATISTICS + " 项",
                    "configuration.oneToOne.summaryStatistics"
            );
        }
        Map<String, CanvasColumnSchema> rightColumns = CanvasNodeSupport.columns(right.schema());
        Set<String> statisticIds = new HashSet<>();
        for (int index = 0; index < options.summaryStatistics().size(); index++) {
            SpatialJoinSummaryStatistic statistic = options.summaryStatistics().get(index);
            String path = "configuration.oneToOne.summaryStatistics[" + index + "]";
            if (statistic == null) {
                issues.error("REQUIRED_CONFIGURATION", "一对一汇总统计不能为空", path);
                continue;
            }
            if (!uuid(statistic.statisticId())) {
                issues.error(
                        "INVALID_SPATIAL_JOIN_SUMMARY_STATISTIC_ID",
                        "一对一汇总统计 ID 必须是 UUID",
                        path + ".statisticId"
                );
            } else if (!statisticIds.add(statistic.statisticId())) {
                issues.error(
                        "DUPLICATE_SPATIAL_JOIN_SUMMARY_STATISTIC_ID",
                        "一对一汇总统计 ID 重复",
                        path + ".statisticId"
                );
            }
            if (statistic.kind() == null) {
                issues.error(
                        "REQUIRED_CONFIGURATION",
                        "请选择一对一汇总统计类型",
                        path + ".kind"
                );
            }
            CanvasNodeSupport.required(
                    statistic.sourceColumnName(),
                    "请选择连接表数值字段",
                    path + ".sourceColumnName",
                    issues
            );
            CanvasNodeSupport.required(
                    statistic.outputColumnName(),
                    "请输入统计输出字段名",
                    path + ".outputColumnName",
                    issues
            );
            addOutputName(statistic.outputColumnName(), path + ".outputColumnName",
                    outputNames, issues);
            CanvasColumnSchema source = rightColumns.get(statistic.sourceColumnName());
            if (!CanvasNodeSupport.blank(statistic.sourceColumnName()) && source == null) {
                issues.error(
                        "COLUMN_NOT_FOUND",
                        "连接表统计字段不存在：" + statistic.sourceColumnName(),
                        path + ".sourceColumnName"
                );
            } else if (source != null && !numeric(source.fieldType())) {
                issues.error(
                        "NUMERIC_COLUMN_REQUIRED",
                        "一对一汇总只支持连接表数值字段",
                        path + ".sourceColumnName"
                );
            }
        }
    }

    private static void validateKeepRule(
            SpatialJoinKeepRule rule,
            SparkCanvasTable right,
            CanvasNodeIssueSink issues
    ) {
        String path = "configuration.oneToOne.keepRule";
        if (rule == null) {
            issues.error(
                    "SPATIAL_JOIN_KEEP_RULE_REQUIRED",
                    "请配置一对一保留记录规则",
                    path
            );
            return;
        }
        if (rule.strategy() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择保留记录策略", path + ".strategy");
        }
        CanvasSortSupport.validate(
                rule.stableOrder(),
                right,
                issues,
                path + ".stableOrder",
                "SPATIAL_JOIN_STABLE_ORDER_REQUIRED",
                "DUPLICATE_SORT_COLUMN"
        );
        if (rule.strategy() == null || rule.strategy() == SpatialJoinKeepStrategy.FIRST) {
            if (rule.strategy() == SpatialJoinKeepStrategy.FIRST
                    && !CanvasNodeSupport.blank(rule.orderByColumnName())) {
                issues.error(
                        "SPATIAL_JOIN_FIRST_ORDER_FIELD_NOT_ALLOWED",
                        "FIRST 由稳定顺序决定，不使用主排序字段",
                        path + ".orderByColumnName"
                );
            }
            return;
        }
        CanvasNodeSupport.required(
                rule.orderByColumnName(),
                "请选择保留记录的主排序字段",
                path + ".orderByColumnName",
                issues
        );
        Map<String, CanvasColumnSchema> rightColumns = CanvasNodeSupport.columns(right.schema());
        CanvasColumnSchema orderBy = rightColumns.get(rule.orderByColumnName());
        if (!CanvasNodeSupport.blank(rule.orderByColumnName()) && orderBy == null) {
            issues.error(
                    "COLUMN_NOT_FOUND",
                    "连接表排序字段不存在：" + rule.orderByColumnName(),
                    path + ".orderByColumnName"
            );
            return;
        }
        if (orderBy == null) {
            return;
        }
        if ((rule.strategy() == SpatialJoinKeepStrategy.LARGEST
                || rule.strategy() == SpatialJoinKeepStrategy.SMALLEST)
                && !numeric(orderBy.fieldType())) {
            issues.error(
                    "NUMERIC_COLUMN_REQUIRED",
                    rule.strategy() + " 要求数值主排序字段",
                    path + ".orderByColumnName"
            );
        }
        if ((rule.strategy() == SpatialJoinKeepStrategy.NEWEST
                || rule.strategy() == SpatialJoinKeepStrategy.OLDEST)
                && !temporal(orderBy.fieldType())) {
            issues.error(
                    "TEMPORAL_COLUMN_REQUIRED",
                    rule.strategy() + " 要求日期或时间主排序字段",
                    path + ".orderByColumnName"
            );
        }
        if (rule.stableOrder() != null && rule.stableOrder().stream()
                .anyMatch(item -> item != null
                        && rule.orderByColumnName().equals(item.columnName()))) {
            issues.error(
                    "DUPLICATE_SORT_COLUMN",
                    "主排序字段不能在稳定顺序中重复",
                    path + ".stableOrder"
            );
        }
    }

    private static ProjectedJoin oneToMany(
            SpatialJoinConfiguration configuration,
            SparkCanvasTable left,
            SparkCanvasTable right,
            List<JoinOutputColumnSupport.ResolvedOutputColumn> outputColumns
    ) {
        Dataset<Row> leftDataset = left.dataset().alias(LEFT_ALIAS);
        Dataset<Row> rightDataset = right.dataset().alias(RIGHT_ALIAS);
        Dataset<Row> joined = join(configuration, leftDataset, rightDataset, left.schema());
        return project(configuration, joined, leftDataset, rightDataset,
                left.schema(), right.schema(), outputColumns, false);
    }

    private static ProjectedJoin oneToOne(
            SpatialJoinConfiguration configuration,
            SparkCanvasTable left,
            SparkCanvasTable right,
            List<JoinOutputColumnSupport.ResolvedOutputColumn> outputColumns
    ) {
        Set<String> internalNames = new HashSet<>();
        internalNames.addAll(CanvasNodeSupport.columns(left.schema()).keySet());
        internalNames.addAll(CanvasNodeSupport.columns(right.schema()).keySet());
        String targetRowId = CanvasSortSupport.temporaryColumnName(
                internalNames, "__datascalpel_spatial_join_target_row_id");
        internalNames.add(targetRowId);
        String matchMarker = CanvasSortSupport.temporaryColumnName(
                internalNames, "__datascalpel_spatial_join_match_marker");

        Dataset<Row> leftDataset = left.dataset()
                .withColumn(targetRowId, functions.monotonically_increasing_id())
                .alias(LEFT_ALIAS);
        Dataset<Row> rightDataset = right.dataset()
                .withColumn(matchMarker, functions.lit(1L))
                .alias(RIGHT_ALIAS);
        Dataset<Row> joined = join(configuration, leftDataset, rightDataset, left.schema());
        SpatialJoinOneToOneOptions options = configuration.oneToOne();
        if (options.mode() == SpatialJoinOneToOneMode.SUMMARIZE_MATCHES) {
            return summarize(joined, leftDataset, rightDataset, targetRowId, matchMarker,
                    outputColumns, right.schema(), options);
        }
        return keepOne(configuration, joined, leftDataset, rightDataset, targetRowId, matchMarker,
                left.schema(), right.schema(), outputColumns, options.keepRule(), internalNames);
    }

    private static Dataset<Row> join(
            SpatialJoinConfiguration configuration,
            Dataset<Row> leftDataset,
            Dataset<Row> rightDataset,
            CanvasTableSchema leftSchema
    ) {
        SpatialJoinNearSupport.PreparedGeodesicJoin preparedNear =
                SpatialJoinNearSupport.prepareGeodesicJoin(
                        configuration.spatialNear(), leftDataset, rightDataset,
                        LEFT_ALIAS, RIGHT_ALIAS);
        if (preparedNear != null) {
            leftDataset = preparedNear.left();
            rightDataset = preparedNear.right();
        }
        Column expression = null;
        for (SpatialJoinCondition condition : configuration.conditions()) {
            Column leftGeometry = leftDataset.col(
                    CanvasNodeSupport.quoteIdentifier(condition.leftGeometryColumnName()));
            Column rightGeometry = rightDataset.col(
                    CanvasNodeSupport.quoteIdentifier(condition.rightGeometryColumnName()));
            Column current = predicate(condition.predicate(), leftGeometry, rightGeometry);
            expression = expression == null ? current : expression.and(current);
        }
        if (configuration.spatialNear() != null) {
            CanvasColumnSchema geometryColumn = CanvasNodeSupport.columns(leftSchema)
                    .get(configuration.spatialNear().leftGeometryColumnName());
            Column current = SpatialJoinNearSupport.expression(
                    configuration.spatialNear(), leftDataset, rightDataset,
                    geometryColumn.geometry(), preparedNear);
            expression = expression == null ? current : expression.and(current);
        }
        if (configuration.attributeConditions() != null) {
            for (JoinCondition condition : configuration.attributeConditions()) {
                Column current = leftDataset
                        .col(CanvasNodeSupport.quoteIdentifier(condition.leftColumnName()))
                        .equalTo(rightDataset.col(
                                CanvasNodeSupport.quoteIdentifier(condition.rightColumnName())));
                expression = expression == null ? current : expression.and(current);
            }
        }
        if (configuration.temporalCondition() != null) {
            Column current = SpatialJoinTemporalSupport.expression(
                    configuration.temporalCondition(), leftDataset, rightDataset);
            expression = expression == null ? current : expression.and(current);
        }
        Dataset<Row> joined = leftDataset.join(
                rightDataset,
                expression,
                configuration.joinType() == JoinType.LEFT ? "left" : "inner"
        );
        return SpatialJoinNearSupport.removeInternalColumns(joined, preparedNear);
    }

    private static ProjectedJoin summarize(
            Dataset<Row> joined,
            Dataset<Row> leftDataset,
            Dataset<Row> rightDataset,
            String targetRowId,
            String matchMarker,
            List<JoinOutputColumnSupport.ResolvedOutputColumn> outputColumns,
            CanvasTableSchema rightSchema,
            SpatialJoinOneToOneOptions options
    ) {
        List<Column> aggregates = new ArrayList<>();
        List<CanvasColumnSchema> fallback = new ArrayList<>();
        for (JoinOutputColumnSupport.ResolvedOutputColumn outputColumn : outputColumns) {
            aggregates.add(functions.first(
                    leftDataset.col(CanvasNodeSupport.quoteIdentifier(
                            outputColumn.sourceColumn().name())), false
            ).alias(outputColumn.outputColumnName()));
            fallback.add(JoinOutputColumnSupport.copyWithName(
                    outputColumn.sourceColumn(), outputColumn.outputColumnName()));
        }
        aggregates.add(functions.count(rightDataset.col(
                CanvasNodeSupport.quoteIdentifier(matchMarker)))
                .alias(options.joinCountColumnName()));
        fallback.add(scalarColumn(options.joinCountColumnName(), PlatformDataType.LONG, false));
        Map<String, CanvasColumnSchema> rightColumns = CanvasNodeSupport.columns(rightSchema);
        for (SpatialJoinSummaryStatistic statistic : options.summaryStatistics()) {
            Column source = rightDataset.col(
                    CanvasNodeSupport.quoteIdentifier(statistic.sourceColumnName()));
            aggregates.add(summaryExpression(source, statistic).alias(statistic.outputColumnName()));
            fallback.add(summaryFallback(
                    rightColumns.get(statistic.sourceColumnName()), statistic));
        }
        Column first = aggregates.getFirst();
        Dataset<Row> summarized = joined.groupBy(leftDataset.col(
                        CanvasNodeSupport.quoteIdentifier(targetRowId)))
                .agg(first, aggregates.subList(1, aggregates.size()).toArray(Column[]::new))
                .drop(targetRowId);
        return new ProjectedJoin(summarized, fallback);
    }

    private static ProjectedJoin keepOne(
            SpatialJoinConfiguration configuration,
            Dataset<Row> joined,
            Dataset<Row> leftDataset,
            Dataset<Row> rightDataset,
            String targetRowId,
            String matchMarker,
            CanvasTableSchema leftSchema,
            CanvasTableSchema rightSchema,
            List<JoinOutputColumnSupport.ResolvedOutputColumn> outputColumns,
            SpatialJoinKeepRule rule,
            Set<String> internalNames
    ) {
        String rankColumn = CanvasSortSupport.temporaryColumnName(
                internalNames, "__datascalpel_spatial_join_rank");
        internalNames.add(rankColumn);
        String tieCountColumn = CanvasSortSupport.temporaryColumnName(
                internalNames, "__datascalpel_spatial_join_tie_count");

        List<Column> order = new ArrayList<>();
        List<Column> tiePartition = new ArrayList<>();
        tiePartition.add(leftDataset.col(CanvasNodeSupport.quoteIdentifier(targetRowId)));
        if (rule.strategy() != SpatialJoinKeepStrategy.FIRST) {
            Column primary = rightDataset.col(
                    CanvasNodeSupport.quoteIdentifier(rule.orderByColumnName()));
            order.add(primaryOrder(primary, rule.strategy()));
            tiePartition.add(primary);
        }
        order.addAll(Arrays.asList(
                CanvasSortSupport.expressions(rightDataset, rule.stableOrder())));
        for (var stable : rule.stableOrder()) {
            tiePartition.add(rightDataset.col(
                    CanvasNodeSupport.quoteIdentifier(stable.columnName())));
        }
        WindowSpec rankWindow = Window.partitionBy(leftDataset.col(
                        CanvasNodeSupport.quoteIdentifier(targetRowId)))
                .orderBy(order.toArray(Column[]::new));
        WindowSpec tieWindow = Window.partitionBy(tiePartition.toArray(Column[]::new));
        Dataset<Row> ranked = joined
                .withColumn(tieCountColumn, functions.count(functions.lit(1L)).over(tieWindow))
                .withColumn(rankColumn, functions.row_number().over(rankWindow));
        Dataset<Row> top = ranked.filter(
                ranked.col(CanvasNodeSupport.quoteIdentifier(rankColumn)).equalTo(1));
        Column uniqueTop = top.col(CanvasNodeSupport.quoteIdentifier(matchMarker)).isNull()
                .or(ranked.col(CanvasNodeSupport.quoteIdentifier(tieCountColumn)).equalTo(1));
        Column stable = functions.when(
                uniqueTop,
                functions.lit(true)
        ).otherwise(functions.raise_error(functions.lit(
                "SPATIAL_JOIN_KEEP_ORDER_NOT_UNIQUE")).cast("boolean"));
        Dataset<Row> selected = top.filter(stable);
        return project(configuration, selected, leftDataset, rightDataset,
                leftSchema, rightSchema, outputColumns, true);
    }

    private static ProjectedJoin project(
            SpatialJoinConfiguration configuration,
            Dataset<Row> joined,
            Dataset<Row> leftDataset,
            Dataset<Row> rightDataset,
            CanvasTableSchema leftSchema,
            CanvasTableSchema rightSchema,
            List<JoinOutputColumnSupport.ResolvedOutputColumn> outputColumns,
            boolean forceLegacyProjection
    ) {
        boolean appendDistances = configuration.distanceOutput() != null
                && configuration.distanceOutput().enabled()
                && configuration.effectiveJoinOperation() == SpatialJoinOperation.JOIN_ONE_TO_MANY;
        if (outputColumns == null && !forceLegacyProjection && !appendDistances) {
            return new ProjectedJoin(
                    joined,
                    CanvasNodeSupport.concatenatedColumns(leftSchema, rightSchema)
            );
        }
        List<Column> projections = new ArrayList<>();
        List<CanvasColumnSchema> fallback = new ArrayList<>();
        if (outputColumns == null) {
            addLegacyProjections(leftDataset, leftSchema, projections, fallback);
            addLegacyProjections(rightDataset, rightSchema, projections, fallback);
        } else {
            for (JoinOutputColumnSupport.ResolvedOutputColumn outputColumn : outputColumns) {
                Dataset<Row> sourceDataset = outputColumn.sourceSide() == JoinOutputColumnSource.LEFT
                        ? leftDataset : rightDataset;
                projections.add(sourceDataset
                        .col(CanvasNodeSupport.quoteIdentifier(outputColumn.sourceColumn().name()))
                        .alias(outputColumn.outputColumnName()));
                fallback.add(JoinOutputColumnSupport.copyWithName(
                        outputColumn.sourceColumn(), outputColumn.outputColumnName()));
            }
        }
        if (appendDistances) {
            addDistanceProjections(configuration, leftDataset, rightDataset,
                    leftSchema, projections, fallback);
        }
        return new ProjectedJoin(joined.select(projections.toArray(Column[]::new)), fallback);
    }

    private static void addDistanceProjections(
            SpatialJoinConfiguration configuration,
            Dataset<Row> left,
            Dataset<Row> right,
            CanvasTableSchema leftSchema,
            List<Column> projections,
            List<CanvasColumnSchema> fallback
    ) {
        SpatialJoinDistanceOutput output = configuration.distanceOutput();
        if (configuration.spatialNear() != null) {
            CanvasColumnSchema geometryColumn = CanvasNodeSupport.columns(leftSchema)
                    .get(configuration.spatialNear().leftGeometryColumnName());
            projections.add(SpatialJoinNearSupport.outputDistance(
                    configuration.spatialNear(), output.spatialDistanceUnit(), left, right,
                    geometryColumn.geometry()).alias(output.spatialDistanceColumnName()));
            fallback.add(SpatialNearestNodeOperator.decimalColumn(
                    output.spatialDistanceColumnName(), configuration.joinType() == JoinType.LEFT));
        }
        if (configuration.temporalCondition() != null
                && configuration.temporalCondition().usesNearDistance()) {
            Column micros = SpatialJoinTemporalSupport.gapMicros(
                    configuration.temporalCondition(), left, right).cast("double");
            projections.add(SpatialJoinNearSupport.decimal(
                    micros,
                    SpatialJoinTemporalSupport.microsPer(output.temporalDifferenceUnit()),
                    "SPATIAL_JOIN_TEMPORAL_DIFFERENCE_INVALID"
            ).alias(output.temporalDifferenceColumnName()));
            fallback.add(SpatialNearestNodeOperator.decimalColumn(
                    output.temporalDifferenceColumnName(), configuration.joinType() == JoinType.LEFT));
        }
    }

    private static void addLegacyProjections(
            Dataset<Row> source,
            CanvasTableSchema schema,
            List<Column> projections,
            List<CanvasColumnSchema> fallback
    ) {
        for (CanvasColumnSchema column : schema.columns()) {
            projections.add(source.col(CanvasNodeSupport.quoteIdentifier(column.name()))
                    .alias(column.name()));
            fallback.add(column);
        }
    }

    private static Column primaryOrder(Column column, SpatialJoinKeepStrategy strategy) {
        return switch (strategy) {
            case LARGEST, NEWEST -> column.desc_nulls_last();
            case SMALLEST, OLDEST -> column.asc_nulls_last();
            case FIRST -> throw new IllegalArgumentException("FIRST has no primary order field");
        };
    }

    private static Column summaryExpression(
            Column source,
            SpatialJoinSummaryStatistic statistic
    ) {
        return switch (statistic.kind()) {
            case SUM -> functions.sum(source);
            case MIN -> functions.min(source);
            case MAX -> functions.max(source);
            case MEAN -> functions.avg(source);
            case STDDEV -> functions.stddev_samp(source);
        };
    }

    private static CanvasColumnSchema summaryFallback(
            CanvasColumnSchema source,
            SpatialJoinSummaryStatistic statistic
    ) {
        PlatformDataType type = switch (statistic.kind()) {
            case MEAN, STDDEV -> PlatformDataType.DOUBLE;
            case SUM, MIN, MAX -> source.fieldType();
        };
        return scalarColumn(statistic.outputColumnName(), type, true);
    }

    private static CanvasColumnSchema scalarColumn(
            String name,
            PlatformDataType type,
            boolean nullable
    ) {
        return new CanvasColumnSchema(
                name, type, null, null, null, nullable,
                null, false, false, null
        );
    }

    private static void addOutputName(
            String name,
            String path,
            Set<String> names,
            CanvasNodeIssueSink issues
    ) {
        if (!CanvasNodeSupport.blank(name) && !names.add(normalize(name))) {
            issues.error("DUPLICATE_COLUMN_NAME", "空间 Join 输出字段名重复：" + name, path);
        }
    }

    private static boolean uuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String normalize(String value) {
        return value.toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean numeric(PlatformDataType type) {
        return type == PlatformDataType.BYTE
                || type == PlatformDataType.SHORT
                || type == PlatformDataType.INTEGER
                || type == PlatformDataType.LONG
                || type == PlatformDataType.FLOAT
                || type == PlatformDataType.DOUBLE
                || type == PlatformDataType.DECIMAL;
    }

    private static boolean temporal(PlatformDataType type) {
        return type == PlatformDataType.DATE
                || type == PlatformDataType.TIMESTAMP
                || type == PlatformDataType.TIMESTAMP_NTZ;
    }

    private record ProjectedJoin(Dataset<Row> dataset, List<CanvasColumnSchema> fallback) {
    }

    private static void validateConditions(
            SpatialJoinConfiguration configuration,
            Map<String, CanvasColumnSchema> leftColumns,
            Map<String, CanvasColumnSchema> rightColumns,
            CanvasNodeIssueSink issues
    ) {
        if (configuration.conditions() == null) {
            return;
        }
        Set<String> keys = new HashSet<>();
        for (int index = 0; index < configuration.conditions().size(); index++) {
            SpatialJoinCondition condition = configuration.conditions().get(index);
            String path = "configuration.conditions[" + index + "]";
            if (condition == null
                    || CanvasNodeSupport.blank(condition.leftGeometryColumnName())
                    || condition.predicate() == null
                    || CanvasNodeSupport.blank(condition.rightGeometryColumnName())) {
                issues.error("REQUIRED_CONFIGURATION", "空间 Join 条件不完整", path);
                continue;
            }
            String key = condition.leftGeometryColumnName() + "\u0000"
                    + condition.predicate() + "\u0000" + condition.rightGeometryColumnName();
            if (!keys.add(key)) {
                issues.error("DUPLICATE_SPATIAL_JOIN_CONDITION", "空间 Join 条件重复", path);
            }
            CanvasColumnSchema left = leftColumns.get(condition.leftGeometryColumnName());
            CanvasColumnSchema right = rightColumns.get(condition.rightGeometryColumnName());
            if (left == null) {
                issues.error("COLUMN_NOT_FOUND", "左表字段不存在：" + condition.leftGeometryColumnName(),
                        path + ".leftGeometryColumnName");
            }
            if (right == null) {
                issues.error("COLUMN_NOT_FOUND", "右表字段不存在：" + condition.rightGeometryColumnName(),
                        path + ".rightGeometryColumnName");
            }
            if (left == null || right == null) {
                continue;
            }
            if (left.fieldType() != PlatformDataType.GEOMETRY
                    || right.fieldType() != PlatformDataType.GEOMETRY) {
                issues.error(
                        "GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                        "空间 Join 条件两侧都必须是 Geometry 字段",
                        path
                );
                continue;
            }
            GeometryTypeDefinition leftGeometry = left.geometry();
            GeometryTypeDefinition rightGeometry = right.geometry();
            if (leftGeometry == null || rightGeometry == null) {
                issues.error("GEOMETRY_TYPE_DEFINITION_REQUIRED", "空间字段缺少 Geometry 定义", path);
                continue;
            }
            SpatialTransformNodeOperator.validateCrs(leftGeometry.crs(), path, issues);
            SpatialTransformNodeOperator.validateCrs(rightGeometry.crs(), path, issues);
            if (leftGeometry.dimension() != rightGeometry.dimension()) {
                issues.error("SPATIAL_DIMENSION_MISMATCH", "空间 Join 两侧坐标维度不一致", path);
            }
            if (leftGeometry.dimension() != CoordinateDimension.XY
                    || rightGeometry.dimension() != CoordinateDimension.XY) {
                issues.error("UNSUPPORTED_GEOMETRY_DIMENSION", "空间 Join 第一阶段只支持 XY 维度", path);
            }
            if (!leftGeometry.crs().equals(rightGeometry.crs())) {
                issues.error(
                        "SPATIAL_CRS_MISMATCH",
                        "空间 Join 两侧 CRS 不一致，请先使用空间转换节点",
                        path
                );
            }
        }
    }

    private static Column predicate(
            SpatialPredicate predicate,
            Column left,
            Column right
    ) {
        return switch (predicate) {
            case INTERSECTS -> st_predicates.ST_Intersects(left, right);
            case CONTAINS -> st_predicates.ST_Contains(left, right);
            case WITHIN -> st_predicates.ST_Within(left, right);
            case COVERS -> st_predicates.ST_Covers(left, right);
            case COVERED_BY -> st_predicates.ST_CoveredBy(left, right);
            case TOUCHES -> st_predicates.ST_Touches(left, right);
            case OVERLAPS -> st_predicates.ST_Overlaps(left, right);
            case CROSSES -> st_predicates.ST_Crosses(left, right);
            case EQUALS -> st_predicates.ST_Equals(left, right);
        };
    }
}
