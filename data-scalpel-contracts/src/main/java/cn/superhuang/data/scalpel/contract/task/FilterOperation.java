package cn.superhuang.data.scalpel.contract.task;

public record FilterOperation(
        String operationId,
        String sourceTableName,
        ProcessorOutput output,
        FilterConditionMode mode,
        CanvasFilterCondition condition,
        String sqlExpression
) implements ProcessorOperation {

    public FilterOperation {
        mode = mode == null ? FilterConditionMode.STRUCTURED : mode;
        sqlExpression = sqlExpression == null ? "" : sqlExpression;
    }

    public FilterOperation(
            String operationId,
            String sourceTableName,
            ProcessorOutput output,
            CanvasFilterCondition condition
    ) {
        this(operationId, sourceTableName, output, FilterConditionMode.STRUCTURED, condition, "");
    }
}
