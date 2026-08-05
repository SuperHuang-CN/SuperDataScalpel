package cn.superhuang.data.scalpel.contract.task;

public record BinaryExpression(
        DeriveBinaryOperator operator,
        CanvasExpression left,
        CanvasExpression right
) implements CanvasExpression {
}
