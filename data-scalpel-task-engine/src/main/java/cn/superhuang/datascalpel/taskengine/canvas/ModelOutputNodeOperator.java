package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.task.MetadataModelPhysicalTableMode;
import cn.superhuang.data.scalpel.contract.task.MetadataModelStatus;
import cn.superhuang.data.scalpel.contract.task.ModelOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ModelOutputNodeOperator implements CanvasNodeOperator {
    private final OutputColumnMappingOperator mappingOperator = new OutputColumnMappingOperator();

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.MODEL_OUTPUT;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.OUTPUT;
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
        if (!(definition instanceof ModelOutputNodeDefinition node)) {
            throw new IllegalArgumentException("MODEL_OUTPUT operator received " + definition.nodeType());
        }
        ModelOutputConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.outputOnly();
        }
        CanvasNodeIssueSink issues = context.issues();
        CanvasNodeSupport.required(
                configuration.sourceTableName(),
                "请选择来源表",
                "configuration.sourceTableName",
                issues
        );
        UUID modelId = configuration.targetModelId();
        if (modelId == null) {
            issues.error("MODEL_ID_REQUIRED", "请选择目标模型", "configuration.targetModelId");
        }
        if (configuration.writeMode() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择写入模式", "configuration.writeMode");
        }
        if (configuration.columnMappingMode() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择字段映射模式", "configuration.columnMappingMode");
        }
        if (configuration.columnMappings() == null) {
            issues.error("REQUIRED_CONFIGURATION", "字段映射列表不能为空", "configuration.columnMappings");
        }

        SparkCanvasTable source = inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error(
                    "TABLE_NOT_FOUND",
                    "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName"
            );
        }
        MetadataIndex.ModelEntry model =
                modelId == null ? null : context.metadataIndex().model(modelId);
        if (modelId != null && model == null) {
            issues.error("MODEL_NOT_FOUND", "目标模型不存在：" + modelId, "configuration.targetModelId");
        }
        if (model != null && model.metadata().status() != MetadataModelStatus.PUBLISHED) {
            issues.error(
                    "MODEL_NOT_PUBLISHED",
                    "目标模型不是已发布状态：" + model.metadata().name(),
                    "configuration.targetModelId"
            );
        }
        if (model != null && model.metadata().columns().stream()
                .anyMatch(column -> column.fieldType() == PlatformDataType.GEOMETRY)) {
            issues.error(
                    "SPATIAL_FIELD_UNSUPPORTED",
                    "模型输出第一版不支持空间字段：" + model.metadata().name(),
                    "configuration.targetModelId"
            );
        }
        MetadataIndex.DataSourceEntry dataSource = model == null
                ? null
                : context.metadataIndex().dataSource(model.metadata().dataSourceId());
        if (model != null && !availableForWrite(dataSource)) {
            issues.error(
                    "MODEL_DATA_SOURCE_UNAVAILABLE",
                    "目标模型数据源不存在、未启用或不具有 STORAGE 用途",
                    "configuration.targetModelId"
            );
        }
        if (model != null
                && configuration.writeMode() == JdbcWriteMode.OVERWRITE
                && model.metadata().physicalTableMode() != MetadataModelPhysicalTableMode.MANAGED) {
            issues.error(
                    "OVERWRITE_REQUIRES_MANAGED_MODEL",
                    "OVERWRITE 只允许写入 MANAGED 模型",
                    "configuration.writeMode"
            );
        }
        if (source == null || model == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.outputOnly();
        }

        CanvasTableSchema targetSchema = new CanvasTableSchema(
                model.metadata().code(),
                model.tableSchema().origin(),
                model.metadata().columns()
        );
        Dataset<Row> selected = mappingOperator.apply(
                source,
                targetSchema,
                configuration.columnMappingMode(),
                configuration.columnMappings(),
                issues
        );
        if (selected == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.outputOnly();
        }
        CanvasPreparedOutput prepared =
                context.dataAccess().prepareModelOutput(node, model, targetSchema, selected);
        return CanvasNodeOperationResult.output(prepared);
    }

    private static boolean availableForWrite(MetadataIndex.DataSourceEntry dataSource) {
        return dataSource != null
                && dataSource.metadata().enabled()
                && dataSource.metadata().connectionKind() == ConnectionKind.JDBC
                && dataSource.metadata().purposes().contains(DataSourcePurpose.STORAGE);
    }
}
