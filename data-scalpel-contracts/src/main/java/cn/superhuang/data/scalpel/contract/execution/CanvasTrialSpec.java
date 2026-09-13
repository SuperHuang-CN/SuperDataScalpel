package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Immutable target selection carried only by a Canvas trial-run Manifest. */
@JsonClassDescription("Canvas 节点试运行的目标表和字段投影；只随试运行 Manifest 使用，不改变已保存任务定义。")
public record CanvasTrialSpec(
        @JsonPropertyDescription("需要实际执行并预览的目标节点 UUID 字符串；上游依赖节点会随执行计划一并运行。")
        String targetNodeId,
        @JsonPropertyDescription("目标节点输出中需要预览的逻辑表名，长度不超过 255。")
        String tableName,
        @JsonPropertyDescription("需要投影到试运行预览的唯一字段名列表；必须包含 1 至 4096 项，每项长度不超过 255。")
        List<String> columnNames
) {
    public static final int MAX_COLUMNS = 4096;

    public CanvasTrialSpec {
        columnNames = columnNames == null ? List.of() : List.copyOf(columnNames);
        Set<String> uniqueColumns = new HashSet<>();
        if (!uuid(targetNodeId) || invalid(tableName, 255)
                || columnNames.isEmpty() || columnNames.size() > MAX_COLUMNS
                || columnNames.stream().anyMatch(column -> invalid(column, 255) || !uniqueColumns.add(column))) {
            throw new IllegalArgumentException("Canvas 试运行目标配置无效");
        }
    }

    private static boolean uuid(String value) {
        try {
            return value != null && value.equals(UUID.fromString(value).toString());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean invalid(String value, int maximumLength) {
        return value == null || value.isBlank() || value.length() > maximumLength;
    }
}
