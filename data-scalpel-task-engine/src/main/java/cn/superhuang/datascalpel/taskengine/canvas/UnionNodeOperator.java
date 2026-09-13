package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.UnionConfiguration;
import cn.superhuang.data.scalpel.contract.task.UnionMergeFieldAction;
import cn.superhuang.data.scalpel.contract.task.UnionMergeFieldRule;
import cn.superhuang.data.scalpel.contract.task.UnionMergeTable;
import cn.superhuang.data.scalpel.contract.task.UnionMode;
import cn.superhuang.data.scalpel.contract.task.UnionNodeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
        boolean allInputsResolved = selectedTables.size()
                == configuration.inputTableNames().size();
        AlignmentPlan alignment = !allInputsResolved
                ? null
                : configuration.mergingTables() == null
                ? validateLegacySchemas(configuration, selectedTables, issues)
                : validateMergeLayers(configuration, selectedTables, issues);
        if (configuration.mode() == UnionMode.DISTINCT
                && selectedTables.stream().flatMap(table -> table.schema().columns().stream())
                .anyMatch(column -> column.fieldType() == PlatformDataType.GEOMETRY)) {
            issues.error(
                    "GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                    "包含 Geometry 字段的 Union 不支持 DISTINCT",
                    "configuration.mode"
            );
        }
        DatasetProperties properties = alignment == null
                ? null
                : validateDatasetProperties(
                configuration,
                selectedTables,
                alignment,
                issues
        );
        if (issues.hasErrors()
                || selectedTables.size() != configuration.inputTableNames().size()
                || configuration.mode() == null
                || alignment == null
                || properties == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> unionDataset = alignment.project(selectedTables.getFirst(), 0);
        for (int index = 1; index < selectedTables.size(); index++) {
            unionDataset = unionDataset.unionByName(
                    alignment.project(selectedTables.get(index), index),
                    alignment.flexible()
            );
        }
        if (configuration.mode() == UnionMode.DISTINCT) {
            unionDataset = unionDataset.distinct();
        }
        List<CanvasColumnSchema> analyzedColumns = SparkTypeMapper.fromStructType(
                unionDataset.schema(),
                alignment.outputColumns()
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
            if (!names.add(key(column.name()))) {
                issues.error(
                        "DUPLICATE_COLUMN_NAME",
                        "输入表包含大小写不敏感的同名字段：" + column.name(),
                        path
                );
            }
        }
    }

    private static AlignmentPlan validateLegacySchemas(
            UnionConfiguration configuration,
            List<SparkCanvasTable> selectedTables,
            CanvasNodeIssueSink issues
    ) {
        if (selectedTables.isEmpty()) return null;
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
        List<Map<String, SourceBinding>> bindings = selectedTables.stream()
                .map(table -> {
                    Map<String, SourceBinding> tableBindings = new LinkedHashMap<>();
                    table.schema().columns().forEach(column -> tableBindings.put(
                            key(column.name()),
                            new SourceBinding(column, column.name())
                    ));
                    return Map.copyOf(tableBindings);
                })
                .toList();
        return new AlignmentPlan(
                selectedTables.getFirst().schema().columns(),
                bindings,
                false
        );
    }

    private static AlignmentPlan validateMergeLayers(
            UnionConfiguration configuration,
            List<SparkCanvasTable> selectedTables,
            CanvasNodeIssueSink issues
    ) {
        if (selectedTables.isEmpty()) return null;
        List<CanvasColumnSchema> outputColumns = new ArrayList<>(
                selectedTables.getFirst().schema().columns()
        );
        Map<String, CanvasColumnSchema> outputByName = new LinkedHashMap<>();
        outputColumns.forEach(column -> outputByName.put(key(column.name()), column));

        Map<String, UnionMergeTable> rulesByTable = new LinkedHashMap<>();
        for (int index = 0; index < configuration.mergingTables().size(); index++) {
            UnionMergeTable tableRules = configuration.mergingTables().get(index);
            String path = "configuration.mergingTables[" + index + "]";
            if (tableRules == null) {
                issues.error("REQUIRED_CONFIGURATION", "合并层配置不能为空", path);
                continue;
            }
            CanvasNodeSupport.required(
                    tableRules.tableName(),
                    "请选择合并层表",
                    path + ".tableName",
                    issues
            );
            if (tableRules.fieldRules() == null) {
                issues.error(
                        "REQUIRED_CONFIGURATION",
                        "合并层字段规则必须是数组",
                        path + ".fieldRules"
                );
            }
            if (CanvasNodeSupport.blank(tableRules.tableName())) continue;
            if (Objects.equals(tableRules.tableName(), configuration.inputTableNames().getFirst())) {
                issues.error(
                        "UNION_BASE_TABLE_RULES_NOT_ALLOWED",
                        "第一张输入表是基准层，不能配置合并层字段规则",
                        path + ".tableName"
                );
                continue;
            }
            if (!configuration.inputTableNames().contains(tableRules.tableName())) {
                issues.error(
                        "UNION_MERGE_TABLE_NOT_SELECTED",
                        "字段规则引用了未选择的合并层：" + tableRules.tableName(),
                        path + ".tableName"
                );
                continue;
            }
            if (rulesByTable.putIfAbsent(tableRules.tableName(), tableRules) != null) {
                issues.error(
                        "DUPLICATE_UNION_MERGE_TABLE",
                        "合并层字段配置重复：" + tableRules.tableName(),
                        path + ".tableName"
                );
            }
        }

        List<Map<String, SourceBinding>> bindings = new ArrayList<>();
        Map<String, SourceBinding> baseBindings = new LinkedHashMap<>();
        selectedTables.getFirst().schema().columns().forEach(column -> baseBindings.put(
                key(column.name()),
                new SourceBinding(column, column.name())
        ));
        bindings.add(Map.copyOf(baseBindings));
        int baseGeometryCount = geometryColumnCount(selectedTables.getFirst());

        for (int tableIndex = 1; tableIndex < selectedTables.size(); tableIndex++) {
            SparkCanvasTable table = selectedTables.get(tableIndex);
            boolean geometryCountMatches = geometryColumnCount(table) == baseGeometryCount;
            if (!geometryCountMatches) {
                issues.error(
                        "UNION_GEOMETRY_SCHEMA_MISMATCH",
                        "Merge Layers 要求所有输入均为属性表，或具有相同数量的 Geometry 字段",
                        "configuration.inputTableNames[" + tableIndex + "]"
                );
            }
            UnionMergeTable tableRules = rulesByTable.get(table.name());
            Map<String, UnionMergeFieldRule> customRules = validateMergeFieldRules(
                    table,
                    tableRules,
                    configuration,
                    issues
            );
            Map<String, SourceBinding> tableBindings = new LinkedHashMap<>();
            for (CanvasColumnSchema source : table.schema().columns()) {
                UnionMergeFieldRule rule = customRules.get(key(source.name()));
                UnionMergeFieldAction action = rule == null
                        ? defaultAction(source, outputByName)
                        : rule.action();
                String targetName = rule == null
                        ? defaultTarget(source, outputByName)
                        : rule.targetColumnName();
                if (action == null) continue;
                if (source.fieldType() == PlatformDataType.GEOMETRY) {
                    if (!geometryCountMatches) continue;
                    if (action != UnionMergeFieldAction.MATCH) {
                        issues.error(
                                "UNION_GEOMETRY_FIELD_ACTION_INVALID",
                                "Geometry 字段必须 Match 到基准层 Geometry，不能 Rename 或 Remove",
                                fieldRulePath(configuration, table.name(), source.name())
                        );
                        continue;
                    }
                }
                if (action == UnionMergeFieldAction.REMOVE) continue;
                if (CanvasNodeSupport.blank(targetName)) continue;

                String targetKey = key(targetName);
                CanvasColumnSchema target = outputByName.get(targetKey);
                if (action == UnionMergeFieldAction.MATCH) {
                    if (target == null) {
                        issues.error(
                                "UNION_MATCH_TARGET_NOT_FOUND",
                                "Match 目标字段不存在：" + targetName,
                                fieldRulePath(configuration, table.name(), source.name())
                        );
                        continue;
                    }
                    validateMatchTypes(
                            source,
                            target,
                            fieldRulePath(configuration, table.name(), source.name()),
                            issues
                    );
                    targetName = target.name();
                } else {
                    if (target != null) {
                        issues.error(
                                "DUPLICATE_COLUMN_NAME",
                                "Rename 目标已存在，请改用 Match：" + targetName,
                                fieldRulePath(configuration, table.name(), source.name())
                        );
                        continue;
                    }
                    CanvasColumnSchema appended = renamedNullableColumn(source, targetName);
                    outputColumns.add(appended);
                    outputByName.put(targetKey, appended);
                }
                if (tableBindings.putIfAbsent(
                        key(targetName),
                        new SourceBinding(source, targetName)
                ) != null) {
                    issues.error(
                            "DUPLICATE_UNION_MERGE_TARGET",
                            "同一合并层的多个字段写入同一输出字段：" + targetName,
                            fieldRulePath(configuration, table.name(), source.name())
                    );
                }
            }
            bindings.add(Map.copyOf(tableBindings));
        }
        return new AlignmentPlan(
                List.copyOf(outputColumns),
                List.copyOf(bindings),
                true
        );
    }

    private static Map<String, UnionMergeFieldRule> validateMergeFieldRules(
            SparkCanvasTable table,
            UnionMergeTable tableRules,
            UnionConfiguration configuration,
            CanvasNodeIssueSink issues
    ) {
        if (tableRules == null || tableRules.fieldRules() == null) return Map.of();
        Map<String, CanvasColumnSchema> sources = new LinkedHashMap<>();
        table.schema().columns().forEach(column -> sources.put(key(column.name()), column));
        Map<String, UnionMergeFieldRule> result = new LinkedHashMap<>();
        int tableRuleIndex = configuration.mergingTables().indexOf(tableRules);
        for (int index = 0; index < tableRules.fieldRules().size(); index++) {
            UnionMergeFieldRule rule = tableRules.fieldRules().get(index);
            String path = "configuration.mergingTables[" + tableRuleIndex
                    + "].fieldRules[" + index + "]";
            if (rule == null) {
                issues.error("REQUIRED_CONFIGURATION", "字段规则不能为空", path);
                continue;
            }
            CanvasNodeSupport.required(
                    rule.sourceColumnName(),
                    "请选择来源字段",
                    path + ".sourceColumnName",
                    issues
            );
            if (rule.action() == null) {
                issues.error(
                        "INVALID_UNION_MERGE_FIELD_ACTION",
                        "请选择 Match、Rename 或 Remove",
                        path + ".action"
                );
            }
            if (CanvasNodeSupport.blank(rule.sourceColumnName()) || rule.action() == null) continue;
            String sourceKey = key(rule.sourceColumnName());
            CanvasColumnSchema source = sources.get(sourceKey);
            if (source == null) {
                issues.error(
                        "COLUMN_NOT_FOUND",
                        "合并层字段不存在：" + rule.sourceColumnName(),
                        path + ".sourceColumnName"
                );
            }
            if (result.putIfAbsent(sourceKey, rule) != null) {
                issues.error(
                        "DUPLICATE_UNION_MERGE_SOURCE",
                        "合并层字段配置重复：" + rule.sourceColumnName(),
                        path + ".sourceColumnName"
                );
            }
            if (rule.action() == UnionMergeFieldAction.REMOVE) {
                if (rule.targetColumnName() != null) {
                    issues.error(
                            "UNION_REMOVE_TARGET_NOT_ALLOWED",
                            "Remove 不能配置目标字段",
                            path + ".targetColumnName"
                    );
                }
            } else {
                CanvasNodeSupport.required(
                        rule.targetColumnName(),
                        rule.action() == UnionMergeFieldAction.MATCH
                                ? "请选择 Match 目标字段"
                                : "请输入 Rename 后的字段名",
                        path + ".targetColumnName",
                        issues
                );
            }
        }
        return Map.copyOf(result);
    }

    private static int geometryColumnCount(SparkCanvasTable table) {
        return (int) table.schema().columns().stream()
                .filter(column -> column.fieldType() == PlatformDataType.GEOMETRY)
                .count();
    }

    private static UnionMergeFieldAction defaultAction(
            CanvasColumnSchema source,
            Map<String, CanvasColumnSchema> outputByName
    ) {
        return outputByName.containsKey(key(source.name()))
                ? UnionMergeFieldAction.MATCH
                : UnionMergeFieldAction.RENAME;
    }

    private static String defaultTarget(
            CanvasColumnSchema source,
            Map<String, CanvasColumnSchema> outputByName
    ) {
        CanvasColumnSchema existing = outputByName.get(key(source.name()));
        return existing == null ? source.name() : existing.name();
    }

    private static void validateMatchTypes(
            CanvasColumnSchema source,
            CanvasColumnSchema target,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (source.fieldType() == PlatformDataType.GEOMETRY
                || target.fieldType() == PlatformDataType.GEOMETRY) {
            if (source.fieldType() != PlatformDataType.GEOMETRY
                    || target.fieldType() != PlatformDataType.GEOMETRY
                    || !Objects.equals(source.geometry(), target.geometry())) {
                issues.error(
                        "SPATIAL_SCHEMA_MISMATCH",
                        "Match 的 Geometry 字段类型、CRS 和维度必须一致",
                        path
                );
            }
            return;
        }
        if (source.fieldType() != target.fieldType()
                && !(numeric(source.fieldType()) && numeric(target.fieldType()))) {
            issues.error(
                    "UNION_MATCH_TYPE_MISMATCH",
                    "Match 只允许相同类型或数值类型之间转换："
                            + source.name() + " → " + target.name(),
                    path
            );
        }
    }

    private static boolean numeric(PlatformDataType type) {
        return type == PlatformDataType.BYTE || type == PlatformDataType.SHORT
                || type == PlatformDataType.INTEGER || type == PlatformDataType.LONG
                || type == PlatformDataType.FLOAT || type == PlatformDataType.DOUBLE
                || type == PlatformDataType.DECIMAL;
    }

    private static CanvasColumnSchema renamedNullableColumn(
            CanvasColumnSchema source,
            String targetName
    ) {
        return new CanvasColumnSchema(
                targetName,
                source.fieldType(),
                source.length(),
                source.precision(),
                source.scale(),
                true,
                null,
                false,
                false,
                source.comment(),
                source.geometry()
        );
    }

    private static String fieldRulePath(
            UnionConfiguration configuration,
            String tableName,
            String sourceColumnName
    ) {
        for (int tableIndex = 0; tableIndex < configuration.mergingTables().size(); tableIndex++) {
            UnionMergeTable table = configuration.mergingTables().get(tableIndex);
            if (table == null || !Objects.equals(table.tableName(), tableName)
                    || table.fieldRules() == null) continue;
            for (int fieldIndex = 0; fieldIndex < table.fieldRules().size(); fieldIndex++) {
                UnionMergeFieldRule rule = table.fieldRules().get(fieldIndex);
                if (rule != null && rule.sourceColumnName() != null
                        && sourceColumnName != null
                        && rule.sourceColumnName().equalsIgnoreCase(sourceColumnName)) {
                    return "configuration.mergingTables[" + tableIndex
                            + "].fieldRules[" + fieldIndex + "]";
                }
            }
        }
        int inputIndex = configuration.inputTableNames().indexOf(tableName);
        return "configuration.inputTableNames[" + inputIndex + "]";
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    private static DatasetProperties validateDatasetProperties(
            UnionConfiguration configuration,
            List<SparkCanvasTable> selectedTables,
            AlignmentPlan alignment,
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
            String currentEventTimeTarget = alignment == null
                    ? current.eventTimeColumn()
                    : alignment.outputName(index, current.eventTimeColumn());
            if (first.datasetKind() == CanvasDatasetKind.UNBOUNDED
                    && !Objects.equals(first.eventTimeColumn(), currentEventTimeTarget)) {
                issues.error(
                        "UNION_EVENT_TIME_MISMATCH",
                        "无界输入的事件时间字段必须 Match 到基准层事件时间字段",
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

    private record SourceBinding(CanvasColumnSchema source, String outputName) {
    }

    private record AlignmentPlan(
            List<CanvasColumnSchema> outputColumns,
            List<Map<String, SourceBinding>> bindings,
            boolean flexible
    ) {
        Dataset<Row> project(SparkCanvasTable table, int index) {
            if (!flexible) return table.dataset();
            Map<String, SourceBinding> tableBindings = bindings.get(index);
            List<Column> projection = new ArrayList<>(outputColumns.size());
            for (CanvasColumnSchema output : outputColumns) {
                SourceBinding binding = tableBindings.get(key(output.name()));
                Column value;
                if (binding == null) {
                    value = functions.lit(null).cast(SparkTypeMapper.toDataType(output));
                } else {
                    value = table.dataset().col(
                            CanvasNodeSupport.quoteIdentifier(binding.source().name())
                    );
                    if (binding.source().fieldType() != PlatformDataType.GEOMETRY) {
                        value = value.cast(SparkTypeMapper.toDataType(output));
                    }
                }
                projection.add(value.as(output.name(), SparkTypeMapper.metadata(output)));
            }
            return table.dataset().select(projection.toArray(Column[]::new));
        }

        String outputName(int tableIndex, String sourceColumnName) {
            if (sourceColumnName == null || tableIndex >= bindings.size()) return null;
            return bindings.get(tableIndex).values().stream()
                    .filter(binding -> binding.source().name().equals(sourceColumnName))
                    .map(SourceBinding::outputName)
                    .findFirst()
                    .orElse(null);
        }
    }

    private record DatasetProperties(
            CanvasDatasetKind datasetKind,
            String eventTimeColumn,
            String watermarkDelay
    ) {
    }
}
