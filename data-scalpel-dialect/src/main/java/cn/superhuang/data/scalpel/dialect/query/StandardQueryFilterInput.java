package cn.superhuang.data.scalpel.dialect.query;

/** Client-independent filter input that has not yet been resolved to a physical column. */
public record StandardQueryFilterInput(
        String field,
        QueryFilterOperator operator,
        Object value
) implements StandardQueryPredicateInput {
}
