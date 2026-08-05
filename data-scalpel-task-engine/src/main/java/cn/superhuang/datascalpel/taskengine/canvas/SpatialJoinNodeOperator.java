package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.JoinType;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinCondition;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinNodeDefinition;
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
import org.apache.spark.sql.sedona_sql.expressions.st_predicates;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SpatialJoinNodeOperator implements CanvasNodeOperator {

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
        } else if (configuration.joinType() != JoinType.INNER) {
            issues.error(
                    "SPATIAL_JOIN_TYPE_UNSUPPORTED",
                    "空间 Join 第一阶段只支持 INNER",
                    "configuration.joinType"
            );
        }
        if (configuration.conditions() == null || configuration.conditions().isEmpty()) {
            issues.error("REQUIRED_CONFIGURATION", "至少配置一个空间条件", "configuration.conditions");
        } else if (configuration.conditions().size() > SpatialJoinConfiguration.MAX_CONDITIONS) {
            issues.error(
                    "SPATIAL_JOIN_CONDITION_LIMIT_EXCEEDED",
                    "空间条件不能超过 " + SpatialJoinConfiguration.MAX_CONDITIONS + " 个",
                    "configuration.conditions"
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
        leftColumns.keySet().stream().filter(rightColumns::containsKey).forEach(columnName ->
                issues.error(
                        "DUPLICATE_COLUMN_NAME",
                        "空间 Join 结果包含同名字段：" + columnName,
                        "configuration"
                ));
        validateConditions(configuration, leftColumns, rightColumns, issues);
        if (issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> leftDataset = left.dataset().alias("left_spatial_input");
        Dataset<Row> rightDataset = right.dataset().alias("right_spatial_input");
        Column expression = null;
        for (SpatialJoinCondition condition : configuration.conditions()) {
            Column leftGeometry = leftDataset.col(
                    CanvasNodeSupport.quoteIdentifier(condition.leftGeometryColumnName()));
            Column rightGeometry = rightDataset.col(
                    CanvasNodeSupport.quoteIdentifier(condition.rightGeometryColumnName()));
            Column current = predicate(condition.predicate(), leftGeometry, rightGeometry);
            expression = expression == null ? current : expression.and(current);
        }
        Dataset<Row> joined = leftDataset.join(rightDataset, expression, "inner");
        List<CanvasColumnSchema> fallback =
                CanvasNodeSupport.concatenatedColumns(left.schema(), right.schema());
        CanvasTableSchema joinedSchema = new CanvasTableSchema(
                configuration.outputTableName(),
                null,
                SparkTypeMapper.fromStructType(joined.schema(), fallback)
        );
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(joinedSchema.name(), new SparkCanvasTable(joinedSchema, joined));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
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
