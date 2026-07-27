package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableOrigin;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.KafkaOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.Map;
import java.util.Set;

public final class KafkaOutputNodeOperator implements CanvasNodeOperator {
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
        CanvasNodeSupport.required(configuration.sourceTableName(), "请选择来源流表",
                "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(configuration.topic(), "请输入 Kafka Topic", "configuration.topic", issues);
        if (configuration.dataSourceId() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择 Kafka 数据源", "configuration.dataSourceId");
        }
        var valueColumns = KafkaValueSchemaSupport.columns(
                configuration.valueSchema(), issues, "configuration.valueSchema");
        if (configuration.columnMappingMode() == null || configuration.columnMappings() == null) {
            issues.error("REQUIRED_CONFIGURATION", "字段映射配置不完整", "configuration.columnMappings");
        }
        SparkCanvasTable source = inputs.get(configuration.sourceTableName());
        if (source == null) {
            issues.error("TABLE_NOT_FOUND", "来源流表不在上游数据中", "configuration.sourceTableName");
        } else if (source.schema().datasetKind() != CanvasDatasetKind.UNBOUNDED) {
            issues.error("KAFKA_OUTPUT_REQUIRES_UNBOUNDED", "Kafka Output 只能消费无界流",
                    "configuration.sourceTableName");
        }
        MetadataIndex.DataSourceEntry dataSource = configuration.dataSourceId() == null
                ? null : context.metadataIndex().dataSource(configuration.dataSourceId());
        if (dataSource == null || !dataSource.metadata().enabled()
                || dataSource.metadata().connectionKind() != ConnectionKind.KAFKA
                || !dataSource.metadata().purposes().contains(DataSourcePurpose.DISTRIBUTION)) {
            issues.error("DATA_SOURCE_UNAVAILABLE",
                    "Kafka 数据源不存在、未启用或不具有 DISTRIBUTION 用途", "configuration.dataSourceId");
        }
        if (source == null || issues.hasErrors()) return CanvasNodeOperationResult.outputOnly();
        CanvasTableSchema target = new CanvasTableSchema(
                configuration.topic(),
                CanvasTableOrigin.kafka(configuration.dataSourceId(), configuration.topic()),
                valueColumns
        );
        String keyColumn = configuration.keyColumnName();
        if (keyColumn != null && !keyColumn.isBlank()) {
            if (CanvasNodeSupport.columns(source.schema()).get(keyColumn) == null) {
                issues.error("COLUMN_NOT_FOUND", "Kafka Key 字段不存在：" + keyColumn,
                        "configuration.keyColumnName");
                return CanvasNodeOperationResult.outputOnly();
            }
        }
        Dataset<Row> mapped = keyColumn == null || keyColumn.isBlank()
                ? mappingOperator.apply(
                        source,
                        target,
                        configuration.columnMappingMode(),
                        configuration.columnMappings(),
                        issues
                )
                : mappingOperator.applyPreserving(
                        source,
                        target,
                        configuration.columnMappingMode(),
                        configuration.columnMappings(),
                        keyColumn,
                    "__datascalpel_kafka_key",
                        issues
                );
        if (mapped == null || issues.hasErrors()) return CanvasNodeOperationResult.outputOnly();
        CanvasPreparedKafkaOutput prepared = context.dataAccess().prepareKafkaOutput(node, mapped);
        return CanvasNodeOperationResult.kafkaOutput(prepared);
    }
}
