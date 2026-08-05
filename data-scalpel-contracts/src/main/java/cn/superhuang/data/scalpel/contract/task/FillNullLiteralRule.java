package cn.superhuang.data.scalpel.contract.task;

public record FillNullLiteralRule(
        String columnName,
        CanvasLiteral value
) implements NullHandlingRule {
}
