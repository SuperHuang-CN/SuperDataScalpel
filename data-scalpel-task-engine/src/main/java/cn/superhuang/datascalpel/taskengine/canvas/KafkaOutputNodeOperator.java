package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableOrigin;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.KafkaOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaOutputValueFormat;
import cn.superhuang.data.scalpel.contract.task.KafkaOutputWrite;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class KafkaOutputNodeOperator implements CanvasNodeOperator {
    private static final String HIDDEN_KEY_COLUMN_PREFIX = "__datascalpel_kafka_key";
    private final OutputColumnMappingOperator mappingOperator = new OutputColumnMappingOperator();

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.KAFKA_OUTPUT;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.OUTPUT;
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
        if (!(definition instanceof KafkaOutputNodeDefinition node)) {
            throw new IllegalArgumentException("KAFKA_OUTPUT operator received " + definition.nodeType());
        }
        KafkaOutputConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.outputOnly();
        CanvasNodeIssueSink issues = context.issues();
        if (configuration.writes().isEmpty()) {
            issues.error("REQUIRED_CONFIGURATION", "至少配置一条写入", "configuration.writes");
            return CanvasNodeOperationResult.outputOnly();
        }
        if (!CanvasNodeSupport.validateOutputWriteIds(
                configuration.writes(), KafkaOutputWrite::writeId, issues)) {
            return CanvasNodeOperationResult.outputOnly();
        }
        if (configuration.writes().size() > 32) {
            issues.error("OUTPUT_WRITE_COUNT_EXCEEDED", "实时输出最多支持 32 条写入", "configuration.writes");
            return CanvasNodeOperationResult.outputOnly();
        }

        UUID dataSourceId = CanvasNodeSupport.parseUuid(
                configuration.dataSourceId(), "configuration.dataSourceId", issues);
        MetadataIndex.DataSourceEntry dataSource = dataSourceId == null
                ? null : context.metadataIndex().dataSource(dataSourceId);
        if (dataSourceId != null && (dataSource == null || !dataSource.metadata().enabled()
                || dataSource.metadata().connectionKind() != ConnectionKind.KAFKA
                || !dataSource.metadata().purposes().contains(DataSourcePurpose.DISTRIBUTION))) {
            issues.error(
                    "DATA_SOURCE_UNAVAILABLE",
                    "Kafka 数据源不存在、未启用或不具有 DISTRIBUTION 用途",
                    "configuration.dataSourceId"
            );
        }

        List<WritePlan> plans = new ArrayList<>();
        for (int index = 0; index < configuration.writes().size(); index++) {
            WritePlan plan = planWrite(
                    configuration.writes().get(index), inputs, dataSourceId, issues,
                    "configuration.writes[" + index + "]"
            );
            if (plan != null) plans.add(plan);
        }
        if (issues.hasErrors() || dataSourceId == null) {
            return CanvasNodeOperationResult.outputOnly();
        }

        List<CanvasPreparedKafkaOutput> prepared = new ArrayList<>();
        List<CanvasLineageOutputCandidate> lineage = new ArrayList<>();
        for (WritePlan plan : plans) {
            CanvasPreparedKafkaOutput output = context.dataAccess().prepareKafkaOutput(
                    node, plan.write(), plan.keyColumnAlias(), plan.dataset());
            if (output != null) prepared.add(output);
            lineage.add(CanvasLineageOutputCandidate.kafka(
                    node, plan.dataset(), dataSourceId, plan.write().topic(),
                    plan.targetSchema(), plan.write().writeId()
            ));
        }
        return CanvasNodeOperationResult.kafkaOutputs(prepared, lineage);
    }

    private WritePlan planWrite(
            KafkaOutputWrite write,
            Map<String, SparkCanvasTable> inputs,
            UUID dataSourceId,
            CanvasNodeIssueSink issues,
            String path
    ) {
        CanvasNodeSupport.required(write.sourceTableName(), "请选择来源流表",
                path + ".sourceTableName", issues);
        CanvasNodeSupport.required(write.topic(), "请输入 Kafka Topic", path + ".topic", issues);
        SparkCanvasTable source = inputs.get(write.sourceTableName());
        if (source == null) {
            issues.error("TABLE_NOT_FOUND", "来源流表不在上游数据中", path + ".sourceTableName");
            return null;
        }
        if (source.schema().datasetKind() != CanvasDatasetKind.UNBOUNDED) {
            issues.error("KAFKA_OUTPUT_REQUIRES_UNBOUNDED", "Kafka Output 只能消费无界流",
                    path + ".sourceTableName");
        }

        CanvasColumnSchema keyColumn = validateKey(write, source, issues, path);
        if (write.legacyMappingMode()) {
            return planLegacyWrite(write, source, keyColumn, dataSourceId, issues, path);
        }
        return planValueWrite(write, source, keyColumn, dataSourceId, issues, path);
    }

    private WritePlan planLegacyWrite(
            KafkaOutputWrite write,
            SparkCanvasTable source,
            CanvasColumnSchema keyColumn,
            UUID dataSourceId,
            CanvasNodeIssueSink issues,
            String path
    ) {
        if (!write.valueColumnNames().isEmpty()) {
            issues.error("KAFKA_OUTPUT_MODE_CONFLICT", "旧版 JSON 映射不能配置 Value 字段选择",
                    path + ".valueColumnNames");
        }
        List<CanvasColumnSchema> valueColumns = KafkaValueSchemaSupport.columns(
                write.valueSchema(), issues, path + ".valueSchema");
        if (write.columnMappings() == null) {
            issues.error("REQUIRED_CONFIGURATION", "字段映射配置不完整", path + ".columnMappings");
        }
        if (issues.hasErrors() || dataSourceId == null) return null;
        CanvasTableSchema target = targetSchema(write, dataSourceId, valueColumns);
        String keyAlias = keyColumn == null ? null : availableKeyAlias(valueColumns);
        Dataset<Row> mapped = keyColumn == null
                ? mappingOperator.apply(source, target, write.columnMappings(), issues)
                : mappingOperator.applyPreserving(
                        source, target, write.columnMappings(), keyColumn.name(), keyAlias, issues);
        return mapped == null ? null : new WritePlan(write, mapped, target, keyAlias);
    }

    private static WritePlan planValueWrite(
            KafkaOutputWrite write,
            SparkCanvasTable source,
            CanvasColumnSchema keyColumn,
            UUID dataSourceId,
            CanvasNodeIssueSink issues,
            String path
    ) {
        if (write.valueSchema() != null && !write.valueSchema().columns().isEmpty()) {
            issues.error("KAFKA_OUTPUT_MODE_CONFLICT", "新 Kafka Value 模式不能配置旧版 Value Schema",
                    path + ".valueSchema");
        }
        if (!write.columnMappings().isEmpty()) {
            issues.error("KAFKA_OUTPUT_MODE_CONFLICT", "新 Kafka Value 模式不能配置旧版字段映射",
                    path + ".columnMappings");
        }
        List<String> selectedNames = write.valueColumnNames();
        if (selectedNames.isEmpty()) {
            issues.error("REQUIRED_CONFIGURATION", "请至少选择一个 Value 字段",
                    path + ".valueColumnNames");
            return null;
        }
        Set<String> requested = new LinkedHashSet<>();
        Map<String, CanvasColumnSchema> sourceColumns = CanvasNodeSupport.columns(source.schema());
        for (int index = 0; index < selectedNames.size(); index++) {
            String name = selectedNames.get(index);
            if (CanvasNodeSupport.blank(name)) {
                issues.error("REQUIRED_CONFIGURATION", "Value 字段不能为空",
                        path + ".valueColumnNames[" + index + "]");
            } else if (!requested.add(name)) {
                issues.error("DUPLICATE_COLUMN_NAME", "Value 字段重复：" + name,
                        path + ".valueColumnNames[" + index + "]");
            } else if (!sourceColumns.containsKey(name)) {
                issues.error("COLUMN_NOT_FOUND", "Value 字段不存在：" + name,
                        path + ".valueColumnNames[" + index + "]");
            }
        }

        List<CanvasColumnSchema> selectedColumns = source.schema().columns().stream()
                .filter(column -> requested.contains(column.name()))
                .toList();
        KafkaOutputValueFormat format = write.valueFormat();
        if (format == KafkaOutputValueFormat.JSON) {
            selectedColumns.stream()
                    .filter(column -> column.fieldType() == PlatformDataType.GEOMETRY)
                    .forEach(column -> issues.error(
                            "KAFKA_JSON_GEOMETRY_REQUIRES_SERIALIZATION",
                            "Geometry 字段必须先序列化为 STRING 或 BINARY：" + column.name(),
                            path + ".valueColumnNames"
                    ));
        } else if (selectedNames.size() != 1) {
            issues.error("KAFKA_VALUE_COLUMN_COUNT_INVALID",
                    format + " 格式必须且只能选择一个 Value 字段",
                    path + ".valueColumnNames");
        } else if (!selectedColumns.isEmpty()) {
            PlatformDataType requiredType = format == KafkaOutputValueFormat.TEXT
                    ? PlatformDataType.STRING : PlatformDataType.BINARY;
            if (selectedColumns.getFirst().fieldType() != requiredType) {
                issues.error("KAFKA_VALUE_COLUMN_TYPE_INVALID",
                        format + " 格式要求 " + requiredType + " 字段",
                        path + ".valueColumnNames[0]");
            }
        }
        if (issues.hasErrors() || dataSourceId == null) return null;

        List<CanvasColumnSchema> targetColumns = format == KafkaOutputValueFormat.JSON
                ? selectedColumns.stream().map(column -> rename(column, column.name())).toList()
                : List.of(rename(selectedColumns.getFirst(), "value"));
        CanvasTableSchema target = targetSchema(write, dataSourceId, targetColumns);
        String keyAlias = keyColumn == null ? null : availableKeyAlias(targetColumns);
        List<Column> projections = new ArrayList<>();
        if (format == KafkaOutputValueFormat.JSON) {
            selectedColumns.forEach(column -> projections.add(source.dataset()
                    .col(CanvasNodeSupport.quoteIdentifier(column.name())).alias(column.name())));
        } else {
            projections.add(source.dataset()
                    .col(CanvasNodeSupport.quoteIdentifier(selectedColumns.getFirst().name()))
                    .alias("value"));
        }
        if (keyColumn != null) {
            projections.add(source.dataset()
                    .col(CanvasNodeSupport.quoteIdentifier(keyColumn.name())).alias(keyAlias));
        }
        Dataset<Row> projected = source.dataset().select(projections.toArray(Column[]::new));
        return new WritePlan(write, projected, target, keyAlias);
    }

    private static CanvasColumnSchema validateKey(
            KafkaOutputWrite write,
            SparkCanvasTable source,
            CanvasNodeIssueSink issues,
            String path
    ) {
        String keyColumnName = write.keyColumnName();
        if (CanvasNodeSupport.blank(keyColumnName)) return null;
        CanvasColumnSchema keyColumn = CanvasNodeSupport.columns(source.schema()).get(keyColumnName);
        if (keyColumn == null) {
            issues.error("COLUMN_NOT_FOUND", "Kafka Key 字段不存在：" + keyColumnName,
                    path + ".keyColumnName");
            return null;
        }
        if (!write.legacyMappingMode()
                && keyColumn.fieldType() != PlatformDataType.STRING
                && keyColumn.fieldType() != PlatformDataType.BINARY) {
            issues.error("KAFKA_KEY_COLUMN_TYPE_INVALID",
                    "Kafka Key 只能使用 STRING 或 BINARY 字段，请先通过 Processor 转换",
                    path + ".keyColumnName");
            return null;
        }
        return keyColumn;
    }

    private static CanvasTableSchema targetSchema(
            KafkaOutputWrite write,
            UUID dataSourceId,
            List<CanvasColumnSchema> columns
    ) {
        return new CanvasTableSchema(
                write.topic(), CanvasTableOrigin.kafka(dataSourceId, write.topic()), columns
        );
    }

    private static CanvasColumnSchema rename(CanvasColumnSchema source, String name) {
        return new CanvasColumnSchema(
                name, source.fieldType(), source.length(), source.precision(), source.scale(),
                source.nullable(), null, false, false, source.comment(), source.geometry()
        );
    }

    private static String availableKeyAlias(List<CanvasColumnSchema> valueColumns) {
        Set<String> names = new HashSet<>();
        valueColumns.forEach(column -> names.add(column.name()));
        String alias = HIDDEN_KEY_COLUMN_PREFIX;
        while (names.contains(alias)) alias += "_";
        return alias;
    }

    private record WritePlan(
            KafkaOutputWrite write,
            Dataset<Row> dataset,
            CanvasTableSchema targetSchema,
            String keyColumnAlias
    ) {
    }
}
