package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record CaseWhenExpression(
        List<CaseWhenBranch> branches,
        CanvasExpression elseExpression
) implements CanvasExpression {
    public CaseWhenExpression {
        branches = branches == null ? null : List.copyOf(branches);
    }
}
