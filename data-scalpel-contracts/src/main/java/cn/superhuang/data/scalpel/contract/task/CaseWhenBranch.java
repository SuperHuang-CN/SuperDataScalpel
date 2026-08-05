package cn.superhuang.data.scalpel.contract.task;

public record CaseWhenBranch(
        CanvasFilterCondition condition,
        CanvasExpression result
) {
}
