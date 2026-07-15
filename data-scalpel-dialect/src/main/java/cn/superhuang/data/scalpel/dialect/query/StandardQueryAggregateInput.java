package cn.superhuang.data.scalpel.dialect.query;

/** Client-independent aggregate input that has not yet been resolved to a physical column. */
public record StandardQueryAggregateInput(AggregateFunction function, String field, String alias) {
}
