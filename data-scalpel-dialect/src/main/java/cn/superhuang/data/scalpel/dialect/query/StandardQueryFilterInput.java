package cn.superhuang.data.scalpel.dialect.query;

import java.util.List;

/** Client-independent filter input that has not yet been resolved to a physical column. */
public record StandardQueryFilterInput(
        String field,
        QueryFilterOperator operator,
        Object value,
        Object secondValue,
        List<Object> values
) {

    public StandardQueryFilterInput {
        values = values == null ? List.of() : List.copyOf(values);
    }
}
