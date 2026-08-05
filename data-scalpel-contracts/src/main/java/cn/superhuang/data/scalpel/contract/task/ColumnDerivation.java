package cn.superhuang.data.scalpel.contract.task;

public record ColumnDerivation(
        String targetColumnName,
        CanvasExpression expression,
        boolean replaceExisting
) {
}
