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
import cn.superhuang.data.scalpel.contract.task.KafkaInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class KafkaInputNodeOperator implements CanvasNodeOperator {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.KAFKA_INPUT;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.INPUT;
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
        if (!(definition instanceof KafkaInputNodeDefinition node)) {
            throw new IllegalArgumentException("KAFKA_INPUT operator received " + definition.nodeType());
        }
        KafkaInputConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(List.of());
        CanvasNodeIssueSink issues = context.issues();
        UUID dataSourceId = CanvasNodeSupport.parseUuid(
                configuration.dataSourceId(),
                "configuration.dataSourceId",
                issues
        );
        CanvasNodeSupport.required(configuration.topic(), "请输入 Kafka Topic", "configuration.topic", issues);
        List<cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema> columns =
                KafkaValueSchemaSupport.columns(configuration.valueSchema(), issues, "configuration.valueSchema");
        CanvasNodeSupport.required(
                configuration.outputTableName(), "请输入输出表名", "configuration.outputTableName", issues);
        if (configuration.startingOffsets() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择首次启动位置", "configuration.startingOffsets");
        }
        MetadataIndex.DataSourceEntry dataSource = dataSourceId == null
                ? null : context.metadataIndex().dataSource(dataSourceId);
        if (dataSourceId != null && (dataSource == null || !dataSource.metadata().enabled()
                || dataSource.metadata().connectionKind() != ConnectionKind.KAFKA
                || !dataSource.metadata().purposes().contains(DataSourcePurpose.SOURCE))) {
            issues.error("DATA_SOURCE_UNAVAILABLE",
                    "Kafka 数据源不存在、未启用或不具有 SOURCE 用途", "configuration.dataSourceId");
        }
        if (issues.hasErrors()) return CanvasNodeOperationResult.invalid(List.of());
        CanvasTableSchema schema = new CanvasTableSchema(
                configuration.outputTableName(),
                CanvasTableOrigin.kafka(dataSourceId, configuration.topic()),
                columns,
                CanvasDatasetKind.UNBOUNDED,
                null,
                null
        );
        Dataset<Row> dataset = context.dataAccess().readKafkaInput(node, schema);
        return CanvasNodeOperationResult.propagated(
                Map.of(schema.name(), new SparkCanvasTable(schema, dataset)),
                List.of(schema)
        );
    }
}
