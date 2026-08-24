package cn.superhuang.data.scalpel.contract.task;

/** A trusted runtime value resolved by the Canvas Task Engine. */
public record RuntimeValueExpression(CanvasRuntimeValue value) implements CanvasExpression {
}
