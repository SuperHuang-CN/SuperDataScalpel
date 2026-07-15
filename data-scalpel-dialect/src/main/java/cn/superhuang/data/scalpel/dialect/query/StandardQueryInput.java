package cn.superhuang.data.scalpel.dialect.query;

import java.util.List;

/**
 * Typed, SQL-free input to the standard table-query compiler.
 * Field names remain logical codes until the compiler validates them against the supplied whitelist.
 */
public record StandardQueryInput(
        Integer pageNo,
        Integer pageSize,
        ConditionConjunction conjunction,
        List<String> columns,
        List<StandardQueryFilterInput> filters,
        List<StandardQueryOrderInput> orders,
        List<String> groups,
        List<StandardQueryAggregateInput> aggregates,
        Boolean returnCount
) {

    public StandardQueryInput {
        columns = columns == null ? List.of() : List.copyOf(columns);
        filters = filters == null ? List.of() : List.copyOf(filters);
        orders = orders == null ? List.of() : List.copyOf(orders);
        groups = groups == null ? List.of() : List.copyOf(groups);
        aggregates = aggregates == null ? List.of() : List.copyOf(aggregates);
    }
}
