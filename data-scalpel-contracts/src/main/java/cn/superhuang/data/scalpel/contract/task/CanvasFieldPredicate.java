package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record CanvasFieldPredicate(
        String columnName,
        FilterOperator operator,
        List<CanvasLiteral> values
) implements CanvasFilterCondition {
    public CanvasFieldPredicate {
        values = values == null ? null : List.copyOf(values);
    }
}
