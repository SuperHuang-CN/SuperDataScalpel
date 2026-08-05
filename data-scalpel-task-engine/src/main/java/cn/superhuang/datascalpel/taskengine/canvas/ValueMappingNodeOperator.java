package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasLiteral;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasValueMappingLimits;
import cn.superhuang.data.scalpel.contract.task.ValueMappingConfiguration;
import cn.superhuang.data.scalpel.contract.task.ValueMappingEntry;
import cn.superhuang.data.scalpel.contract.task.ValueMappingNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ValueMappingRule;
import cn.superhuang.data.scalpel.contract.task.ValueMappingUnmatchedStrategy;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ValueMappingNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.VALUE_MAPPING;
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
        if (!(definition instanceof ValueMappingNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "VALUE_MAPPING operator received " + definition.nodeType()
            );
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        ValueMappingConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(inputSchemas);

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
                    "值映射规则必须是数组",
                    "configuration.rules"
            );
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        if (configuration.rules().isEmpty()) {
            issues.error(
                    "EMPTY_VALUE_MAPPING_RULES",
                    "至少配置一条值映射规则",
                    "configuration.rules"
            );
        }
        if (configuration.rules().size() > CanvasValueMappingLimits.MAX_RULES) {
            issues.error(
                    "VALUE_MAPPING_RULE_LIMIT_EXCEEDED",
                    "值映射字段规则不能超过 " + CanvasValueMappingLimits.MAX_RULES + " 项",
                    "configuration.rules"
            );
        }

        Map<String, CanvasColumnSchema> columns = source == null
                ? Map.of() : CanvasNodeSupport.columns(source.schema());
        Set<String> mappedColumns = new HashSet<>();
        int totalEntries = 0;
        for (int index = 0; index < configuration.rules().size(); index++) {
            ValueMappingRule rule = configuration.rules().get(index);
            String path = "configuration.rules[" + index + "]";
            if (rule == null) {
                issues.error("REQUIRED_CONFIGURATION", "值映射规则不能为空", path);
                continue;
            }
            totalEntries += validateRule(
                    rule, source, columns, mappedColumns, context.executionMode(), issues, path
            );
        }
        if (totalEntries > CanvasValueMappingLimits.MAX_TOTAL_ENTRIES) {
            issues.error(
                    "VALUE_MAPPING_ENTRY_LIMIT_EXCEEDED",
                    "单节点映射项总数不能超过 "
                            + CanvasValueMappingLimits.MAX_TOTAL_ENTRIES,
                    "configuration.rules"
            );
        }
        if (source == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> sourceDataset = source.dataset();
        Map<String, ValueMappingRule> rulesByColumn = new LinkedHashMap<>();
        configuration.rules().forEach(rule -> rulesByColumn.put(rule.columnName(), rule));
        List<Column> projection = new ArrayList<>(source.schema().columns().size());
        for (CanvasColumnSchema column : source.schema().columns()) {
            ValueMappingRule rule = rulesByColumn.get(column.name());
            projection.add(rule == null
                    ? sourceDataset.col(CanvasNodeSupport.quoteIdentifier(column.name()))
                    : mappingExpression(sourceDataset, column, rule).alias(column.name()));
        }
        Dataset<Row> mapped = sourceDataset.select(projection.toArray(Column[]::new));
        List<CanvasColumnSchema> analyzed = SparkTypeMapper.fromStructType(
                mapped.schema(),
                source.schema().columns()
        );
        List<CanvasColumnSchema> outputColumns = new ArrayList<>(analyzed.size());
        for (CanvasColumnSchema analyzedColumn : analyzed) {
            CanvasColumnSchema original = columns.get(analyzedColumn.name());
            outputColumns.add(mappedColumns.contains(analyzedColumn.name())
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
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, mapped));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static int validateRule(
            ValueMappingRule rule,
            SparkCanvasTable source,
            Map<String, CanvasColumnSchema> columns,
            Set<String> mappedColumns,
            CanvasExecutionMode executionMode,
            CanvasNodeIssueSink issues,
            String path
    ) {
        CanvasNodeSupport.required(
                rule.columnName(),
                "请选择映射字段",
                path + ".columnName",
                issues
        );
        CanvasColumnSchema column = CanvasNodeSupport.blank(rule.columnName())
                ? null : columns.get(rule.columnName());
        if (source != null && !CanvasNodeSupport.blank(rule.columnName()) && column == null) {
            issues.error(
                    "COLUMN_NOT_FOUND",
                    "映射字段不存在：" + rule.columnName(),
                    path + ".columnName"
            );
        }
        if (!CanvasNodeSupport.blank(rule.columnName()) && !mappedColumns.add(rule.columnName())) {
            issues.error(
                    "DUPLICATE_VALUE_MAPPING_COLUMN",
                    "同一字段只能配置一条映射规则：" + rule.columnName(),
                    path + ".columnName"
            );
        }
        if (column != null && column.fieldType() == PlatformDataType.GEOMETRY) {
            issues.error(
                    "VALUE_MAPPING_TYPE_NOT_SUPPORTED",
                    "GEOMETRY 不支持内联值映射",
                    path + ".columnName"
            );
        }
        if (executionMode == CanvasExecutionMode.STREAMING
                && source != null
                && rule.columnName() != null
                && rule.columnName().equals(source.schema().eventTimeColumn())) {
            issues.error(
                    "STREAM_EVENT_TIME_COLUMN_IMMUTABLE",
                    "流任务不能映射事件时间字段：" + rule.columnName(),
                    path + ".columnName"
            );
        }
        if (rule.entries() == null) {
            issues.error(
                    "REQUIRED_CONFIGURATION",
                    "映射项必须是数组",
                    path + ".entries"
            );
            return 0;
        }
        if (rule.entries().isEmpty()) {
            issues.error(
                    "EMPTY_VALUE_MAPPING_ENTRIES",
                    "每个字段至少配置一个映射项",
                    path + ".entries"
            );
        }
        if (rule.entries().size() > CanvasValueMappingLimits.MAX_ENTRIES_PER_RULE) {
            issues.error(
                    "VALUE_MAPPING_ENTRY_LIMIT_EXCEEDED",
                    "单字段映射项不能超过 "
                            + CanvasValueMappingLimits.MAX_ENTRIES_PER_RULE,
                    path + ".entries"
            );
        }
        Set<String> sourceKeys = new HashSet<>();
        for (int index = 0; index < rule.entries().size(); index++) {
            ValueMappingEntry entry = rule.entries().get(index);
            String entryPath = path + ".entries[" + index + "]";
            if (entry == null) {
                issues.error("REQUIRED_CONFIGURATION", "映射项不能为空", entryPath);
                continue;
            }
            validateLiteral(
                    entry.sourceValue(),
                    column,
                    false,
                    "VALUE_MAPPING_SOURCE_REQUIRED",
                    "源值不能为空",
                    entryPath + ".sourceValue",
                    issues
            );
            if (entry.sourceValue() != null
                    && CanvasPredicateExpressionBuilder.validLiteral(entry.sourceValue())
                    && column != null
                    && entry.sourceValue().dataType() == column.fieldType()) {
                String key = canonicalLiteralKey(entry.sourceValue());
                if (!sourceKeys.add(key)) {
                    issues.error(
                            "DUPLICATE_VALUE_MAPPING_SOURCE",
                            "同一字段存在语义重复的源值",
                            entryPath + ".sourceValue"
                    );
                }
            }
            if (entry.targetValue() != null) {
                validateLiteral(
                        entry.targetValue(),
                        column,
                        false,
                        "INVALID_VALUE_MAPPING_LITERAL",
                        "目标值格式无效",
                        entryPath + ".targetValue",
                        issues
                );
            }
        }
        validateUnmatched(rule, column, issues, path);
        return rule.entries().size();
    }

    private static void validateUnmatched(
            ValueMappingRule rule,
            CanvasColumnSchema column,
            CanvasNodeIssueSink issues,
            String path
    ) {
        if (rule.unmatchedStrategy() == null) {
            issues.error(
                    "INVALID_UNMATCHED_VALUE_STRATEGY",
                    "请选择未匹配值处理策略",
                    path + ".unmatchedStrategy"
            );
            return;
        }
        if (rule.unmatchedStrategy() == ValueMappingUnmatchedStrategy.SET_LITERAL) {
            if (rule.unmatchedValue() == null) {
                issues.error(
                        "UNMATCHED_VALUE_REQUIRED",
                        "SET_LITERAL 必须配置默认值",
                        path + ".unmatchedValue"
                );
            } else {
                validateLiteral(
                        rule.unmatchedValue(),
                        column,
                        false,
                        "INVALID_VALUE_MAPPING_LITERAL",
                        "默认值格式无效",
                        path + ".unmatchedValue",
                        issues
                );
            }
        } else if (rule.unmatchedValue() != null) {
            issues.error(
                    "UNMATCHED_VALUE_NOT_ALLOWED",
                    "只有 SET_LITERAL 可以配置默认值",
                    path + ".unmatchedValue"
            );
        }
    }

    private static void validateLiteral(
            CanvasLiteral literal,
            CanvasColumnSchema column,
            boolean allowNull,
            String missingCode,
            String missingMessage,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (literal == null || !allowNull && literal.value() == null) {
            issues.error(missingCode, missingMessage, path);
            return;
        }
        if (!CanvasPredicateExpressionBuilder.validLiteral(literal)) {
            issues.error("INVALID_VALUE_MAPPING_LITERAL", "Literal 格式无效", path);
            return;
        }
        if (column != null && literal.dataType() != column.fieldType()) {
            issues.error(
                    "VALUE_MAPPING_LITERAL_TYPE_MISMATCH",
                    "Literal 类型必须与映射字段一致",
                    path + ".dataType"
            );
        }
    }

    private static Column mappingExpression(
            Dataset<Row> dataset,
            CanvasColumnSchema column,
            ValueMappingRule rule
    ) {
        Column original = dataset.col(CanvasNodeSupport.quoteIdentifier(column.name()));
        Column result = functions.when(original.isNull(), original);
        for (ValueMappingEntry entry : rule.entries()) {
            result = result.when(
                    original.equalTo(literalColumn(entry.sourceValue(), column)),
                    entry.targetValue() == null
                            ? functions.lit(null).cast(SparkTypeMapper.toDataType(column))
                            : literalColumn(entry.targetValue(), column)
            );
        }
        Column unmatched = switch (rule.unmatchedStrategy()) {
            case KEEP -> original;
            case SET_NULL -> functions.lit(null).cast(SparkTypeMapper.toDataType(column));
            case SET_LITERAL -> literalColumn(rule.unmatchedValue(), column);
            case ERROR -> functions.raise_error(functions.lit(
                    "VALUE_MAPPING_UNMATCHED_VALUE column=" + column.name()
            ));
        };
        return result.otherwise(unmatched).cast(SparkTypeMapper.toDataType(column));
    }

    private static Column literalColumn(CanvasLiteral literal, CanvasColumnSchema column) {
        return functions.lit(CanvasPredicateExpressionBuilder.literalValue(literal))
                .cast(SparkTypeMapper.toDataType(column));
    }

    private static String canonicalLiteralKey(CanvasLiteral literal) {
        Object value = CanvasPredicateExpressionBuilder.literalValue(literal);
        String canonical = switch (value) {
            case BigDecimal decimal -> decimal.stripTrailingZeros().toPlainString();
            case Float number -> number == 0F
                    ? "0" : BigDecimal.valueOf(number.doubleValue()).stripTrailingZeros().toPlainString();
            case Double number -> number == 0D
                    ? "0" : BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
            case Byte number -> Byte.toString(number);
            case Short number -> Short.toString(number);
            case Integer number -> Integer.toString(number);
            case Long number -> Long.toString(number);
            case byte[] bytes -> Base64.getEncoder().encodeToString(bytes);
            case Timestamp timestamp -> timestamp.toInstant().toString();
            case Date date -> date.toLocalDate().toString();
            case LocalDateTime dateTime -> dateTime.toString();
            default -> value.toString();
        };
        return literal.dataType().name() + "\u0000" + canonical;
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
                original.comment()
        );
    }
}
