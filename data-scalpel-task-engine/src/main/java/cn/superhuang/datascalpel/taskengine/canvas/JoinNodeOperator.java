package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.JoinCondition;
import cn.superhuang.data.scalpel.contract.task.JoinConfiguration;
import cn.superhuang.data.scalpel.contract.task.JoinNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JoinOperator;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JoinNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.JOIN;
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
        if (!(definition instanceof JoinNodeDefinition node)) {
            throw new IllegalArgumentException("JOIN operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        JoinConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        CanvasNodeIssueSink issues = context.issues();
        CanvasNodeSupport.required(
                configuration.leftTableName(), "请选择左表", "configuration.leftTableName", issues);
        CanvasNodeSupport.required(
                configuration.rightTableName(), "请选择右表", "configuration.rightTableName", issues);
        CanvasNodeSupport.required(
                configuration.outputTableName(), "请输入输出表名", "configuration.outputTableName", issues);
        if (configuration.joinType() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择 Join 类型", "configuration.joinType");
        }
        if (configuration.conditions() == null || configuration.conditions().isEmpty()) {
            issues.error("REQUIRED_CONFIGURATION", "至少配置一个 Join 条件", "configuration.conditions");
        }
        if (!CanvasNodeSupport.blank(configuration.leftTableName())
                && configuration.leftTableName().equals(configuration.rightTableName())) {
            issues.error("INVALID_JOIN_TABLE", "Join 左右表不能相同", "configuration.rightTableName");
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
            issues.error(
                    "TABLE_NOT_FOUND",
                    "左表不在上游数据中：" + configuration.leftTableName(),
                    "configuration.leftTableName"
            );
        }
        if (!CanvasNodeSupport.blank(configuration.rightTableName()) && right == null) {
            issues.error(
                    "TABLE_NOT_FOUND",
                    "右表不在上游数据中：" + configuration.rightTableName(),
                    "configuration.rightTableName"
            );
        }
        if (left == null || right == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Map<String, CanvasColumnSchema> leftColumns = CanvasNodeSupport.columns(left.schema());
        Map<String, CanvasColumnSchema> rightColumns = CanvasNodeSupport.columns(right.schema());
        leftColumns.keySet().stream().filter(rightColumns::containsKey).forEach(columnName ->
                issues.error(
                        "DUPLICATE_COLUMN_NAME",
                        "Join 结果包含同名字段：" + columnName,
                        "configuration"
                ));
        validateConditions(configuration, leftColumns, rightColumns, issues);
        if (issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> leftDataset = left.dataset().alias("left_input");
        Dataset<Row> rightDataset = right.dataset().alias("right_input");
        Column expression = null;
        for (JoinCondition condition : configuration.conditions()) {
            Column current = leftDataset
                    .col(CanvasNodeSupport.quoteIdentifier(condition.leftColumnName()))
                    .equalTo(rightDataset.col(CanvasNodeSupport.quoteIdentifier(condition.rightColumnName())));
            expression = expression == null ? current : expression.and(current);
        }
        Dataset<Row> joined = leftDataset.join(
                rightDataset,
                expression,
                configuration.joinType().name().toLowerCase()
        );
        List<CanvasColumnSchema> fallback = CanvasNodeSupport.concatenatedColumns(left.schema(), right.schema());
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
            JoinConfiguration configuration,
            Map<String, CanvasColumnSchema> leftColumns,
            Map<String, CanvasColumnSchema> rightColumns,
            CanvasNodeIssueSink issues
    ) {
        if (configuration.conditions() == null) {
            return;
        }
        Set<String> conditionKeys = new HashSet<>();
        for (int index = 0; index < configuration.conditions().size(); index++) {
            JoinCondition condition = configuration.conditions().get(index);
            String path = "configuration.conditions[" + index + "]";
            if (condition == null
                    || CanvasNodeSupport.blank(condition.leftColumnName())
                    || CanvasNodeSupport.blank(condition.rightColumnName())
                    || condition.operator() != JoinOperator.EQUALS) {
                issues.error("REQUIRED_CONFIGURATION", "Join 条件不完整或操作符不受支持", path);
                continue;
            }
            String key = condition.leftColumnName() + "\u0000" + condition.rightColumnName();
            if (!conditionKeys.add(key)) {
                issues.error("DUPLICATE_JOIN_CONDITION", "Join 条件重复", path);
            }
            if (!leftColumns.containsKey(condition.leftColumnName())) {
                issues.error("COLUMN_NOT_FOUND", "左表字段不存在：" + condition.leftColumnName(), path);
            }
            if (!rightColumns.containsKey(condition.rightColumnName())) {
                issues.error("COLUMN_NOT_FOUND", "右表字段不存在：" + condition.rightColumnName(), path);
            }
        }
    }

}
