package cn.superhuang.data.scalpel.dialect.query;

/** Client-independent order input that has not yet been resolved to a physical column. */
public record StandardQueryOrderInput(String field, QuerySortDirection direction) {
}
