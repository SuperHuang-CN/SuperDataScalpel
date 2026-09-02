package cn.superhuang.data.scalpel.contract.execution;

import java.util.List;

public record SparkJarTrialPreview(
        List<WritePreview> writes,
        List<String> warnings
) {
    public static final int MAX_WRITES = 20;
    public static final int MAX_ROWS_PER_WRITE = 100;

    public SparkJarTrialPreview {
        writes = writes == null ? List.of() : List.copyOf(writes);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        if (writes.size() > MAX_WRITES || warnings.size() > 100
                || writes.stream().anyMatch(value -> value == null)
                || warnings.stream().anyMatch(value -> invalid(value, 1000))) {
            throw new IllegalArgumentException("Spark JAR 试运行预览无效");
        }
    }

    public record WritePreview(
            int index,
            ResourceKind resourceKind,
            String bindingName,
            String target,
            String writeMode,
            String schemaJson,
            List<String> rowsJson,
            boolean truncated
    ) {
        public WritePreview {
            rowsJson = rowsJson == null ? List.of() : List.copyOf(rowsJson);
            if (index < 1 || resourceKind == null || invalid(bindingName, 100)
                    || invalid(target, 1000) || invalid(writeMode, 50)
                    || invalid(schemaJson, 256 * 1024) || rowsJson.size() > MAX_ROWS_PER_WRITE
                    || rowsJson.stream().anyMatch(value -> invalid(value, 256 * 1024))) {
                throw new IllegalArgumentException("Spark JAR 试运行写入预览无效");
            }
        }
    }

    public enum ResourceKind {
        MODEL,
        JDBC,
        KAFKA
    }

    private static boolean invalid(String value, int maximumLength) {
        return value == null || value.isBlank() || value.length() > maximumLength;
    }
}
