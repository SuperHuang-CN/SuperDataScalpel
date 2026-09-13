package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("调用 DeriveFunction 白名单中的 Spark 函数；参数表达式按数组顺序求值，数量须符合固定签名。函数不接受任意名称或 SQL，参数类型和结果类型最终由 Spark Analyzer 判断。")
public record FunctionExpression(
        @JsonPropertyDescription("必填白名单函数；可选值只包括字符串处理、COALESCE/CONCAT 及 DATE_FORMAT/DATE_ADD/DATE_SUB。")
        DeriveFunction function,
        @JsonPropertyDescription("非 NULL 的有序参数数组。TRIM/LTRIM/RTRIM/LOWER/UPPER 为 1 项，REPLACE/SUBSTRING 为 3 项，COALESCE/CONCAT 至少 2 项，DATE_FORMAT/DATE_ADD/DATE_SUB 为 2 项；日期函数第二项还有 Literal 限制。")
        List<CanvasExpression> arguments
) implements CanvasExpression {
    public FunctionExpression {
        arguments = arguments == null ? null : List.copyOf(arguments);
    }
}
