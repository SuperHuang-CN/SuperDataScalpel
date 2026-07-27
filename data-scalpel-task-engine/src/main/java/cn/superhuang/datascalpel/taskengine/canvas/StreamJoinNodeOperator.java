package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.JoinCondition;
import cn.superhuang.data.scalpel.contract.task.JoinOperator;
import cn.superhuang.data.scalpel.contract.task.StreamJoinConfiguration;
import cn.superhuang.data.scalpel.contract.task.StreamJoinNodeDefinition;
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

public final class StreamJoinNodeOperator implements CanvasNodeOperator {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.STREAM_JOIN;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.PROCESSOR;
    }

    @Override
    public Set<CanvasExecutionMode> supportedModes() {
        return Set.of(CanvasExecutionMode.STREAMING);
    }

    @Override
    public CanvasNodeOperationResult apply(
            CanvasNodeDefinition definition,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeOperationContext context
    ) {
        if (!(definition instanceof StreamJoinNodeDefinition node)) {
            throw new IllegalArgumentException("STREAM_JOIN operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        StreamJoinConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(inputSchemas);
        CanvasNodeIssueSink issues = context.issues();
        CanvasNodeSupport.required(configuration.leftTableName(), "请选择左流表", "configuration.leftTableName", issues);
        CanvasNodeSupport.required(configuration.rightTableName(), "请选择右静态表", "configuration.rightTableName", issues);
        CanvasNodeSupport.required(configuration.outputTableName(), "请输入输出表名", "configuration.outputTableName", issues);
        if (configuration.joinType() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择 Stream Join 类型", "configuration.joinType");
        }
        if (configuration.conditions() == null || configuration.conditions().isEmpty()) {
            issues.error("REQUIRED_CONFIGURATION", "至少配置一个等值条件", "configuration.conditions");
        }
        if (inputs.containsKey(configuration.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName");
        }
        SparkCanvasTable left = inputs.get(configuration.leftTableName());
        SparkCanvasTable right = inputs.get(configuration.rightTableName());
        if (left == null) {
            issues.error("TABLE_NOT_FOUND", "左流表不在上游数据中", "configuration.leftTableName");
        } else if (left.schema().datasetKind() != CanvasDatasetKind.UNBOUNDED) {
            issues.error("STREAM_JOIN_LEFT_MUST_BE_UNBOUNDED", "Stream Join 左侧必须为无界流",
                    "configuration.leftTableName");
        }
        if (right == null) {
            issues.error("TABLE_NOT_FOUND", "右静态表不在上游数据中", "configuration.rightTableName");
        } else if (right.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error("STREAM_JOIN_RIGHT_MUST_BE_BOUNDED", "Stream Join 右侧必须为有界静态表",
                    "configuration.rightTableName");
        }
        if (left == null || right == null) return CanvasNodeOperationResult.invalid(inputSchemas);
        Map<String, CanvasColumnSchema> leftColumns = CanvasNodeSupport.columns(left.schema());
        Map<String, CanvasColumnSchema> rightColumns = CanvasNodeSupport.columns(right.schema());
        leftColumns.keySet().stream().filter(rightColumns::containsKey).forEach(column ->
                issues.error("DUPLICATE_COLUMN_NAME", "Stream Join 结果包含同名字段：" + column, "configuration"));
        validateConditions(configuration.conditions(), leftColumns, rightColumns, issues);
        if (issues.hasErrors()) return CanvasNodeOperationResult.invalid(inputSchemas);

        Dataset<Row> leftDataset = left.dataset().alias("left_stream");
        Dataset<Row> rightDataset = right.dataset().alias("right_static");
        Column expression = null;
        for (JoinCondition condition : configuration.conditions()) {
            Column current = leftDataset.col(CanvasNodeSupport.quoteIdentifier(condition.leftColumnName()))
                    .equalTo(rightDataset.col(CanvasNodeSupport.quoteIdentifier(condition.rightColumnName())));
            expression = expression == null ? current : expression.and(current);
        }
        Dataset<Row> joined = leftDataset.join(
                rightDataset, expression, configuration.joinType().name().toLowerCase());
        List<CanvasColumnSchema> fallback = CanvasNodeSupport.concatenatedColumns(left.schema(), right.schema());
        CanvasTableSchema schema = new CanvasTableSchema(
                configuration.outputTableName(),
                null,
                SparkTypeMapper.fromStructType(joined.schema(), fallback),
                CanvasDatasetKind.UNBOUNDED,
                left.schema().eventTimeColumn(),
                left.schema().watermarkDelay()
        );
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(schema.name(), new SparkCanvasTable(schema, joined));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateConditions(
            List<JoinCondition> conditions,
            Map<String, CanvasColumnSchema> leftColumns,
            Map<String, CanvasColumnSchema> rightColumns,
            CanvasNodeIssueSink issues
    ) {
        if (conditions == null) return;
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < conditions.size(); index++) {
            JoinCondition condition = conditions.get(index);
            String path = "configuration.conditions[" + index + "]";
            if (condition == null || condition.operator() != JoinOperator.EQUALS
                    || CanvasNodeSupport.blank(condition.leftColumnName())
                    || CanvasNodeSupport.blank(condition.rightColumnName())) {
                issues.error("REQUIRED_CONFIGURATION", "Stream Join 条件不完整", path);
                continue;
            }
            if (!seen.add(condition.leftColumnName() + "\u0000" + condition.rightColumnName())) {
                issues.error("DUPLICATE_JOIN_CONDITION", "Stream Join 条件重复", path);
            }
            if (!leftColumns.containsKey(condition.leftColumnName())) {
                issues.error("COLUMN_NOT_FOUND", "左流字段不存在：" + condition.leftColumnName(), path);
            }
            if (!rightColumns.containsKey(condition.rightColumnName())) {
                issues.error("COLUMN_NOT_FOUND", "右静态表字段不存在：" + condition.rightColumnName(), path);
            }
        }
    }
}
