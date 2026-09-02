package cn.superhuang.data.scalpel.dialect.query;

import java.util.List;

/**
 * Typed, SQL-free input to the standard table-query compiler.
 * Field names remain logical codes until the compiler validates them against the supplied whitelist.
 */
public record StandardQueryInput(
        Integer pageNo,
        Integer pageSize,
        List<String> fields,
        StandardQueryFilterGroupInput filter,
        List<StandardQueryOrderInput> sort,
        List<String> groupBy,
        List<StandardQueryAggregateInput> aggregates,
        Boolean returnCount
) {

    public StandardQueryInput {
        fields = fields == null ? List.of() : List.copyOf(fields);
        sort = sort == null ? List.of() : List.copyOf(sort);
        groupBy = groupBy == null ? List.of() : List.copyOf(groupBy);
        aggregates = aggregates == null ? List.of() : List.copyOf(aggregates);
    }
}
