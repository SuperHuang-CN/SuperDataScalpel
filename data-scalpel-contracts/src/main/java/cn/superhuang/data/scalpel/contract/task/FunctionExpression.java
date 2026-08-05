package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record FunctionExpression(
        DeriveFunction function,
        List<CanvasExpression> arguments
) implements CanvasExpression {
    public FunctionExpression {
        arguments = arguments == null ? null : List.copyOf(arguments);
    }
}
