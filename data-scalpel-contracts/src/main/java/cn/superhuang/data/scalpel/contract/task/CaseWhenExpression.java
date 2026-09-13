package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("按数组顺序判断 1 至 64 个结构化条件并返回首个为 true 的 result；全部未命中时返回 elseExpression，省略 elseExpression 时返回 SQL NULL。分支条件复用 FILTER 条件契约，结果类型由 Spark Analyzer 统一。")
public record CaseWhenExpression(
        @JsonPropertyDescription("必填的有序分支数组，1 至 64 项且每项 condition、result 都必须存在；先命中的分支遮蔽后续分支。")
        List<CaseWhenBranch> branches,
        @JsonPropertyDescription("可选的兜底结果表达式；所有条件均未命中且该值为 NULL 时，CASE 结果为 SQL NULL。")
        CanvasExpression elseExpression
) implements CanvasExpression {
    public CaseWhenExpression {
        branches = branches == null ? null : List.copyOf(branches);
    }
}
