package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.DatabaseObjectType;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.MetadataTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class JdbcOutputNodeOperator implements CanvasNodeOperator {
    private final OutputColumnMappingOperator mappingOperator = new OutputColumnMappingOperator();

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.JDBC_OUTPUT;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.OUTPUT;
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
        if (!(definition instanceof JdbcOutputNodeDefinition node)) {
            throw new IllegalArgumentException("JDBC_OUTPUT operator received " + definition.nodeType());
        }
        JdbcOutputConfiguration configuration = node.configuration();
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
        UUID dataSourceId = CanvasNodeSupport.parseUuid(
                configuration.dataSourceId(), "configuration.dataSourceId", issues);
        CanvasNodeSupport.required(
                configuration.targetTableName(),
                "请选择目标表",
                "configuration.targetTableName",
                issues
        );
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
        MetadataIndex.DataSourceEntry dataSource =
                dataSourceId == null ? null : context.metadataIndex().dataSource(dataSourceId);
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
        MetadataTable target =
                dataSource == null ? null : dataSource.table(configuration.targetTableName());
        if (!CanvasNodeSupport.blank(configuration.targetTableName()) && target == null) {
            issues.error(
                    "TABLE_NOT_FOUND",
                    "目标表不存在：" + configuration.targetTableName(),
                    "configuration.targetTableName"
            );
        } else if (target != null && target.objectType() != DatabaseObjectType.TABLE) {
            issues.error(
                    "TARGET_TABLE_NOT_WRITABLE",
                    "JDBC 输出目标必须是物理表",
                    "configuration.targetTableName"
            );
        }
        if (source == null || target == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.outputOnly();
        }

        CanvasTableSchema targetSchema =
                new CanvasTableSchema(target.tableName(), null, target.columns());
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
                context.dataAccess().prepareJdbcOutput(node, targetSchema, selected);
        return CanvasNodeOperationResult.output(prepared);
    }
}
