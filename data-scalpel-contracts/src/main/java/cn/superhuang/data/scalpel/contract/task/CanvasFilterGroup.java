package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("使用必填的 AND 或 OR 递归组合一个或多个字段谓词或子条件组；空组不可执行。组本身也计入整棵树最多 256 个节点和 12 层深度的限制。")
public record CanvasFilterGroup(
        @JsonPropertyDescription("子条件组合方式：AND 要求全部成立，OR 要求至少一个成立。")
        FilterGroupOperator operator,
        @JsonPropertyDescription("递归子条件数组，至少一项且每项非 NULL；可包含字段谓词或嵌套组合组。")
        List<CanvasFilterCondition> children
) implements CanvasFilterCondition {
    public CanvasFilterGroup {
        children = children == null ? null : List.copyOf(children);
    }
}
