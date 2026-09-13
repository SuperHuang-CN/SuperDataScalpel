package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasJdbcDatabaseType;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.DatabaseObjectType;
import cn.superhuang.data.scalpel.contract.task.JdbcSnapshotSyncOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcSnapshotSyncOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.MetadataTable;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class JdbcSnapshotSyncOutputNodeOperator implements CanvasNodeOperator {
    private final SnapshotSyncOperatorSupport support = new SnapshotSyncOperatorSupport();

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.JDBC_SNAPSHOT_SYNC_OUTPUT;
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
        if (!(definition instanceof JdbcSnapshotSyncOutputNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "JDBC_SNAPSHOT_SYNC_OUTPUT operator received " + definition.nodeType());
        }
        JdbcSnapshotSyncOutputConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.outputOnly();
        CanvasNodeIssueSink issues = context.issues();
        UUID dataSourceId = CanvasNodeSupport.parseUuid(
                configuration.dataSourceId(), "configuration.dataSourceId", issues);
        CanvasNodeSupport.required(
                configuration.targetTableName(), "请选择目标表", "configuration.targetTableName", issues);

        MetadataIndex.DataSourceEntry dataSource = dataSourceId == null
                ? null : context.metadataIndex().dataSource(dataSourceId);
        if (dataSourceId != null && (dataSource == null
                || !dataSource.metadata().enabled()
                || dataSource.metadata().connectionKind() != ConnectionKind.JDBC
                || !dataSource.metadata().purposes().contains(DataSourcePurpose.DISTRIBUTION))) {
            issues.error(
                    "DATA_SOURCE_UNAVAILABLE",
                    "目标数据源不存在、未启用或不具有 DISTRIBUTION 用途",
                    "configuration.dataSourceId"
            );
        }
        if (dataSource != null
                && dataSource.metadata().jdbcDatabaseType() != CanvasJdbcDatabaseType.POSTGRESQL
                && dataSource.metadata().jdbcDatabaseType() != CanvasJdbcDatabaseType.HIGHGO
                && dataSource.metadata().jdbcDatabaseType() != CanvasJdbcDatabaseType.MYSQL
                && dataSource.metadata().jdbcDatabaseType() != CanvasJdbcDatabaseType.OPENGAUSS
                && dataSource.metadata().jdbcDatabaseType() != CanvasJdbcDatabaseType.KINGBASE) {
            issues.error(
                    "SNAPSHOT_SYNC_DATABASE_NOT_SUPPORTED",
                    "快照同步只支持 PostgreSQL、HighGo、MySQL、openGauss 和人大金仓",
                    "configuration.dataSourceId"
            );
        }
        MetadataTable target = dataSource == null
                ? null : dataSource.table(configuration.targetTableName());
        if (!CanvasNodeSupport.blank(configuration.targetTableName()) && target == null) {
            issues.error(
                    "TABLE_NOT_FOUND",
                    "目标表不存在：" + configuration.targetTableName(),
                    "configuration.targetTableName"
            );
        } else if (target != null && target.objectType() != DatabaseObjectType.TABLE) {
            issues.error(
                    "TARGET_TABLE_NOT_WRITABLE",
                    "快照同步目标必须是普通物理表",
                    "configuration.targetTableName"
            );
        }
        if (target == null || issues.hasErrors()) return CanvasNodeOperationResult.outputOnly();

        CanvasTableSchema targetSchema = new CanvasTableSchema(target.tableName(), null, target.columns());
        Dataset<Row> selected = support.validateAndMap(
                configuration, inputs, targetSchema, target.uniqueKeys(), issues);
        if (selected == null || issues.hasErrors()) return CanvasNodeOperationResult.outputOnly();
        return CanvasNodeOperationResult.snapshotSyncOutput(
                context.dataAccess().prepareJdbcSnapshotSyncOutput(node, targetSchema, selected),
                CanvasLineageOutputCandidate.jdbcTable(
                        node, selected, dataSourceId, target,
                        cn.superhuang.data.scalpel.contract.task.CanvasLineageCompilation.WriteMode.SNAPSHOT_SYNC,
                        null
                )
        );
    }
}
