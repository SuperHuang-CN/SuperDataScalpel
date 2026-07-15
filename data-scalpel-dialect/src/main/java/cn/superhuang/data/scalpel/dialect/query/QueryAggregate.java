package cn.superhuang.data.scalpel.dialect.query;

/** A validated aggregate. {@code *} is permitted only for COUNT. */
public record QueryAggregate(AggregateFunction function, String column, String alias) {

    public QueryAggregate {
        if (function == null || column == null || column.isBlank() || alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("Aggregate function, column and alias are required");
        }
        if ("*".equals(column) && function != AggregateFunction.COUNT) {
            throw new IllegalArgumentException("Only COUNT supports *");
        }
    }
}
