package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("成功的用户 Spark JAR 试运行预览；按调用顺序汇总被拦截的受控写入及非阻断告警。")
public record SparkJarTrialPreview(
        @JsonPropertyDescription("Spark JAR 试运行中按调用顺序收集的受控写入预览，始终为数组，最多 20 项；试运行拦截写入，不向正式目标提交数据。")
        List<WritePreview> writes,
        @JsonPropertyDescription("非阻断告警列表，始终为数组，最多 100 项。")
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

    @JsonClassDescription("用户 Spark JAR 试运行中一次被拦截写入的有界预览；描述目标、写入模式、Schema 和样例行。")
    public record WritePreview(
            @JsonPropertyDescription("写入预览在本次作业中的调用顺序，从 1 开始。")
            int index,
            @JsonPropertyDescription("试运行写入目标的平台资源类型。")
            ResourceKind resourceKind,
            @JsonPropertyDescription("用户 JAR 使用的资源绑定名。")
            String bindingName,
            @JsonPropertyDescription("经过安全规范化的写入目标说明，不含凭据。")
            String target,
            @JsonPropertyDescription("目标写入模式。")
            String writeMode,
            @JsonPropertyDescription("写入 Dataset 的 Spark StructType JSON。")
            String schemaJson,
            @JsonPropertyDescription("有界预览行，始终为数组；每项是需要再次解析的 JSON 对象字符串，不是嵌套 JSON 对象，每次写入最多 100 行。")
            List<String> rowsJson,
            @JsonPropertyDescription("结果是否因数量或大小上限被截断。")
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

    @JsonClassDescription("被试运行拦截的写入目标类型：MODEL 模型，JDBC 物理表，KAFKA 主题。")
    public enum ResourceKind {
        MODEL,
        JDBC,
        KAFKA
    }

    private static boolean invalid(String value, int maximumLength) {
        return value == null || value.isBlank() || value.length() > maximumLength;
    }
}
