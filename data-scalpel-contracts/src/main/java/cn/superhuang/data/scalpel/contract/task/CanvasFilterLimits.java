package cn.superhuang.data.scalpel.contract.task;

public final class CanvasFilterLimits {
    public static final int MAX_DEPTH = 12;
    public static final int MAX_CONDITION_NODES = 256;
    public static final int MAX_VALUES_PER_PREDICATE = 100;
    public static final int MAX_SQL_EXPRESSION_LENGTH = FilterSqlExpressionPolicy.MAX_EXPRESSION_LENGTH;

    private CanvasFilterLimits() {
    }
}
