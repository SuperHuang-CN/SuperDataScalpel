package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.JoinOutputColumn;
import cn.superhuang.data.scalpel.contract.task.JoinOutputColumnSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class JoinOutputColumnSupport {

    private JoinOutputColumnSupport() {
    }

    static List<ResolvedOutputColumn> validate(
            List<JoinOutputColumn> configuredColumns,
            Map<String, CanvasColumnSchema> leftColumns,
            Map<String, CanvasColumnSchema> rightColumns,
            CanvasNodeIssueSink issues
    ) {
        if (configuredColumns == null || configuredColumns.isEmpty()) {
            issues.error(
                    "JOIN_OUTPUT_COLUMNS_REQUIRED",
                    "请配置 Join 输出字段",
                    "configuration.outputColumns"
            );
            return List.of();
        }
        Set<String> sourceKeys = new HashSet<>();
        Set<String> outputNames = new HashSet<>();
        List<ResolvedOutputColumn> resolved = new ArrayList<>();
        for (int index = 0; index < configuredColumns.size(); index++) {
            JoinOutputColumn outputColumn = configuredColumns.get(index);
            String path = "configuration.outputColumns[" + index + "]";
            if (outputColumn == null || outputColumn.sourceSide() == null
                    || CanvasNodeSupport.blank(outputColumn.sourceColumnName())) {
                issues.error("REQUIRED_CONFIGURATION", "Join 输出字段来源不完整", path);
                continue;
            }
            String sourceKey = outputColumn.sourceSide() + "\u0000" + outputColumn.sourceColumnName();
            if (!sourceKeys.add(sourceKey)) {
                issues.error(
                        "DUPLICATE_JOIN_OUTPUT_SOURCE",
                        "同一来源字段只能配置一次",
                        path
                );
            }
            Map<String, CanvasColumnSchema> sourceColumns =
                    outputColumn.sourceSide() == JoinOutputColumnSource.LEFT
                            ? leftColumns : rightColumns;
            CanvasColumnSchema sourceColumn = sourceColumns.get(outputColumn.sourceColumnName());
            if (sourceColumn == null) {
                issues.error(
                        "COLUMN_NOT_FOUND",
                        "Join 输出来源字段不存在：" + outputColumn.sourceColumnName(),
                        path + ".sourceColumnName"
                );
                continue;
            }
            if (!outputColumn.included()) {
                continue;
            }
            if (CanvasNodeSupport.blank(outputColumn.outputColumnName())) {
                issues.error(
                        "REQUIRED_CONFIGURATION",
                        "请输入 Join 输出字段名",
                        path + ".outputColumnName"
                );
                continue;
            }
            String normalizedOutputName = outputColumn.outputColumnName().toLowerCase(Locale.ROOT);
            if (!outputNames.add(normalizedOutputName)) {
                issues.error(
                        "DUPLICATE_COLUMN_NAME",
                        "Join 输出字段名重复：" + outputColumn.outputColumnName(),
                        path + ".outputColumnName"
                );
                continue;
            }
            resolved.add(new ResolvedOutputColumn(
                    outputColumn.sourceSide(), sourceColumn, outputColumn.outputColumnName()));
        }
        if (resolved.isEmpty()) {
            issues.error(
                    "JOIN_OUTPUT_COLUMNS_REQUIRED",
                    "Join 至少需要输出一个字段",
                    "configuration.outputColumns"
            );
        }
        return List.copyOf(resolved);
    }

    static CanvasColumnSchema copyWithName(CanvasColumnSchema source, String name) {
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

    record ResolvedOutputColumn(
            JoinOutputColumnSource sourceSide,
            CanvasColumnSchema sourceColumn,
            String outputColumnName
    ) {
    }
}
