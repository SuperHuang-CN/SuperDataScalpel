package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SelectColumnsConfiguration;
import cn.superhuang.data.scalpel.contract.task.SelectColumnsNodeDefinition;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SelectColumnsNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SELECT_COLUMNS;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.PROCESSOR;
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
        if (!(definition instanceof SelectColumnsNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "SELECT_COLUMNS operator received " + definition.nodeType()
            );
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        SelectColumnsConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        CanvasNodeIssueSink issues = context.issues();
        CanvasNodeSupport.required(
                configuration.sourceTableName(),
                "请选择来源表",
                "configuration.sourceTableName",
                issues
        );
        CanvasNodeSupport.required(
                configuration.outputTableName(),
                "请输入输出表名",
                "configuration.outputTableName",
                issues
        );
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error(
                    "DUPLICATE_TABLE_NAME",
                    "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName"
            );
        }

        SparkCanvasTable source = CanvasNodeSupport.blank(configuration.sourceTableName())
                ? null
                : inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error(
                    "TABLE_NOT_FOUND",
                    "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName"
            );
        }
        if (configuration.columns() == null) {
            issues.error(
                    "REQUIRED_CONFIGURATION",
                    "选择字段必须是数组",
                    "configuration.columns"
            );
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        if (configuration.columns().isEmpty()) {
            issues.error(
                    "EMPTY_COLUMN_SELECTION",
                    "至少选择一个字段",
                    "configuration.columns"
            );
        }

        Map<String, CanvasColumnSchema> sourceColumns = source == null
                ? Map.of()
                : CanvasNodeSupport.columns(source.schema());
        Set<String> selectedNames = new HashSet<>();
        List<CanvasColumnSchema> selectedColumns = new ArrayList<>(configuration.columns().size());
        for (int index = 0; index < configuration.columns().size(); index++) {
            String columnName = configuration.columns().get(index);
            String path = "configuration.columns[" + index + "]";
            if (CanvasNodeSupport.blank(columnName)) {
                issues.error("REQUIRED_CONFIGURATION", "字段名不能为空", path);
                continue;
            }
            if (!selectedNames.add(columnName)) {
                issues.error(
                        "DUPLICATE_SELECTED_COLUMN",
                        "字段被重复选择：" + columnName,
                        path
                );
                continue;
            }
            CanvasColumnSchema sourceColumn = sourceColumns.get(columnName);
            if (source != null && sourceColumn == null) {
                issues.error("COLUMN_NOT_FOUND", "来源字段不存在：" + columnName, path);
                continue;
            }
            if (sourceColumn != null) {
                selectedColumns.add(sourceColumn);
            }
        }
        if (source == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> sourceDataset = source.dataset();
        Column[] projection = configuration.columns().stream()
                .map(columnName -> sourceDataset.col(CanvasNodeSupport.quoteIdentifier(columnName)))
                .toArray(Column[]::new);
        Dataset<Row> projectedDataset = sourceDataset.select(projection);

        String eventTimeColumn = source.schema().eventTimeColumn();
        boolean keepsEventTime = eventTimeColumn != null && selectedNames.contains(eventTimeColumn);
        CanvasTableSchema projectedSchema = new CanvasTableSchema(
                configuration.outputTableName(),
                source.schema().origin(),
                selectedColumns,
                source.schema().datasetKind(),
                keepsEventTime ? eventTimeColumn : null,
                keepsEventTime ? source.schema().watermarkDelay() : null
        );
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(
                projectedSchema.name(),
                new SparkCanvasTable(projectedSchema, projectedDataset)
        );
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }
}
