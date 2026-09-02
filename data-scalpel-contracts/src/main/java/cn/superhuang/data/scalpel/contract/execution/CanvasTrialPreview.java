package cn.superhuang.data.scalpel.contract.execution;

import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;

import java.util.List;
import java.nio.charset.StandardCharsets;

/** Selected rows produced by a successful Canvas trial run. */
public record CanvasTrialPreview(
        String targetNodeId,
        String targetNodeName,
        CanvasTableSchema tableSchema,
        List<String> rowsJson,
        boolean truncated,
        List<String> warnings
) {
    public static final int MAX_ROWS = 100;
    public static final int MAX_CONTENT_CHARACTERS = 4 * 1024 * 1024;

    public CanvasTrialPreview {
        rowsJson = rowsJson == null ? List.of() : List.copyOf(rowsJson);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        long contentBytes = rowsJson.stream()
                .filter(java.util.Objects::nonNull)
                .mapToLong(value -> value.getBytes(StandardCharsets.UTF_8).length)
                .sum();
        if (invalid(targetNodeId, 100) || invalid(targetNodeName, 100)
                || tableSchema == null || invalid(tableSchema.name(), 255)
                || tableSchema.columns() == null || tableSchema.columns().isEmpty()
                || rowsJson.size() > MAX_ROWS || contentBytes > MAX_CONTENT_CHARACTERS
                || rowsJson.stream().anyMatch(value -> value == null)
                || warnings.size() > 20
                || warnings.stream().anyMatch(value -> invalid(value, 1000))) {
            throw new IllegalArgumentException("Canvas 试运行预览无效");
        }
    }

    private static boolean invalid(String value, int maximumLength) {
        return value == null || value.isBlank() || value.length() > maximumLength;
    }
}
