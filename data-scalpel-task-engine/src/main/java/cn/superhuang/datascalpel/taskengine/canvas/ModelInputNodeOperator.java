package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.MetadataModelStatus;
import cn.superhuang.data.scalpel.contract.task.ModelInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.ModelInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ModelInputNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.MODEL_INPUT;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.INPUT;
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
        if (!(definition instanceof ModelInputNodeDefinition node)) {
            throw new IllegalArgumentException("MODEL_INPUT operator received " + definition.nodeType());
        }
        ModelInputConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(List.of());
        }
        CanvasNodeIssueSink issues = context.issues();
        UUID modelId = configuration.modelId();
        if (modelId == null) {
            issues.error("MODEL_ID_REQUIRED", "请选择输入模型", "configuration.modelId");
            return CanvasNodeOperationResult.invalid(List.of());
        }
        MetadataIndex.ModelEntry model = context.metadataIndex().model(modelId);
        if (model == null) {
            issues.error("MODEL_NOT_FOUND", "输入模型不存在：" + modelId, "configuration.modelId");
            return CanvasNodeOperationResult.invalid(List.of());
        }
        if (model.metadata().status() != MetadataModelStatus.PUBLISHED) {
            issues.error(
                    "MODEL_NOT_PUBLISHED",
                    "输入模型不是已发布状态：" + model.metadata().name(),
                    "configuration.modelId"
            );
        }
        if (model.metadata().columns().stream()
                .anyMatch(column -> column.fieldType() == PlatformDataType.GEOMETRY)) {
            issues.error(
                    "SPATIAL_FIELD_UNSUPPORTED",
                    "模型输入第一版不支持空间字段：" + model.metadata().name(),
                    "configuration.modelId"
            );
        }
        MetadataIndex.DataSourceEntry dataSource =
                context.metadataIndex().dataSource(model.metadata().dataSourceId());
        if (!availableForRead(dataSource)) {
            issues.error(
                    "MODEL_DATA_SOURCE_UNAVAILABLE",
                    "模型数据源不存在、未启用或不具有 SOURCE/STORAGE 用途",
                    "configuration.modelId"
            );
        }
        if (issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(List.of());
        }

        Dataset<Row> dataset = context.dataAccess().readModelInput(node, model, model.tableSchema());
        Map<String, SparkCanvasTable> output = Map.of(
                model.metadata().code(),
                new SparkCanvasTable(model.tableSchema(), dataset)
        );
        return CanvasNodeOperationResult.propagated(output, List.of(model.tableSchema()));
    }

    private static boolean availableForRead(MetadataIndex.DataSourceEntry dataSource) {
        return dataSource != null
                && dataSource.metadata().enabled()
                && dataSource.metadata().connectionKind() == ConnectionKind.JDBC
                && (dataSource.metadata().purposes().contains(DataSourcePurpose.SOURCE)
                || dataSource.metadata().purposes().contains(DataSourcePurpose.STORAGE));
    }
}
