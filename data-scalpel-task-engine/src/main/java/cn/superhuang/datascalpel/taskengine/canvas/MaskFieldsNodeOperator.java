package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasMaskingLimits;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.MaskFieldRule;
import cn.superhuang.data.scalpel.contract.task.MaskFieldsConfiguration;
import cn.superhuang.data.scalpel.contract.task.MaskFieldsNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.MaskingRuleDefinition;
import cn.superhuang.data.scalpel.contract.task.MaskingRuleSource;
import cn.superhuang.data.scalpel.contract.task.MaskingSourceRuleReference;
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
import java.util.UUID;

public final class MaskFieldsNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.MASK_FIELDS;
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
        if (!(definition instanceof MaskFieldsNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "MASK_FIELDS operator received " + definition.nodeType()
            );
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        MaskFieldsConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
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
        if (configuration.fieldRules() == null) {
            issues.error(
                    "REQUIRED_CONFIGURATION",
                    "字段脱敏规则必须是数组",
                    "configuration.fieldRules"
            );
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        if (configuration.fieldRules().isEmpty()) {
            issues.error(
                    "EMPTY_MASKING_RULES",
                    "至少配置一条字段脱敏规则",
                    "configuration.fieldRules"
            );
        }
        if (configuration.fieldRules().size() > CanvasMaskingLimits.MAX_FIELD_RULES) {
            issues.error(
                    "MASKING_RULE_LIMIT_EXCEEDED",
                    "字段脱敏规则不能超过 " + CanvasMaskingLimits.MAX_FIELD_RULES + " 项",
                    "configuration.fieldRules"
            );
        }

        Map<String, CanvasColumnSchema> columns = source == null
                ? Map.of() : CanvasNodeSupport.columns(source.schema());
        Set<String> maskedFields = new HashSet<>();
        for (int index = 0; index < configuration.fieldRules().size(); index++) {
            MaskFieldRule rule = configuration.fieldRules().get(index);
            validateRule(
                    rule,
                    source,
                    columns,
                    maskedFields,
                    context.executionMode(),
                    issues,
                    "configuration.fieldRules[" + index + "]"
            );
        }
        if (source == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> sourceDataset = source.dataset();
        Map<String, MaskFieldRule> rulesByField = new LinkedHashMap<>();
        configuration.fieldRules().forEach(rule -> rulesByField.put(rule.fieldName(), rule));
        List<Column> projection = new ArrayList<>(source.schema().columns().size());
        for (CanvasColumnSchema column : source.schema().columns()) {
            MaskFieldRule rule = rulesByField.get(column.name());
            projection.add(rule == null
                    ? sourceDataset.col(CanvasNodeSupport.quoteIdentifier(column.name()))
                    : maskingExpression(sourceDataset, column, rule.definition()).alias(column.name()));
        }
        Dataset<Row> masked = sourceDataset.select(projection.toArray(Column[]::new));
        masked.schema();

        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(),
                source.schema().origin(),
                source.schema().columns().stream()
                        .map(column -> maskedFields.contains(column.name())
                                ? maskedColumn(column)
                                : column)
                        .toList(),
                source.schema().datasetKind(),
                source.schema().eventTimeColumn(),
                source.schema().watermarkDelay()
        );
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, masked));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateRule(
            MaskFieldRule rule,
            SparkCanvasTable source,
            Map<String, CanvasColumnSchema> columns,
            Set<String> maskedFields,
            CanvasExecutionMode executionMode,
            CanvasNodeIssueSink issues,
            String path
    ) {
        if (rule == null) {
            issues.error("REQUIRED_CONFIGURATION", "字段脱敏规则不能为空", path);
            return;
        }
        CanvasNodeSupport.required(
                rule.fieldName(),
                "请选择脱敏字段",
                path + ".fieldName",
                issues
        );
        CanvasColumnSchema column = CanvasNodeSupport.blank(rule.fieldName())
                ? null : columns.get(rule.fieldName());
        if (source != null && !CanvasNodeSupport.blank(rule.fieldName()) && column == null) {
            issues.error(
                    "COLUMN_NOT_FOUND",
                    "脱敏字段不存在：" + rule.fieldName(),
                    path + ".fieldName"
            );
        }
        if (!CanvasNodeSupport.blank(rule.fieldName()) && !maskedFields.add(rule.fieldName())) {
            issues.error(
                    "DUPLICATE_MASKING_FIELD",
                    "同一字段只能配置一条脱敏规则：" + rule.fieldName(),
                    path + ".fieldName"
            );
        }
        if (executionMode == CanvasExecutionMode.STREAMING
                && source != null
                && rule.fieldName() != null
                && rule.fieldName().equals(source.schema().eventTimeColumn())) {
            issues.error(
                    "STREAM_EVENT_TIME_COLUMN_IMMUTABLE",
                    "实时任务不能脱敏事件时间字段：" + rule.fieldName(),
                    path + ".fieldName"
            );
        }
        validateSource(rule, issues, path);
        validateDefinition(rule.definition(), column, issues, path + ".definition");
    }

    private static void validateSource(
            MaskFieldRule rule,
            CanvasNodeIssueSink issues,
            String path
    ) {
        if (rule.ruleSource() == null) {
            issues.error(
                    "MASKING_RULE_SOURCE_REQUIRED",
                    "请选择规则来源",
                    path + ".ruleSource"
            );
            return;
        }
        if (rule.ruleSource() == MaskingRuleSource.INLINE) {
            if (rule.sourceRuleRef() != null) {
                issues.error(
                        "MASKING_SOURCE_REFERENCE_NOT_ALLOWED",
                        "自定义规则不能保留全局规则来源",
                        path + ".sourceRuleRef"
                );
            }
            return;
        }
        MaskingSourceRuleReference reference = rule.sourceRuleRef();
        if (reference == null) {
            issues.error(
                    "MASKING_SOURCE_REFERENCE_REQUIRED",
                    "全局规则缺少来源信息",
                    path + ".sourceRuleRef"
            );
            return;
        }
        if (CanvasNodeSupport.blank(reference.ruleId())) {
            issues.error(
                    "MASKING_SOURCE_RULE_ID_REQUIRED",
                    "全局规则 ID 不能为空",
                    path + ".sourceRuleRef.ruleId"
            );
        } else {
            try {
                UUID.fromString(reference.ruleId());
            } catch (IllegalArgumentException exception) {
                issues.error(
                        "INVALID_MASKING_SOURCE_RULE_ID",
                        "全局规则 ID 必须是 UUID",
                        path + ".sourceRuleRef.ruleId"
                );
            }
        }
        CanvasNodeSupport.required(
                reference.ruleCode(),
                "全局规则编码不能为空",
                path + ".sourceRuleRef.ruleCode",
                issues
        );
        CanvasNodeSupport.required(
                reference.ruleName(),
                "全局规则名称不能为空",
                path + ".sourceRuleRef.ruleName",
                issues
        );
    }

    private static void validateDefinition(
            MaskingRuleDefinition sourceDefinition,
            CanvasColumnSchema column,
            CanvasNodeIssueSink issues,
            String path
    ) {
        if (sourceDefinition == null || sourceDefinition.strategy() == null) {
            issues.error("MASKING_STRATEGY_REQUIRED", "请选择脱敏策略", path + ".strategy");
            return;
        }
        MaskingRuleDefinition definition = sourceDefinition.canonical();
        switch (definition.strategy()) {
            case PARTIAL_MASK -> {
                requireStringColumn(column, issues, path);
                validateKeepLength(
                        definition.keepPrefixLength(),
                        "保留前缀字符数",
                        path + ".keepPrefixLength",
                        issues
                );
                validateKeepLength(
                        definition.keepSuffixLength(),
                        "保留后缀字符数",
                        path + ".keepSuffixLength",
                        issues
                );
                validateMaskCharacter(definition.maskCharacter(), path + ".maskCharacter", issues);
                if (sourceDefinition.fixedValue() != null) {
                    issues.error(
                            "MASKING_PARAMETER_NOT_ALLOWED",
                            "PARTIAL_MASK 不能配置固定替换值",
                            path + ".fixedValue"
                    );
                }
            }
            case KEEP_LENGTH_MASK -> {
                requireStringColumn(column, issues, path);
                validateMaskCharacter(definition.maskCharacter(), path + ".maskCharacter", issues);
                rejectUnusedLengthsAndFixedValue(sourceDefinition, issues, path);
            }
            case FIXED_VALUE -> {
                requireStringColumn(column, issues, path);
                if (definition.fixedValue() == null) {
                    issues.error(
                            "MASKING_FIXED_VALUE_REQUIRED",
                            "请输入固定替换值",
                            path + ".fixedValue"
                    );
                } else if (definition.fixedValue().length()
                        > CanvasMaskingLimits.MAX_FIXED_VALUE_LENGTH) {
                    issues.error(
                            "MASKING_FIXED_VALUE_TOO_LONG",
                            "固定替换值不能超过 "
                                    + CanvasMaskingLimits.MAX_FIXED_VALUE_LENGTH + " 个字符",
                            path + ".fixedValue"
                    );
                }
                if (sourceDefinition.keepPrefixLength() != null
                        || sourceDefinition.keepSuffixLength() != null
                        || sourceDefinition.maskCharacter() != null) {
                    issues.error(
                            "MASKING_PARAMETER_NOT_ALLOWED",
                            "FIXED_VALUE 包含不适用参数",
                            path
                    );
                }
            }
            case NULLIFY -> {
                if (column != null && !column.nullable()) {
                    issues.error(
                            "MASKING_NULLIFY_REQUIRES_NULLABLE_FIELD",
                            "NULLIFY 只能用于可空字段",
                            path + ".strategy"
                    );
                }
                if (sourceDefinition.keepPrefixLength() != null
                        || sourceDefinition.keepSuffixLength() != null
                        || sourceDefinition.maskCharacter() != null
                        || sourceDefinition.fixedValue() != null) {
                    issues.error(
                            "MASKING_PARAMETER_NOT_ALLOWED",
                            "NULLIFY 不能配置其他参数",
                            path
                    );
                }
            }
        }
    }

    private static void requireStringColumn(
            CanvasColumnSchema column,
            CanvasNodeIssueSink issues,
            String path
    ) {
        if (column != null && column.fieldType() != PlatformDataType.STRING) {
            issues.error(
                    "MASKING_STRING_FIELD_REQUIRED",
                    "当前脱敏策略仅支持 STRING 字段",
                    path + ".strategy"
            );
        }
    }

    private static void validateKeepLength(
            Integer value,
            String label,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (value == null || value < 0 || value > CanvasMaskingLimits.MAX_KEEP_LENGTH) {
            issues.error(
                    "INVALID_MASKING_KEEP_LENGTH",
                    label + "必须在 0.." + CanvasMaskingLimits.MAX_KEEP_LENGTH + " 之间",
                    path
            );
        }
    }

    private static void validateMaskCharacter(
            String value,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (value == null || value.codePointCount(0, value.length()) != 1) {
            issues.error(
                    "INVALID_MASKING_CHARACTER",
                    "掩码字符必须是一个 Unicode 字符",
                    path
            );
        }
    }

    private static void rejectUnusedLengthsAndFixedValue(
            MaskingRuleDefinition definition,
            CanvasNodeIssueSink issues,
            String path
    ) {
        if (definition.keepPrefixLength() != null
                || definition.keepSuffixLength() != null
                || definition.fixedValue() != null) {
            issues.error(
                    "MASKING_PARAMETER_NOT_ALLOWED",
                    "KEEP_LENGTH_MASK 包含不适用参数",
                    path
            );
        }
    }

    private static Column maskingExpression(
            Dataset<Row> dataset,
            CanvasColumnSchema column,
            MaskingRuleDefinition sourceDefinition
    ) {
        MaskingRuleDefinition definition = sourceDefinition.canonical();
        Column original = dataset.col(CanvasNodeSupport.quoteIdentifier(column.name()));
        Column masked = switch (definition.strategy()) {
            case PARTIAL_MASK -> partialMask(original, definition);
            case KEEP_LENGTH_MASK -> functions.repeat(
                    functions.lit(definition.maskCharacter()),
                    functions.length(original)
            );
            case FIXED_VALUE -> functions.lit(definition.fixedValue());
            case NULLIFY -> functions.lit(null).cast(SparkTypeMapper.toDataType(column));
        };
        Column result = column.nullable() && definition.strategy()
                != cn.superhuang.data.scalpel.contract.task.MaskingStrategy.NULLIFY
                ? functions.when(original.isNull(), original).otherwise(masked)
                : masked;
        return result.cast(SparkTypeMapper.toDataType(column));
    }

    private static Column partialMask(
            Column original,
            MaskingRuleDefinition definition
    ) {
        int prefixLength = definition.keepPrefixLength();
        int suffixLength = definition.keepSuffixLength();
        int retainedLength = prefixLength + suffixLength;
        Column length = functions.length(original);
        Column allMasked = functions.repeat(functions.lit(definition.maskCharacter()), length);
        Column prefix = functions.substring(
                original,
                functions.lit(1),
                functions.lit(prefixLength)
        );
        Column middle = functions.repeat(
                functions.lit(definition.maskCharacter()),
                length.minus(retainedLength)
        );
        Column suffix = functions.substring(
                original,
                length.minus(suffixLength).plus(1),
                functions.lit(suffixLength)
        );
        return functions.when(length.leq(retainedLength), allMasked)
                .otherwise(functions.concat(prefix, middle, suffix));
    }

    private static CanvasColumnSchema maskedColumn(CanvasColumnSchema original) {
        return new CanvasColumnSchema(
                original.name(),
                original.fieldType(),
                original.length(),
                original.precision(),
                original.scale(),
                original.nullable(),
                null,
                false,
                false,
                original.comment(),
                original.geometry()
        );
    }
}
