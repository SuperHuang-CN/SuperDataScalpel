package cn.superhuang.data.scalpel.dialect.query;

import java.util.List;

/** A validated physical-column predicate. Values never originate from SQL text. */
public record QueryFilter(
        String column,
        QueryValueType valueType,
        QueryFilterOperator operator,
        List<Object> values
) {

    public QueryFilter {
        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException("Filter column is required");
        }
        if (valueType == null || operator == null) {
            throw new IllegalArgumentException("Filter type and operator are required");
        }
        values = values == null ? List.of() : List.copyOf(values);
    }
}
