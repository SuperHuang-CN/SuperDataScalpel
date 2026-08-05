package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.UnionConfiguration;
import cn.superhuang.data.scalpel.contract.task.UnionMode;
import cn.superhuang.data.scalpel.contract.task.UnionNodeDefinition;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class UnionNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.UNION;
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
        if (!(definition instanceof UnionNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "UNION operator received " + definition.nodeType()
            );
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        UnionConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        CanvasNodeIssueSink issues = context.issues();
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
        if (configuration.inputTableNames() == null) {
            issues.error(
                    "REQUIRED_CONFIGURATION",
                    "输入表必须是数组",
                    "configuration.inputTableNames"
            );
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        if (configuration.inputTableNames().size() < 2) {
            issues.error(
                    "UNION_REQUIRES_MULTIPLE_TABLES",
                    "至少选择两张输入表",
                    "configuration.inputTableNames"
            );
        }
        if (configuration.mode() == null) {
            issues.error(
                    "INVALID_UNION_MODE",
                    "请选择 Union 模式",
                    "configuration.mode"
            );
        }

        Set<String> selectedNames = new HashSet<>();
        List<SparkCanvasTable> selectedTables = new ArrayList<>();
        for (int index = 0; index < configuration.inputTableNames().size(); index++) {
            String tableName = configuration.inputTableNames().get(index);
            String path = "configuration.inputTableNames[" + index + "]";
            CanvasNodeSupport.required(tableName, "请选择输入表", path, issues);
            if (!CanvasNodeSupport.blank(tableName) && !selectedNames.add(tableName)) {
                issues.error(
                        "DUPLICATE_UNION_INPUT_TABLE",
                        "Union 输入表重复：" + tableName,
                        path
                );
                continue;
            }
            SparkCanvasTable table = CanvasNodeSupport.blank(tableName)
                    ? null
                    : inputs.get(tableName);
            if (!CanvasNodeSupport.blank(tableName) && table == null) {
                issues.error(
                        "TABLE_NOT_FOUND",
                        "输入表不在上游数据中：" + tableName,
                        path
                );
            } else if (table != null) {
                selectedTables.add(table);
                validateUniqueColumns(table, path, issues);
            }
        }
        validateSchemas(configuration, selectedTables, issues);
        if (configuration.mode() == UnionMode.DISTINCT
                && selectedTables.stream().flatMap(table -> table.schema().columns().stream())
                .anyMatch(column -> column.fieldType()
                        == cn.superhuang.data.scalpel.contract.type.PlatformDataType.GEOMETRY)) {
            issues.error(
                    "GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                    "包含 Geometry 字段的 Union 不支持 DISTINCT",
                    "configuration.mode"
            );
        }
        DatasetProperties properties = validateDatasetProperties(
                configuration,
                selectedTables,
                issues
        );
        if (issues.hasErrors()
                || selectedTables.size() != configuration.inputTableNames().size()
                || configuration.mode() == null
                || properties == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> unionDataset = selectedTables.getFirst().dataset();
        for (int index = 1; index < selectedTables.size(); index++) {
            unionDataset = unionDataset.unionByName(selectedTables.get(index).dataset());
        }
        if (configuration.mode() == UnionMode.DISTINCT) {
            unionDataset = unionDataset.distinct();
        }
        List<CanvasColumnSchema> analyzedColumns = SparkTypeMapper.fromStructType(
                unionDataset.schema(),
                List.of()
        );
        List<CanvasColumnSchema> outputColumns = analyzedColumns.stream()
                .map(UnionNodeOperator::unionColumn)
                .toList();
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(),
                null,
                outputColumns,
                properties.datasetKind(),
                properties.eventTimeColumn(),
                properties.watermarkDelay()
        );
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(
                outputSchema.name(),
                new SparkCanvasTable(outputSchema, unionDataset)
        );
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateUniqueColumns(
            SparkCanvasTable table,
            String path,
            CanvasNodeIssueSink issues
    ) {
        Set<String> names = new HashSet<>();
        for (CanvasColumnSchema column : table.schema().columns()) {
            if (!names.add(column.name())) {
                issues.error(
                        "DUPLICATE_COLUMN_NAME",
                        "输入表包含同名字段：" + column.name(),
                        path
                );
            }
        }
    }

    private static void validateSchemas(
            UnionConfiguration configuration,
            List<SparkCanvasTable> selectedTables,
            CanvasNodeIssueSink issues
    ) {
        if (selectedTables.size() < 2) return;
        Set<String> expected = selectedTables.getFirst().schema().columns().stream()
                .map(CanvasColumnSchema::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (int index = 1; index < selectedTables.size(); index++) {
            Set<String> actual = selectedTables.get(index).schema().columns().stream()
                    .map(CanvasColumnSchema::name)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (!expected.equals(actual)) {
                issues.error(
                        "UNION_SCHEMA_MISMATCH",
                        "输入表字段集合与第一张表不一致："
                                + selectedTables.get(index).name(),
                        "configuration.inputTableNames[" + index + "]"
                );
                continue;
            }
            Map<String, CanvasColumnSchema> firstColumns =
                    CanvasNodeSupport.columns(selectedTables.getFirst().schema());
            Map<String, CanvasColumnSchema> currentColumns =
                    CanvasNodeSupport.columns(selectedTables.get(index).schema());
            for (String name : expected) {
                CanvasColumnSchema first = firstColumns.get(name);
                CanvasColumnSchema current = currentColumns.get(name);
                if ((first.fieldType()
                        == cn.superhuang.data.scalpel.contract.type.PlatformDataType.GEOMETRY
                        || current.fieldType()
                        == cn.superhuang.data.scalpel.contract.type.PlatformDataType.GEOMETRY)
                        && (first.fieldType() != current.fieldType()
                        || !Objects.equals(first.geometry(), current.geometry()))) {
                    issues.error(
                            "SPATIAL_SCHEMA_MISMATCH",
                            "Union Geometry 字段定义不一致：" + name,
                            "configuration.inputTableNames[" + index + "]"
                    );
                }
            }
        }
    }

    private static DatasetProperties validateDatasetProperties(
            UnionConfiguration configuration,
            List<SparkCanvasTable> selectedTables,
            CanvasNodeIssueSink issues
    ) {
        if (selectedTables.isEmpty()) return null;
        CanvasTableSchema first = selectedTables.getFirst().schema();
        for (int index = 1; index < selectedTables.size(); index++) {
            CanvasTableSchema current = selectedTables.get(index).schema();
            String path = "configuration.inputTableNames[" + index + "]";
            if (current.datasetKind() != first.datasetKind()) {
                issues.error(
                        "UNION_MIXED_DATASET_KIND",
                        "Union 不能混合有界表和无界表",
                        path
                );
                continue;
            }
            if (first.datasetKind() == CanvasDatasetKind.UNBOUNDED
                    && !Objects.equals(
                    first.eventTimeColumn(),
                    current.eventTimeColumn())) {
                issues.error(
                        "UNION_EVENT_TIME_MISMATCH",
                        "无界输入的事件时间字段必须一致",
                        path
                );
            }
            if (first.datasetKind() == CanvasDatasetKind.UNBOUNDED
                    && !Objects.equals(
                    first.watermarkDelay(),
                    current.watermarkDelay())) {
                issues.error(
                        "UNION_WATERMARK_MISMATCH",
                        "无界输入的 Watermark 必须一致",
                        path
                );
            }
        }
        if (first.datasetKind() == CanvasDatasetKind.UNBOUNDED
                && configuration.mode() == UnionMode.DISTINCT) {
            issues.error(
                    "STREAMING_UNION_DISTINCT_NOT_SUPPORTED",
                    "无界 Union 不支持 DISTINCT",
                    "configuration.mode"
            );
        }
        return new DatasetProperties(
                first.datasetKind(),
                first.datasetKind() == CanvasDatasetKind.UNBOUNDED
                        ? first.eventTimeColumn()
                        : null,
                first.datasetKind() == CanvasDatasetKind.UNBOUNDED
                        ? first.watermarkDelay()
                        : null
        );
    }

    private static CanvasColumnSchema unionColumn(CanvasColumnSchema analyzed) {
        return new CanvasColumnSchema(
                analyzed.name(),
                analyzed.fieldType(),
                analyzed.length(),
                analyzed.precision(),
                analyzed.scale(),
                analyzed.nullable(),
                null,
                false,
                false,
                null,
                analyzed.geometry()
        );
    }

    private record DatasetProperties(
            CanvasDatasetKind datasetKind,
            String eventTimeColumn,
            String watermarkDelay
    ) {
    }
}
