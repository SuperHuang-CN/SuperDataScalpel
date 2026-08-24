package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.FilterConfiguration;
import cn.superhuang.data.scalpel.contract.task.FilterConditionMode;
import cn.superhuang.data.scalpel.contract.task.FilterOperation;
import cn.superhuang.data.scalpel.contract.task.FilterSqlExpressionPolicy;
import cn.superhuang.data.scalpel.contract.task.FilterNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ProcessorOutput;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FilterNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.FILTER;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.PROCESSOR;
    }

    @Override
    public Set<CanvasExecutionMode> supportedModes() {
        return Set.of(CanvasExecutionMode.BATCH, CanvasExecutionMode.STREAMING);
    }

    @Override
    public CanvasNodeOperationResult apply(
            CanvasNodeDefinition definition,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeOperationContext context
    ) {
        if (!(definition instanceof FilterNodeDefinition node)) {
            throw new IllegalArgumentException("FILTER operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        FilterConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        if (!ProcessorOperationSupport.isInternalSingle(configuration.operations())) {
            return ProcessorOperationSupport.apply(
                    configuration.operations(), inputs, context, false,
                    (operation, scopedContext) -> {
                        FilterOperation sourceOperation = (FilterOperation) operation.operation();
                        FilterConfiguration single = new FilterConfiguration(List.of(new FilterOperation(
                                ProcessorOperationSupport.INTERNAL_OPERATION_ID,
                                operation.temporarySourceTableName(),
                                new ProcessorOutput.CreateNewTable(operation.outputTableName()),
                                sourceOperation.mode(),
                                sourceOperation.condition(),
                                sourceOperation.sqlExpression()
                        )));
                        return apply(new FilterNodeDefinition(node.id(), node.name(), node.layout(), single),
                                Map.of(operation.temporarySourceTableName(), operation.source()), scopedContext);
                    }
            );
        }

        CanvasNodeIssueSink issues = context.issues();
        CanvasNodeSupport.required(
                configuration.sourceTableName(),
                "请选择来源表",
                "configuration.sourceTableName",
                issues
        );
        CanvasNodeSupport.required(
                configuration.outputTableName(),
                "请输入输出表名",
                "configuration.outputTableName",
                issues
        );
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error(
                    "DUPLICATE_TABLE_NAME",
                    "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName"
            );
        }

        SparkCanvasTable source = CanvasNodeSupport.blank(configuration.sourceTableName())
                ? null
                : inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error(
                    "TABLE_NOT_FOUND",
                    "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName"
            );
        }
        FilterConditionMode mode = configuration.mode();
        if (mode == FilterConditionMode.STRUCTURED) {
            if (configuration.condition() == null) {
                issues.error("REQUIRED_CONFIGURATION", "请配置筛选条件", "configuration.condition");
            } else {
                CanvasPredicateExpressionBuilder.validate(
                        configuration.condition(),
                        source,
                        issues,
                        "configuration.condition"
                );
            }
        } else {
            validateSqlExpression(configuration.sqlExpression(), issues);
        }
        if (source == null
                || (mode == FilterConditionMode.STRUCTURED && configuration.condition() == null)
                || issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> filtered;
        if (mode == FilterConditionMode.SQL_EXPRESSION) {
            try {
                filtered = source.dataset().filter(functions.expr(configuration.sqlExpression().trim()));
                filtered.queryExecution().analyzed();
            } catch (Exception exception) {
                issues.error(
                        "INVALID_FILTER_SQL_EXPRESSION",
                        "SQL 表达式无法针对当前来源表解析为布尔筛选条件",
                        "configuration.sqlExpression"
                );
                return CanvasNodeOperationResult.invalid(inputSchemas);
            }
        } else {
            filtered = source.dataset().filter(
                    CanvasPredicateExpressionBuilder.expression(
                            configuration.condition(),
                            source.dataset()
                    )
            );
        }
        CanvasTableSchema sourceSchema = source.schema();
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(),
                sourceSchema.origin(),
                sourceSchema.columns(),
                sourceSchema.datasetKind(),
                sourceSchema.eventTimeColumn(),
                sourceSchema.watermarkDelay()
        );
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, filtered));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateSqlExpression(String expression, CanvasNodeIssueSink issues) {
        FilterSqlExpressionPolicy.Violation violation = FilterSqlExpressionPolicy.findViolation(expression);
        if (violation == null) return;
        if (violation == FilterSqlExpressionPolicy.Violation.REQUIRED) {
            issues.error(
                    "REQUIRED_CONFIGURATION",
                    "请输入 SQL 布尔表达式",
                    "configuration.sqlExpression"
            );
            return;
        }
        if (violation == FilterSqlExpressionPolicy.Violation.TOO_LONG) {
            issues.error(
                    "INVALID_FILTER_SQL_EXPRESSION",
                    "SQL 表达式不能超过 " + FilterSqlExpressionPolicy.MAX_EXPRESSION_LENGTH + " 个字符",
                    "configuration.sqlExpression"
            );
            return;
        }
        issues.error(
                "INVALID_FILTER_SQL_EXPRESSION",
                "只允许填写布尔谓词，不能包含 WHERE、完整 SQL、注释或分号",
                "configuration.sqlExpression"
        );
    }

}
