package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.NullOrdering;
import cn.superhuang.data.scalpel.contract.task.SortDirection;
import cn.superhuang.data.scalpel.contract.task.SortField;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CanvasSortSupport {

    private CanvasSortSupport() {
    }

    static void validate(
            List<SortField> fields,
            SparkCanvasTable source,
            CanvasNodeIssueSink issues,
            String path,
            String emptyCode,
            String duplicateCode
    ) {
        if (fields == null) {
            issues.error("REQUIRED_CONFIGURATION", "排序规则必须是数组", path);
            return;
        }
        if (fields.isEmpty()) {
            issues.error(emptyCode, "至少配置一个排序字段", path);
        }
        Map<String, CanvasColumnSchema> columns = source == null
                ? Map.of() : CanvasNodeSupport.columns(source.schema());
        Set<String> names = new HashSet<>();
        for (int index = 0; index < fields.size(); index++) {
            SortField field = fields.get(index);
            String itemPath = path + "[" + index + "]";
            if (field == null) {
                issues.error("REQUIRED_CONFIGURATION", "排序项不能为空", itemPath);
                continue;
            }
            CanvasNodeSupport.required(
                    field.columnName(),
                    "请选择排序字段",
                    itemPath + ".columnName",
                    issues
            );
            if (!CanvasNodeSupport.blank(field.columnName()) && !names.add(field.columnName())) {
                issues.error(
                        duplicateCode,
                        "排序字段重复：" + field.columnName(),
                        itemPath + ".columnName"
                );
            }
            if (source != null
                    && !CanvasNodeSupport.blank(field.columnName())
                    && !columns.containsKey(field.columnName())) {
                issues.error(
                        "COLUMN_NOT_FOUND",
                        "排序字段不存在：" + field.columnName(),
                        itemPath + ".columnName"
                );
            } else if (columns.get(field.columnName()) != null
                    && columns.get(field.columnName()).fieldType()
                    == cn.superhuang.data.scalpel.contract.type.PlatformDataType.GEOMETRY) {
                issues.error(
                        "GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                        "Geometry 字段不能参与排序",
                        itemPath + ".columnName"
                );
            }
            if (field.direction() == null) {
                issues.error(
                        "INVALID_SORT_DIRECTION",
                        "请选择排序方向",
                        itemPath + ".direction"
                );
            }
            if (field.nullOrdering() == null) {
                issues.error(
                        "INVALID_NULL_ORDERING",
                        "请选择 NULL 排序位置",
                        itemPath + ".nullOrdering"
                );
            }
        }
    }

    static Column[] expressions(Dataset<Row> dataset, List<SortField> fields) {
        return fields.stream()
                .map(field -> expression(dataset, field))
                .toArray(Column[]::new);
    }

    static String temporaryColumnName(Set<String> existing, String base) {
        String candidate = base;
        int suffix = 1;
        while (existing.contains(candidate)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    private static Column expression(Dataset<Row> dataset, SortField field) {
        Column column = dataset.col(CanvasNodeSupport.quoteIdentifier(field.columnName()));
        if (field.direction() == SortDirection.ASC) {
            return field.nullOrdering() == NullOrdering.FIRST
                    ? column.asc_nulls_first() : column.asc_nulls_last();
        }
        return field.nullOrdering() == NullOrdering.FIRST
                ? column.desc_nulls_first() : column.desc_nulls_last();
    }
}
