package cn.superhuang.data.scalpel.dialect.query;

/** SQL-free recursive predicate input before logical fields are resolved to physical columns. */
public sealed interface StandardQueryPredicateInput
        permits StandardQueryFilterInput, StandardQueryFilterGroupInput {
}
