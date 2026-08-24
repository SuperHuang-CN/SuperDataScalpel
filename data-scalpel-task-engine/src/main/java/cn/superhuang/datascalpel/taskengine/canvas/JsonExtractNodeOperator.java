package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasJsonExtractLimits;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.JsonExtractConfiguration;
import cn.superhuang.data.scalpel.contract.task.JsonExtractOperation;
import cn.superhuang.data.scalpel.contract.task.JsonExtractFailureStrategy;
import cn.superhuang.data.scalpel.contract.task.JsonExtractNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JsonExtraction;
import cn.superhuang.data.scalpel.contract.task.ProcessorOutput;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
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

public final class JsonExtractNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.JSON_EXTRACT;
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
        if (!(definition instanceof JsonExtractNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "JSON_EXTRACT operator received " + definition.nodeType()
            );
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        JsonExtractConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        if (!ProcessorOperationSupport.isInternalSingle(configuration.operations())) {
            return ProcessorOperationSupport.apply(configuration.operations(), inputs, context, false,
                    (operation, scopedContext) -> {
                        JsonExtractOperation sourceOperation = (JsonExtractOperation) operation.operation();
                        JsonExtractConfiguration single = new JsonExtractConfiguration(List.of(new JsonExtractOperation(
                                ProcessorOperationSupport.INTERNAL_OPERATION_ID, operation.temporarySourceTableName(),
                                new ProcessorOutput.CreateNewTable(operation.outputTableName()),
                                sourceOperation.sourceColumnName(), sourceOperation.extractions(), sourceOperation.failureStrategy()
                        )));
                        return apply(new JsonExtractNodeDefinition(node.id(), node.name(), node.layout(), single),
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
        CanvasNodeSupport.required(
                configuration.sourceColumnName(),
                "请选择 JSON 来源字段",
                "configuration.sourceColumnName",
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
        Map<String, CanvasColumnSchema> sourceColumns = source == null
                ? Map.of() : CanvasNodeSupport.columns(source.schema());
        CanvasColumnSchema sourceColumn = CanvasNodeSupport.blank(configuration.sourceColumnName())
                ? null : sourceColumns.get(configuration.sourceColumnName());
        if (source != null
                && !CanvasNodeSupport.blank(configuration.sourceColumnName())
                && sourceColumn == null) {
            issues.error(
                    "COLUMN_NOT_FOUND",
                    "JSON 来源字段不存在：" + configuration.sourceColumnName(),
                    "configuration.sourceColumnName"
            );
        } else if (sourceColumn != null && sourceColumn.fieldType() != PlatformDataType.STRING) {
            issues.error(
                    "JSON_SOURCE_COLUMN_TYPE_MISMATCH",
                    "JSON 来源字段必须是 STRING：" + configuration.sourceColumnName(),
                    "configuration.sourceColumnName"
            );
        }

        if (configuration.extractions() == null) {
            issues.error(
                    "REQUIRED_CONFIGURATION",
                    "JSON 提取项必须是数组",
                    "configuration.extractions"
            );
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        if (configuration.extractions().isEmpty()) {
            issues.error(
                    "EMPTY_JSON_EXTRACTIONS",
                    "至少配置一个 JSON 提取项",
                    "configuration.extractions"
            );
        }
        if (configuration.extractions().size() > CanvasJsonExtractLimits.MAX_EXTRACTIONS) {
            issues.error(
                    "JSON_EXTRACTION_LIMIT_EXCEEDED",
                    "JSON 提取项不能超过 " + CanvasJsonExtractLimits.MAX_EXTRACTIONS + " 项",
                    "configuration.extractions"
            );
        }
        if (configuration.failureStrategy() == null) {
            issues.error(
                    "INVALID_JSON_FAILURE_STRATEGY",
                    "请选择 JSON 解析失败策略",
                    "configuration.failureStrategy"
            );
        }

        Set<String> outputColumnNames = new HashSet<>(sourceColumns.keySet());
        for (int index = 0; index < configuration.extractions().size(); index++) {
            validateExtraction(
                    configuration.extractions().get(index),
                    outputColumnNames,
                    issues,
                    "configuration.extractions[" + index + "]"
            );
        }
        if (source == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> sourceDataset = source.dataset();
        Column jsonText = sourceDataset.col(
                CanvasNodeSupport.quoteIdentifier(configuration.sourceColumnName())
        );
        Column parsedJson = configuration.failureStrategy() == JsonExtractFailureStrategy.ERROR
                ? functions.parse_json(jsonText)
                : functions.try_parse_json(jsonText);
        List<Column> projection = new ArrayList<>(
                source.schema().columns().size() + configuration.extractions().size()
        );
        for (CanvasColumnSchema column : source.schema().columns()) {
            projection.add(sourceDataset.col(CanvasNodeSupport.quoteIdentifier(column.name())));
        }
        for (JsonExtraction extraction : configuration.extractions()) {
            String targetSqlType = SparkTypeMapper.toDataType(extraction.targetType()).sql();
            Column extracted = configuration.failureStrategy() == JsonExtractFailureStrategy.ERROR
                    ? functions.variant_get(parsedJson, extraction.jsonPath(), targetSqlType)
                    : functions.try_variant_get(parsedJson, extraction.jsonPath(), targetSqlType);
            projection.add(extracted.alias(extraction.outputColumnName()));
        }
        Dataset<Row> extractedDataset = sourceDataset.select(projection.toArray(Column[]::new));
        extractedDataset.schema();

        List<CanvasColumnSchema> outputColumns = new ArrayList<>(
                source.schema().columns().size() + configuration.extractions().size()
        );
        outputColumns.addAll(source.schema().columns());
        configuration.extractions().stream()
                .map(JsonExtractNodeOperator::extractedColumn)
                .forEach(outputColumns::add);
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(),
                source.schema().origin(),
                outputColumns,
                source.schema().datasetKind(),
                source.schema().eventTimeColumn(),
                source.schema().watermarkDelay()
        );
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(
                outputSchema.name(),
                new SparkCanvasTable(outputSchema, extractedDataset)
        );
        return CanvasNodeOperationResult.propagated(
                output,
                CanvasNodeSupport.schemas(output)
        );
    }

    private static void validateExtraction(
            JsonExtraction extraction,
            Set<String> outputColumnNames,
            CanvasNodeIssueSink issues,
            String path
    ) {
        if (extraction == null) {
            issues.error("REQUIRED_CONFIGURATION", "JSON 提取项不能为空", path);
            return;
        }
        CanvasNodeSupport.required(
                extraction.jsonPath(),
                "请输入 JSON Path",
                path + ".jsonPath",
                issues
        );
        if (!CanvasNodeSupport.blank(extraction.jsonPath())) {
            if (extraction.jsonPath().length() > CanvasJsonExtractLimits.MAX_JSON_PATH_LENGTH) {
                issues.error(
                        "INVALID_JSON_PATH",
                        "JSON Path 不能超过 "
                                + CanvasJsonExtractLimits.MAX_JSON_PATH_LENGTH + " 个字符",
                        path + ".jsonPath"
                );
            } else if (!extraction.jsonPath().startsWith("$")) {
                issues.error(
                        "INVALID_JSON_PATH",
                        "JSON Path 必须以 $ 开头",
                        path + ".jsonPath"
                );
            }
        }
        CanvasNodeSupport.required(
                extraction.outputColumnName(),
                "请输入输出字段名",
                path + ".outputColumnName",
                issues
        );
        if (!CanvasNodeSupport.blank(extraction.outputColumnName())
                && !outputColumnNames.add(extraction.outputColumnName())) {
            issues.error(
                    "DUPLICATE_JSON_OUTPUT_COLUMN",
                    "输出字段名与来源字段或其他提取项重复："
                            + extraction.outputColumnName(),
                    path + ".outputColumnName"
            );
        }
        validateTargetType(extraction.targetType(), path + ".targetType", issues);
    }

    private static void validateTargetType(
            PlatformTypeDefinition targetType,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (targetType == null || targetType.type() == null) {
            issues.error(
                    "INVALID_TARGET_PLATFORM_TYPE",
                    "请选择目标平台类型",
                    path
            );
            return;
        }
        if (targetType.type() == PlatformDataType.GEOMETRY) {
            issues.error(
                    "INVALID_TARGET_PLATFORM_TYPE",
                    "JSON 提取暂不支持 GEOMETRY",
                    path + ".type"
            );
            return;
        }
        try {
            SparkTypeMapper.toDataType(targetType);
        } catch (RuntimeException exception) {
            issues.error(
                    "INVALID_TARGET_PLATFORM_TYPE",
                    "目标平台类型参数无效",
                    path
            );
        }
    }

    private static CanvasColumnSchema extractedColumn(JsonExtraction extraction) {
        PlatformTypeDefinition targetType = extraction.targetType();
        return new CanvasColumnSchema(
                extraction.outputColumnName(),
                targetType.type(),
                targetType.type() == PlatformDataType.STRING ? targetType.length() : null,
                targetType.type() == PlatformDataType.DECIMAL ? targetType.precision() : null,
                targetType.type() == PlatformDataType.DECIMAL ? targetType.scale() : null,
                true,
                null,
                false,
                false,
                null
        );
    }
}
