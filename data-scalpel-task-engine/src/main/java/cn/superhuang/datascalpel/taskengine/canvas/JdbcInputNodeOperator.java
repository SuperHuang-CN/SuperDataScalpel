package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableOrigin;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.JdbcInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.MetadataTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class JdbcInputNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.JDBC_INPUT;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.INPUT;
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
        if (!(definition instanceof JdbcInputNodeDefinition node)) {
            throw new IllegalArgumentException("JDBC_INPUT operator received " + definition.nodeType());
        }
        JdbcInputConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(List.of());
        }
        CanvasNodeIssueSink issues = context.issues();
        UUID dataSourceId = CanvasNodeSupport.parseUuid(
                configuration.dataSourceId(), "configuration.dataSourceId", issues);
        CanvasNodeSupport.required(
                configuration.tableName(), "输入表名不能为空", "configuration.tableName", issues);
        if (issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(List.of());
        }

        MetadataIndex.DataSourceEntry dataSource = context.metadataIndex().dataSource(dataSourceId);
        if (dataSource == null
                || !dataSource.metadata().enabled()
                || dataSource.metadata().connectionKind() != ConnectionKind.JDBC
                || !dataSource.metadata().purposes().contains(DataSourcePurpose.SOURCE)) {
            issues.error(
                    "DATA_SOURCE_UNAVAILABLE",
                    "输入数据源不存在、未启用或不具有 SOURCE 用途",
                    "configuration.dataSourceId"
            );
            return CanvasNodeOperationResult.invalid(List.of());
        }
        MetadataTable table = dataSource.table(configuration.tableName());
        if (table == null) {
            issues.error(
                    "TABLE_NOT_FOUND",
                    "输入表不存在：" + configuration.tableName(),
                    "configuration.tableName"
            );
            return CanvasNodeOperationResult.invalid(List.of());
        }

        CanvasTableSchema schema = new CanvasTableSchema(
                configuration.tableName(),
                CanvasTableOrigin.jdbc(dataSourceId, configuration.tableName()),
                table.columns()
        );
        CanvasNodeSupport.validateSupportedGeometry(
                schema.columns(),
                "configuration.tableName",
                issues
        );
        CanvasNodeSupport.validateJdbcGeometryDatabase(
                schema.columns(),
                dataSource.metadata().jdbcDatabaseType(),
                "configuration.tableName",
                issues
        );
        if (issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(List.of());
        }
        Dataset<Row> dataset = context.dataAccess().readJdbcInput(node, schema);
        Map<String, SparkCanvasTable> output = Map.of(
                schema.name(),
                new SparkCanvasTable(schema, dataset)
        );
        return CanvasNodeOperationResult.propagated(output, List.of(schema));
    }
}
