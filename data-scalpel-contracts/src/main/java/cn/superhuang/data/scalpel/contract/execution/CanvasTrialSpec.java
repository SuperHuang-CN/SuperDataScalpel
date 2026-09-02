package cn.superhuang.data.scalpel.contract.execution;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Immutable target selection carried only by a Canvas trial-run Manifest. */
public record CanvasTrialSpec(
        String targetNodeId,
        String tableName,
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
