package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.JdbcColumnMapping;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class OutputColumnMappingOperator {

    Dataset<Row> apply(
            SparkCanvasTable source,
            CanvasTableSchema target,
            List<JdbcColumnMapping> mappings,
            CanvasNodeIssueSink issues
    ) {
        return apply(source, target, mappings, null, null, issues);
    }

    Dataset<Row> applyPreserving(
            SparkCanvasTable source,
            CanvasTableSchema target,
            List<JdbcColumnMapping> mappings,
            String preservedSourceColumn,
            String preservedAlias,
            CanvasNodeIssueSink issues
    ) {
        if (CanvasNodeSupport.blank(preservedSourceColumn) || CanvasNodeSupport.blank(preservedAlias)) {
            throw new IllegalArgumentException("保留字段和别名不能为空");
        }
        return apply(source, target, mappings, preservedSourceColumn, preservedAlias, issues);
    }

    private static Dataset<Row> apply(
            SparkCanvasTable source,
            CanvasTableSchema target,
            List<JdbcColumnMapping> mappings,
            String preservedSourceColumn,
            String preservedAlias,
            CanvasNodeIssueSink issues
    ) {
        mappings = mappings == null ? List.of() : mappings;
        if (mappings.isEmpty()) {
            issues.error(
                    "REQUIRED_CONFIGURATION",
                    "至少需要配置一个字段映射",
                    "configuration.columnMappings"
            );
            return null;
        }
        Map<String, CanvasColumnSchema> sourceColumns = CanvasNodeSupport.columns(source.schema());
        Map<String, CanvasColumnSchema> targetColumns = CanvasNodeSupport.columns(target);
        Set<String> mappedTargets = new HashSet<>();
        Map<String, Column> selectionsByTarget = new java.util.LinkedHashMap<>();
        for (int index = 0; index < mappings.size(); index++) {
            JdbcColumnMapping mapping = mappings.get(index);
            String path = "configuration.columnMappings[" + index + "]";
            if (mapping == null
                    || CanvasNodeSupport.blank(mapping.sourceColumnName())
                    || CanvasNodeSupport.blank(mapping.targetColumnName())) {
                issues.error("REQUIRED_CONFIGURATION", "字段映射不完整", path);
                continue;
            }
            if (!mappedTargets.add(mapping.targetColumnName())) {
                issues.error(
                        "DUPLICATE_TARGET_COLUMN_MAPPING",
                        "目标字段被重复映射：" + mapping.targetColumnName(),
                        path
                );
            }
            CanvasColumnSchema sourceColumn = sourceColumns.get(mapping.sourceColumnName());
            CanvasColumnSchema targetColumn = targetColumns.get(mapping.targetColumnName());
            if (sourceColumn == null) {
                issues.error("COLUMN_NOT_FOUND", "来源字段不存在：" + mapping.sourceColumnName(), path);
            }
            if (targetColumn == null) {
                issues.error("COLUMN_NOT_FOUND", "目标字段不存在：" + mapping.targetColumnName(), path);
            } else if (targetColumn.autoIncrement() || targetColumn.generated()) {
                issues.error(
                        "TARGET_COLUMN_NOT_WRITABLE",
                        "自增或生成字段不能配置映射：" + mapping.targetColumnName(),
                        path
                );
            }
            if (sourceColumn != null && targetColumn != null
                    && !targetColumn.autoIncrement() && !targetColumn.generated()) {
                Column mapped = mappedColumn(source, sourceColumn, targetColumn, path, issues);
                if (mapped != null) {
                    selectionsByTarget.putIfAbsent(targetColumn.name(), mapped);
                }
            }
        }
        for (CanvasColumnSchema targetColumn : requiredColumns(target)) {
            if (!mappedTargets.contains(targetColumn.name())) {
                issues.error(
                        "REQUIRED_TARGET_COLUMN_MISSING",
                        "目标必填字段未配置映射：" + targetColumn.name(),
                        "configuration.columnMappings"
                );
            }
        }
        List<Column> selections = target.columns().stream()
                .map(column -> selectionsByTarget.get(column.name()))
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        addPreservedSelection(source, preservedSourceColumn, preservedAlias, selections, issues);
        return select(source, selections, issues);
    }

    private static void addPreservedSelection(
            SparkCanvasTable source,
            String sourceColumnName,
            String alias,
            List<Column> selections,
            CanvasNodeIssueSink issues
    ) {
        if (sourceColumnName == null) {
            return;
        }
        if (CanvasNodeSupport.columns(source.schema()).get(sourceColumnName) == null) {
            issues.error("COLUMN_NOT_FOUND", "来源字段不存在：" + sourceColumnName, "configuration.keyColumnName");
            return;
        }
        selections.add(source.dataset()
                .col(CanvasNodeSupport.quoteIdentifier(sourceColumnName))
                .cast("string")
                .as(alias));
    }

    private static Column mappedColumn(
            SparkCanvasTable sourceTable,
            CanvasColumnSchema source,
            CanvasColumnSchema target,
            String path,
            CanvasNodeIssueSink issues
    ) {
        Column sourceColumn = sourceTable.dataset().col(CanvasNodeSupport.quoteIdentifier(source.name()));
        if (source.fieldType() == PlatformDataType.GEOMETRY
                || target.fieldType() == PlatformDataType.GEOMETRY) {
            if (source.fieldType() != PlatformDataType.GEOMETRY
                    || target.fieldType() != PlatformDataType.GEOMETRY) {
                issues.error(
                        "GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                        "Geometry 字段不能与标量字段互相映射",
                        path
                );
                return null;
            }
            if (!Objects.equals(source.geometry(), target.geometry())) {
                issues.error(
                        "SPATIAL_SCHEMA_MISMATCH",
                        "Geometry 来源与目标的类型、CRS 或维度不一致",
                        path
                );
                return null;
            }
            return sourceColumn.as(target.name());
        }
        Column converted = sourceColumn;
        if (OutputTypeConversionPolicy.needsSparkCast(source, target)) {
            converted = sourceColumn.cast(SparkTypeMapper.toDataType(target));
            try {
                sourceTable.dataset().select(converted.as(target.name())).schema();
            } catch (Exception exception) {
                issues.error(
                        "UNSUPPORTED_COLUMN_CAST",
                        "Spark 不支持字段 %s (%s) 转换为 %s (%s)".formatted(
                                source.name(), source.fieldType(), target.name(), target.fieldType()),
                        path
                );
                return null;
            }
        }
        addRisks(source, target, path, issues);
        return converted.as(target.name());
    }

    private static void addRisks(
            CanvasColumnSchema source,
            CanvasColumnSchema target,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (source.fieldType() != target.fieldType()
                && OutputTypeConversionPolicy.assignment(source, target)
                == OutputTypeConversionPolicy.ConversionRisk.RISKY) {
            issues.warning(
                    "COLUMN_CAST_RISK",
                    "字段 %s (%s) 转换为 %s (%s) 可能因实际数据失败".formatted(
                            source.name(), source.fieldType(), target.name(), target.fieldType()),
                    path
            );
        }
        if (source.nullable() && !target.nullable()) {
            issues.warning(
                    "NULLABILITY_RISK",
                    "来源字段可能为空，但目标字段不允许为空：" + target.name(),
                    path
            );
        }
        if (source.fieldType() == PlatformDataType.STRING
                && target.fieldType() == PlatformDataType.STRING
                && target.length() != null
                && (source.length() == null || source.length() > target.length())) {
            issues.warning(
                    "STRING_LENGTH_RISK",
                    "来源字符串可能超过目标长度：" + target.name() + "(" + target.length() + ")",
                    path
            );
        }
        if (source.fieldType() == PlatformDataType.DECIMAL
                && target.fieldType() == PlatformDataType.DECIMAL
                && OutputTypeConversionPolicy.assignment(source, target)
                == OutputTypeConversionPolicy.ConversionRisk.RISKY) {
            issues.warning(
                    "DECIMAL_PRECISION_RISK",
                    "Decimal 字段写入目标精度时可能溢出或舍入：" + target.name(),
                    path
            );
        }
    }

    private static Dataset<Row> select(
            SparkCanvasTable source,
            List<Column> selections,
            CanvasNodeIssueSink issues
    ) {
        if (issues.hasErrors()) {
            return null;
        }
        Dataset<Row> selected = source.dataset().select(selections.toArray(Column[]::new));
        selected.schema();
        return selected;
    }

    private static List<CanvasColumnSchema> writableColumns(CanvasTableSchema table) {
        return table.columns().stream()
                .filter(column -> !column.autoIncrement() && !column.generated())
                .toList();
    }

    private static List<CanvasColumnSchema> requiredColumns(CanvasTableSchema table) {
        return writableColumns(table).stream()
                .filter(column -> !column.nullable() && column.defaultValue() == null)
                .toList();
    }
}
