package cn.superhuang.data.scalpel.contract.service;

import jakarta.validation.Valid;

import java.util.List;

/** Fixed public V1 request body for every standard table service. */
public record StandardServiceQueryRequest(
        Integer pageNo,
        Integer pageSize,
        List<String> fields,
        @Valid StandardFilterNode filter,
        List<@Valid StandardOrder> sort,
        List<String> groupBy,
        List<@Valid StandardAggregator> aggregates,
        Boolean returnCount
) {

    public StandardServiceQueryRequest {
        fields = fields == null ? List.of() : List.copyOf(fields);
        sort = sort == null ? List.of() : List.copyOf(sort);
        groupBy = groupBy == null ? List.of() : List.copyOf(groupBy);
        aggregates = aggregates == null ? List.of() : List.copyOf(aggregates);
    }
}
