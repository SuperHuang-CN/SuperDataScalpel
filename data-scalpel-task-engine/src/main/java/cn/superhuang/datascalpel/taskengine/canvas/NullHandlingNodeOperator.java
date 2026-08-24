package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasNullHandlingLimits;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.DropNullRowsRule;
import cn.superhuang.data.scalpel.contract.task.FillNullLiteralRule;
import cn.superhuang.data.scalpel.contract.task.NullHandlingConfiguration;
import cn.superhuang.data.scalpel.contract.task.NullHandlingOperation;
import cn.superhuang.data.scalpel.contract.task.NullHandlingNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.NullHandlingRule;
import cn.superhuang.data.scalpel.contract.task.NullMatchMode;
import cn.superhuang.data.scalpel.contract.task.ProcessorOutput;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NullHandlingNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.NULL_HANDLING;
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
        if (!(definition instanceof NullHandlingNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "NULL_HANDLING operator received " + definition.nodeType()
            );
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        NullHandlingConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(inputSchemas);
        if (!ProcessorOperationSupport.isInternalSingle(configuration.operations())) {
            return ProcessorOperationSupport.apply(configuration.operations(), inputs, context, false,
                    (operation, scopedContext) -> {
                        NullHandlingOperation sourceOperation = (NullHandlingOperation) operation.operation();
                        NullHandlingConfiguration single = new NullHandlingConfiguration(List.of(new NullHandlingOperation(
                                ProcessorOperationSupport.INTERNAL_OPERATION_ID, operation.temporarySourceTableName(),
                                new ProcessorOutput.CreateNewTable(operation.outputTableName()), sourceOperation.rules()
                        )));
                        return apply(new NullHandlingNodeDefinition(node.id(), node.name(), node.layout(), single),
                                Map.of(operation.temporarySourceTableName(), operation.source()), scopedContext);
                    });
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
                ? null : inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error(
                    "TABLE_NOT_FOUND",
                    "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName"
            );
        }
        if (configuration.rules() == null) {
            issues.error(
                    "REQUIRED_CONFIGURATION",
                    "空值处理规则必须是数组",
                    "configuration.rules"
            );
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        if (configuration.rules().isEmpty()) {
            issues.error(
                    "EMPTY_NULL_HANDLING_RULES",
                    "至少配置一条空值处理规则",
                    "configuration.rules"
            );
        }
        if (configuration.rules().size() > CanvasNullHandlingLimits.MAX_RULES) {
            issues.error(
                    "NULL_HANDLING_RULE_LIMIT_EXCEEDED",
                    "空值处理规则不能超过 " + CanvasNullHandlingLimits.MAX_RULES + " 项",
                    "configuration.rules"
            );
        }

        Map<String, CanvasColumnSchema> columns = source == null
                ? Map.of() : CanvasNodeSupport.columns(source.schema());
        Set<String> fillColumns = new HashSet<>();
        for (int index = 0; index < configuration.rules().size(); index++) {
            NullHandlingRule rule = configuration.rules().get(index);
            String path = "configuration.rules[" + index + "]";
            if (rule == null) {
                issues.error("REQUIRED_CONFIGURATION", "空值处理规则不能为空", path);
                continue;
            }
            switch (rule) {
                case DropNullRowsRule drop ->
                        validateDropRule(drop, source, columns, issues, path);
                case FillNullLiteralRule fill ->
                        validateFillRule(
                                fill, source, columns, fillColumns, context.executionMode(),
                                issues, path
                        );
            }
        }
        if (source == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> result = source.dataset();
        for (NullHandlingRule rule : configuration.rules()) {
            if (rule instanceof DropNullRowsRule drop) {
                Column nullMatch = null;
                for (String columnName : drop.columnNames()) {
                    Column isNull = result.col(
                            CanvasNodeSupport.quoteIdentifier(columnName)).isNull();
                    nullMatch = nullMatch == null
                            ? isNull
                            : drop.matchMode() == NullMatchMode.ANY_NULL
                                    ? nullMatch.or(isNull) : nullMatch.and(isNull);
                }
                result = result.filter(functions.not(nullMatch));
            } else if (rule instanceof FillNullLiteralRule fill) {
                CanvasColumnSchema column = columns.get(fill.columnName());
                Column original = result.col(
                        CanvasNodeSupport.quoteIdentifier(fill.columnName()));
                Column replacement = functions.lit(
                                CanvasPredicateExpressionBuilder.literalValue(fill.value()))
                        .cast(SparkTypeMapper.toDataType(column));
                result = result.withColumn(
                        fill.columnName(),
                        functions.coalesce(original, replacement)
                );
            }
        }

        List<CanvasColumnSchema> analyzed = SparkTypeMapper.fromStructType(
                result.schema(),
                source.schema().columns()
        );
        List<CanvasColumnSchema> outputColumns = new ArrayList<>(analyzed.size());
        for (CanvasColumnSchema analyzedColumn : analyzed) {
            CanvasColumnSchema original = columns.get(analyzedColumn.name());
            outputColumns.add(fillColumns.contains(analyzedColumn.name())
                    ? modifiedColumn(original, analyzedColumn)
                    : original);
        }
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(),
                source.schema().origin(),
                outputColumns,
                source.schema().datasetKind(),
                source.schema().eventTimeColumn(),
                source.schema().watermarkDelay()
        );
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, result));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateDropRule(
            DropNullRowsRule rule,
            SparkCanvasTable source,
            Map<String, CanvasColumnSchema> columns,
            CanvasNodeIssueSink issues,
            String path
    ) {
        if (rule.columnNames() == null) {
            issues.error(
                    "REQUIRED_CONFIGURATION",
                    "空值检查字段必须是数组",
                    path + ".columnNames"
            );
            return;
        }
        if (rule.columnNames().isEmpty()) {
            issues.error(
                    "EMPTY_NULL_CHECK_COLUMNS",
                    "至少选择一个空值检查字段",
                    path + ".columnNames"
            );
        }
        Set<String> names = new HashSet<>();
        for (int index = 0; index < rule.columnNames().size(); index++) {
            String columnName = rule.columnNames().get(index);
            String columnPath = path + ".columnNames[" + index + "]";
            CanvasNodeSupport.required(columnName, "请选择空值检查字段", columnPath, issues);
            if (!CanvasNodeSupport.blank(columnName) && !names.add(columnName)) {
                issues.error(
                        "DUPLICATE_NULL_CHECK_COLUMN",
                        "空值检查字段重复：" + columnName,
                        columnPath
                );
            }
            if (source != null
                    && !CanvasNodeSupport.blank(columnName)
                    && !columns.containsKey(columnName)) {
                issues.error(
                        "COLUMN_NOT_FOUND",
                        "空值检查字段不存在：" + columnName,
                        columnPath
                );
            }
        }
        if (rule.matchMode() == null) {
            issues.error(
                    "INVALID_NULL_MATCH_MODE",
                    "请选择空值匹配方式",
                    path + ".matchMode"
            );
        }
    }

    private static void validateFillRule(
            FillNullLiteralRule rule,
            SparkCanvasTable source,
            Map<String, CanvasColumnSchema> columns,
            Set<String> fillColumns,
            CanvasExecutionMode executionMode,
            CanvasNodeIssueSink issues,
            String path
    ) {
        CanvasNodeSupport.required(
                rule.columnName(),
                "请选择填充字段",
                path + ".columnName",
                issues
        );
        CanvasColumnSchema column = CanvasNodeSupport.blank(rule.columnName())
                ? null : columns.get(rule.columnName());
        if (source != null && !CanvasNodeSupport.blank(rule.columnName()) && column == null) {
            issues.error(
                    "COLUMN_NOT_FOUND",
                    "填充字段不存在：" + rule.columnName(),
                    path + ".columnName"
            );
        }
        if (!CanvasNodeSupport.blank(rule.columnName()) && !fillColumns.add(rule.columnName())) {
            issues.error(
                    "DUPLICATE_NULL_FILL_COLUMN",
                    "同一字段只能配置一次固定值填充：" + rule.columnName(),
                    path + ".columnName"
            );
        }
        if (rule.value() == null || rule.value().value() == null) {
            issues.error(
                    "NULL_FILL_VALUE_REQUIRED",
                    "请输入非 NULL 填充值",
                    path + ".value"
            );
        } else if (!CanvasPredicateExpressionBuilder.validLiteral(rule.value())) {
            issues.error(
                    "INVALID_NULL_FILL_LITERAL",
                    "填充值格式无效",
                    path + ".value"
            );
        } else if (column != null && rule.value().dataType() != column.fieldType()) {
            issues.error(
                    "NULL_FILL_LITERAL_TYPE_MISMATCH",
                    "填充值类型必须与字段类型一致",
                    path + ".value.dataType"
            );
        }
        if (column != null && column.fieldType() == PlatformDataType.GEOMETRY) {
            issues.error(
                    "NULL_FILL_LITERAL_TYPE_NOT_SUPPORTED",
                    "GEOMETRY 暂不支持固定值填充",
                    path + ".value"
            );
        }
        if (executionMode == CanvasExecutionMode.STREAMING
                && source != null
                && rule.columnName() != null
                && rule.columnName().equals(source.schema().eventTimeColumn())) {
            issues.error(
                    "STREAM_EVENT_TIME_COLUMN_IMMUTABLE",
                    "流任务不能填充事件时间字段：" + rule.columnName(),
                    path + ".columnName"
            );
        }
    }

    private static CanvasColumnSchema modifiedColumn(
            CanvasColumnSchema original,
            CanvasColumnSchema analyzed
    ) {
        return new CanvasColumnSchema(
                original.name(),
                analyzed.fieldType(),
                original.length(),
                original.precision(),
                original.scale(),
                analyzed.nullable(),
                null,
                false,
                false,
                original.comment(),
                original.geometry()
        );
    }
}
