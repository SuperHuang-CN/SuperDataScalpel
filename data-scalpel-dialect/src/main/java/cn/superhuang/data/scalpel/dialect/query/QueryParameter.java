package cn.superhuang.data.scalpel.dialect.query;

/** A JDBC binding in the exact order of its placeholder. */
public record QueryParameter(Object value, QueryValueType type) {
}
