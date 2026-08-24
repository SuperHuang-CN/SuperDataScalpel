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
import cn.superhuang.data.scalpel.contract.task.JdbcInputTableSelection;
import cn.superhuang.data.scalpel.contract.task.MetadataTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
        List<JdbcInputTableSelection> selections = configuration.tables();
        if (selections == null || selections.isEmpty()) {
            issues.error(
                    "REQUIRED_CONFIGURATION",
                    "请至少选择一张物理表",
                    "configuration.tables"
            );
        }
        Set<String> configuredTableNames = new LinkedHashSet<>();
        if (selections != null) {
            for (int index = 0; index < selections.size(); index++) {
                JdbcInputTableSelection selection = selections.get(index);
                String path = "configuration.tables[" + index + "].tableName";
                if (selection == null) {
                    issues.error("REQUIRED_CONFIGURATION", "物理表配置不能为空", "configuration.tables[" + index + "]");
                    continue;
                }
                CanvasNodeSupport.required(selection.tableName(), "输入表名不能为空", path, issues);
                JdbcInputReadOptionPolicy.validate(
                        selection.readOptions(),
                        "configuration.tables[" + index + "].readOptions",
                        issues
                );
                if (!CanvasNodeSupport.blank(selection.tableName())
                        && !configuredTableNames.add(selection.tableName())) {
                    issues.error(
                            "DUPLICATE_TABLE_NAME",
                            "同一 JDBC 输入中不能重复选择物理表：" + selection.tableName(),
                            path
                    );
                }
            }
        }
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
        List<ResolvedTable> resolvedTables = new ArrayList<>();
        for (int index = 0; index < selections.size(); index++) {
            JdbcInputTableSelection selection = selections.get(index);
            String path = "configuration.tables[" + index + "].tableName";
            MetadataTable table = dataSource.table(selection.tableName());
            if (table == null) {
                issues.error(
                        "TABLE_NOT_FOUND",
                        "输入表不存在：" + selection.tableName(),
                        path
                );
                continue;
            }
            CanvasTableSchema schema = new CanvasTableSchema(
                    selection.tableName(),
                    CanvasTableOrigin.jdbc(dataSourceId, selection.tableName()),
                    table.columns()
            );
            CanvasNodeSupport.validateSupportedGeometry(schema.columns(), path, issues);
            CanvasNodeSupport.validateJdbcGeometryDatabase(
                    schema.columns(),
                    dataSource.metadata().jdbcDatabaseType(),
                    path,
                    issues
            );
            resolvedTables.add(new ResolvedTable(selection, schema));
        }
        List<CanvasTableSchema> schemas = resolvedTables.stream().map(ResolvedTable::schema).toList();
        if (issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(schemas);
        }
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>();
        for (ResolvedTable resolved : resolvedTables) {
            Dataset<Row> dataset = context.dataAccess().readJdbcInput(
                    node, resolved.selection(), resolved.schema());
            output.put(
                    resolved.schema().name(),
                    new SparkCanvasTable(resolved.schema(), dataset)
            );
        }
        return CanvasNodeOperationResult.propagated(output, schemas);
    }

    private record ResolvedTable(
            JdbcInputTableSelection selection,
            CanvasTableSchema schema
    ) {
    }
}
