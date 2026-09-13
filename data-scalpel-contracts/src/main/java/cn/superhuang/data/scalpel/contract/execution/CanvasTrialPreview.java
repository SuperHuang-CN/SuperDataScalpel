package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;

import java.util.List;
import java.nio.charset.StandardCharsets;

/** Selected rows produced by a successful Canvas trial run. */
@JsonClassDescription("成功的 Canvas 试运行在目标节点产生的有界数据预览；只返回选取行、Schema 和告警，不代表完整结果集。")
public record CanvasTrialPreview(
        @JsonPropertyDescription("本次试运行的目标节点规范 UUID 字符串，与请求 targetNodeId 一致。")
        String targetNodeId,
        @JsonPropertyDescription("Canvas 试运行目标节点的用户可读名称。")
        String targetNodeName,
        @JsonPropertyDescription("目标节点所选逻辑表的字段 Schema，只包含本次投影列并保持请求顺序。")
        CanvasTableSchema tableSchema,
        @JsonPropertyDescription("有界预览行，始终为数组；每项是需要再次解析的 JSON 对象字符串，不是嵌套 JSON 对象。最多 100 行，所有行 UTF-8 合计不超过 4 MiB。")
        List<String> rowsJson,
        @JsonPropertyDescription("结果是否因数量或大小上限被截断。")
        boolean truncated,
        @JsonPropertyDescription("非阻断告警列表，始终为数组，最多 20 项。")
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
