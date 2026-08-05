package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.RenameColumnMapping;
import cn.superhuang.data.scalpel.contract.task.RenameConfiguration;
import cn.superhuang.data.scalpel.contract.task.RenameNodeDefinition;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Atomically renames one logical table and any selected columns without mutating upstream state.
 */
public final class RenameNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.RENAME;
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
        if (!(definition instanceof RenameNodeDefinition node)) {
            throw new IllegalArgumentException("RENAME operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        RenameConfiguration configuration = node.configuration();
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
        if (configuration.columnMappings() == null) {
            issues.error(
                    "REQUIRED_CONFIGURATION",
                    "字段重命名映射必须是数组",
                    "configuration.columnMappings"
            );
        }

        SparkCanvasTable source = inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error(
                    "TABLE_NOT_FOUND",
                    "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName"
            );
        }
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && !configuration.outputTableName().equals(configuration.sourceTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error(
                    "DUPLICATE_TABLE_NAME",
                    "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName"
            );
        }
        if (source == null || configuration.columnMappings() == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Map<String, CanvasColumnSchema> sourceColumns = CanvasNodeSupport.columns(source.schema());
        Map<String, String> renamedColumns = new LinkedHashMap<>();
        List<Integer> redundantMappings = new ArrayList<>();
        for (int index = 0; index < configuration.columnMappings().size(); index++) {
            RenameColumnMapping mapping = configuration.columnMappings().get(index);
            String path = "configuration.columnMappings[" + index + "]";
            if (mapping == null
                    || CanvasNodeSupport.blank(mapping.sourceColumnName())
                    || CanvasNodeSupport.blank(mapping.targetColumnName())) {
                issues.error("REQUIRED_CONFIGURATION", "字段重命名映射不完整", path);
                continue;
            }
            if (renamedColumns.putIfAbsent(mapping.sourceColumnName(), mapping.targetColumnName()) != null) {
                issues.error(
                        "DUPLICATE_RENAME_SOURCE_COLUMN",
                        "来源字段重复配置：" + mapping.sourceColumnName(),
                        path + ".sourceColumnName"
                );
                continue;
            }
            if (!sourceColumns.containsKey(mapping.sourceColumnName())) {
                issues.error(
                        "COLUMN_NOT_FOUND",
                        "来源字段不存在：" + mapping.sourceColumnName(),
                        path + ".sourceColumnName"
                );
            }
            if (mapping.sourceColumnName().equals(mapping.targetColumnName())) {
                redundantMappings.add(index);
            }
        }

        List<CanvasColumnSchema> targetColumns = new ArrayList<>(source.schema().columns().size());
        Set<String> finalNames = new HashSet<>();
        boolean columnNameChanged = false;
        for (CanvasColumnSchema column : source.schema().columns()) {
            String targetName = renamedColumns.getOrDefault(column.name(), column.name());
            if (!finalNames.add(targetName)) {
                issues.error(
                        "DUPLICATE_COLUMN_NAME",
                        "重命名结果包含同名字段：" + targetName,
                        "configuration.columnMappings"
                );
            }
            columnNameChanged |= !column.name().equals(targetName);
            targetColumns.add(copyWithName(column, targetName));
        }
        if (issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        boolean tableNameChanged = !configuration.sourceTableName().equals(configuration.outputTableName());
        if (!tableNameChanged && !columnNameChanged) {
            issues.warning(
                    "RENAME_HAS_NO_EFFECT",
                    "表名和字段名均未发生变化",
                    "configuration"
            );
        } else {
            for (int index : redundantMappings) {
                issues.warning(
                        "REDUNDANT_RENAME_MAPPING",
                        "字段映射前后名称相同，不会产生变化",
                        "configuration.columnMappings[" + index + "]"
                );
            }
        }

        Dataset<Row> sourceDataset = source.dataset();
        Column[] projection = new Column[source.schema().columns().size()];
        for (int index = 0; index < source.schema().columns().size(); index++) {
            CanvasColumnSchema sourceColumn = source.schema().columns().get(index);
            projection[index] = sourceDataset
                    .col(CanvasNodeSupport.quoteIdentifier(sourceColumn.name()))
                    .alias(targetColumns.get(index).name());
        }
        Dataset<Row> renamedDataset = sourceDataset.select(projection);
        CanvasTableSchema renamedSchema = new CanvasTableSchema(
                configuration.outputTableName(),
                source.schema().origin(),
                SparkTypeMapper.fromStructType(renamedDataset.schema(), targetColumns),
                source.schema().datasetKind(),
                source.schema().eventTimeColumn(),
                source.schema().watermarkDelay()
        );
        SparkCanvasTable renamedTable = new SparkCanvasTable(renamedSchema, renamedDataset);

        Map<String, SparkCanvasTable> output = new LinkedHashMap<>();
        for (Map.Entry<String, SparkCanvasTable> entry : inputs.entrySet()) {
            if (entry.getKey().equals(configuration.sourceTableName())) {
                output.put(configuration.outputTableName(), renamedTable);
            } else {
                output.put(entry.getKey(), entry.getValue());
            }
        }
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static CanvasColumnSchema copyWithName(CanvasColumnSchema source, String name) {
        return new CanvasColumnSchema(
                name,
                source.fieldType(),
                source.length(),
                source.precision(),
                source.scale(),
                source.nullable(),
                source.defaultValue(),
                source.autoIncrement(),
                source.generated(),
                source.comment(),
                source.geometry()
        );
    }
}
