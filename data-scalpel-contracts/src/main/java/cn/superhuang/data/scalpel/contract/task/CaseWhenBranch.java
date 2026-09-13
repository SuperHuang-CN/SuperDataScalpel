package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("CASE_WHEN 中一个必填条件与结果分支；按数组顺序判断，condition 为 true 时返回 result 并停止匹配后续分支。")
public record CaseWhenBranch(
        @JsonPropertyDescription("必填的结构化 FILTER 条件树，只能引用当前操作原始来源字段；SQL 表达式模式不用于 CASE 分支。")
        CanvasFilterCondition condition,
        @JsonPropertyDescription("必填的命中结果表达式；其类型必须能与其他分支及 elseExpression 由 Spark Analyzer 统一。")
        CanvasExpression result
) {
}
