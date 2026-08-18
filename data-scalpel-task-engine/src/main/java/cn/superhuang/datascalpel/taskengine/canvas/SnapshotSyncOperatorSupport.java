package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.MetadataUniqueKey;
import cn.superhuang.data.scalpel.contract.task.SnapshotDeletePolicy;
import cn.superhuang.data.scalpel.contract.task.SnapshotSyncConfiguration;
import cn.superhuang.data.scalpel.contract.task.SnapshotTargetOnlyAction;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared compile-time rules for JDBC and model snapshot synchronization outputs. */
final class SnapshotSyncOperatorSupport {
    private static final int MAX_KEY_COLUMNS = 32;
    private final OutputColumnMappingOperator mappingOperator = new OutputColumnMappingOperator();

    Dataset<Row> validateAndMap(
            SnapshotSyncConfiguration configuration,
            Map<String, SparkCanvasTable> inputs,
            CanvasTableSchema targetSchema,
            List<MetadataUniqueKey> uniqueKeys,
            CanvasNodeIssueSink issues
    ) {
        CanvasNodeSupport.required(
                configuration.sourceTableName(),
                "请选择来源表",
                "configuration.sourceTableName",
                issues
        );
        if (configuration.columnMappings() == null) {
            issues.error("REQUIRED_CONFIGURATION", "字段映射列表不能为空", "configuration.columnMappings");
        }
        validateDeletePolicy(configuration.deletePolicy(), issues);
        validateKeyShape(configuration.keyColumns(), issues);

        SparkCanvasTable source = inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error(
                    "TABLE_NOT_FOUND",
                    "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName"
            );
        }
        if (source != null && source.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error(
                    "SNAPSHOT_SYNC_REQUIRES_BOUNDED_INPUT",
                    "快照同步只接受 BOUNDED 来源表",
                    "configuration.sourceTableName"
            );
        }
        if (source == null || issues.hasErrors()) {
            return null;
        }

        CanvasNodeSupport.validateSupportedGeometry(
                targetSchema.columns(),
                "configuration.sourceTableName",
                issues
        );
        if (issues.hasErrors()) {
            return null;
        }
        Dataset<Row> selected = mappingOperator.apply(
                source,
                targetSchema,
                configuration.columnMappings(),
                issues
        );
        if (selected == null || issues.hasErrors()) {
            return null;
        }
        validateMappedKeys(
                configuration.keyColumns(),
                targetSchema,
                uniqueKeys == null ? List.of() : uniqueKeys,
                Set.of(selected.columns()),
                issues
        );
        return issues.hasErrors() ? null : selected;
    }

    private static void validateKeyShape(List<String> keys, CanvasNodeIssueSink issues) {
        if (keys == null || keys.isEmpty()) {
            issues.error(
                    "SNAPSHOT_SYNC_KEY_REQUIRED",
                    "快照同步必须选择至少一个 Key 字段",
                    "configuration.keyColumns"
            );
            return;
        }
        if (keys.size() > MAX_KEY_COLUMNS) {
            issues.error(
                    "SNAPSHOT_SYNC_KEY_REQUIRED",
                    "快照同步 Key 最多允许 32 个字段",
                    "configuration.keyColumns"
            );
        }
        Set<String> observed = new HashSet<>();
        for (int index = 0; index < keys.size(); index++) {
            String key = keys.get(index);
            if (CanvasNodeSupport.blank(key)) {
                issues.error(
                        "SNAPSHOT_SYNC_KEY_REQUIRED",
                        "Key 字段名不能为空",
                        "configuration.keyColumns[" + index + "]"
                );
            } else if (!observed.add(key)) {
                issues.error(
                        "SNAPSHOT_SYNC_KEY_DUPLICATE",
                        "Key 字段不能重复：" + key,
                        "configuration.keyColumns[" + index + "]"
                );
            }
        }
    }

    private static void validateMappedKeys(
            List<String> keys,
            CanvasTableSchema targetSchema,
            List<MetadataUniqueKey> uniqueKeys,
            Set<String> mappedColumns,
            CanvasNodeIssueSink issues
    ) {
        Map<String, CanvasColumnSchema> targetColumns = new LinkedHashMap<>();
        for (CanvasColumnSchema column : targetSchema.columns()) {
            targetColumns.put(column.name(), column);
        }
        for (int index = 0; index < keys.size(); index++) {
            String key = keys.get(index);
            CanvasColumnSchema column = targetColumns.get(key);
            String path = "configuration.keyColumns[" + index + "]";
            if (column == null) {
                issues.error(
                        "SNAPSHOT_SYNC_KEY_COLUMN_NOT_FOUND",
                        "Key 字段不存在于目标：" + key,
                        path
                );
                continue;
            }
            if (!mappedColumns.contains(key)) {
                issues.error(
                        "SNAPSHOT_SYNC_KEY_NOT_MAPPED",
                        "Key 字段必须被映射到目标：" + key,
                        path
                );
            }
            if (column.autoIncrement() || column.generated()
                    || column.fieldType() == PlatformDataType.GEOMETRY) {
                issues.error(
                        "SNAPSHOT_SYNC_KEY_COLUMN_NOT_ALLOWED",
                        "Key 不能是自增、生成或 Geometry 字段：" + key,
                        path
                );
            }
            if (column.nullable()) {
                issues.warning(
                        "SNAPSHOT_SYNC_NULLABLE_KEY",
                        "Key 字段元数据允许 NULL，真实 NULL 会在运行时阻止同步：" + key,
                        path
                );
            }
        }
        Set<String> configured = new HashSet<>(keys);
        boolean databaseUnique = uniqueKeys.stream().anyMatch(key ->
                key.columns().size() == keys.size() && new HashSet<>(key.columns()).equals(configured));
        if (!databaseUnique) {
            issues.warning(
                    "SNAPSHOT_SYNC_KEY_NOT_DATABASE_UNIQUE",
                    "所选 Key 不匹配目标表的主键或安全唯一索引，运行时将校验真实唯一性",
                    "configuration.keyColumns"
            );
        }
    }

    private static void validateDeletePolicy(
            SnapshotDeletePolicy policy,
            CanvasNodeIssueSink issues
    ) {
        if (policy == null || policy.action() == null) {
            issues.error(
                    "SNAPSHOT_SYNC_DELETE_POLICY_INVALID",
                    "请选择目标独有行处理策略",
                    "configuration.deletePolicy.action"
            );
            return;
        }
        if (policy.action() == SnapshotTargetOnlyAction.KEEP) {
            if (policy.maxDeleteRows() != null || policy.maxDeleteRatio() != null) {
                issues.error(
                        "SNAPSHOT_SYNC_DELETE_POLICY_INVALID",
                        "KEEP 模式不能保存删除阈值",
                        "configuration.deletePolicy"
                );
            }
            return;
        }
        if (policy.maxDeleteRows() == null || policy.maxDeleteRows() < 1) {
            issues.error(
                    "SNAPSHOT_SYNC_DELETE_POLICY_INVALID",
                    "DELETE 模式的最大删除数量必须是正整数",
                    "configuration.deletePolicy.maxDeleteRows"
            );
        }
        if (policy.maxDeleteRatio() == null
                || !Double.isFinite(policy.maxDeleteRatio())
                || policy.maxDeleteRatio() <= 0
                || policy.maxDeleteRatio() > 1) {
            issues.error(
                    "SNAPSHOT_SYNC_DELETE_POLICY_INVALID",
                    "DELETE 模式的最大删除比例必须位于 (0, 1]",
                    "configuration.deletePolicy.maxDeleteRatio"
            );
        }
    }
}
