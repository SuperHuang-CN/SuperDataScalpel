package cn.superhuang.data.scalpel.dialect.query;

/** Runtime-specific guardrails applied by {@link StandardTableQueryCompiler}. */
public record StandardQueryLimits(
        int defaultPageSize,
        int maximumPageSize,
        int maximumFilterCount,
        int maximumInValues,
        int maximumOrderCount,
        int maximumGroupCount,
        int maximumAggregateCount,
        int maximumOffset
) {

    public StandardQueryLimits {
        if (defaultPageSize < 1 || maximumPageSize < defaultPageSize
                || maximumFilterCount < 0 || maximumInValues < 1 || maximumOrderCount < 0
                || maximumGroupCount < 0 || maximumAggregateCount < 0 || maximumOffset < 0) {
            throw new IllegalArgumentException("Invalid standard query limits");
        }
    }
}
