package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record CanvasFilterGroup(
        FilterGroupOperator operator,
        List<CanvasFilterCondition> children
) implements CanvasFilterCondition {
    public CanvasFilterGroup {
        children = children == null ? null : List.copyOf(children);
    }
}
