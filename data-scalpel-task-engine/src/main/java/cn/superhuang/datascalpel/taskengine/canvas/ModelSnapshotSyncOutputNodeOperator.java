package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasJdbcDatabaseType;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.MetadataModelPhysicalTableMode;
import cn.superhuang.data.scalpel.contract.task.MetadataModelStatus;
import cn.superhuang.data.scalpel.contract.task.ModelSnapshotSyncOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.ModelSnapshotSyncOutputNodeDefinition;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ModelSnapshotSyncOutputNodeOperator implements CanvasNodeOperator {
    private final SnapshotSyncOperatorSupport support = new SnapshotSyncOperatorSupport();

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.MODEL_SNAPSHOT_SYNC_OUTPUT;
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
        if (!(definition instanceof ModelSnapshotSyncOutputNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "MODEL_SNAPSHOT_SYNC_OUTPUT operator received " + definition.nodeType());
        }
        ModelSnapshotSyncOutputConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.outputOnly();
        CanvasNodeIssueSink issues = context.issues();
        UUID modelId = CanvasNodeSupport.parseModelUuid(
                configuration.targetModelId(), "configuration.targetModelId", issues);
        MetadataIndex.ModelEntry model = modelId == null ? null : context.metadataIndex().model(modelId);
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
        if (model != null
                && model.metadata().physicalTableMode() != MetadataModelPhysicalTableMode.MANAGED) {
            issues.error(
                    "MODEL_SNAPSHOT_SYNC_REQUIRES_MANAGED_MODEL",
                    "模型快照同步只允许写入 MANAGED 模型",
                    "configuration.targetModelId"
            );
        }
        MetadataIndex.DataSourceEntry dataSource = model == null
                ? null : context.metadataIndex().dataSource(model.metadata().dataSourceId());
        if (model != null && (dataSource == null
                || !dataSource.metadata().enabled()
                || dataSource.metadata().connectionKind() != ConnectionKind.JDBC
                || !dataSource.metadata().purposes().contains(DataSourcePurpose.STORAGE))) {
            issues.error(
                    "MODEL_DATA_SOURCE_UNAVAILABLE",
                    "目标模型数据源不存在、未启用或不具有 STORAGE 用途",
                    "configuration.targetModelId"
            );
        }
        if (dataSource != null
                && dataSource.metadata().jdbcDatabaseType() != CanvasJdbcDatabaseType.POSTGRESQL
                && dataSource.metadata().jdbcDatabaseType() != CanvasJdbcDatabaseType.MYSQL) {
            issues.error(
                    "MODEL_SNAPSHOT_SYNC_DATABASE_NOT_SUPPORTED",
                    "模型快照同步只支持 PostgreSQL 和 MySQL",
                    "configuration.targetModelId"
            );
        }
        if (model == null || issues.hasErrors()) return CanvasNodeOperationResult.outputOnly();

        CanvasTableSchema targetSchema = new CanvasTableSchema(
                model.metadata().code(), model.tableSchema().origin(), model.metadata().columns());
        Dataset<Row> selected = support.validateAndMap(
                configuration, inputs, targetSchema, model.metadata().uniqueKeys(), issues);
        if (selected == null || issues.hasErrors()) return CanvasNodeOperationResult.outputOnly();
        return CanvasNodeOperationResult.snapshotSyncOutput(
                context.dataAccess().prepareModelSnapshotSyncOutput(node, model, targetSchema, selected),
                CanvasLineageOutputCandidate.model(
                        node, selected, model.metadata(), targetSchema,
                        cn.superhuang.data.scalpel.contract.task.CanvasLineageCompilation.WriteMode.SNAPSHOT_SYNC
                )
        );
    }
}
