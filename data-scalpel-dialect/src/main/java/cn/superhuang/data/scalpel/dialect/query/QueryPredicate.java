package cn.superhuang.data.scalpel.dialect.query;

/** Validated recursive WHERE predicate with trusted physical identifiers. */
public sealed interface QueryPredicate permits QueryFilter, QueryFilterGroup {
}
