package cn.superhuang.data.scalpel.contract.service;

import jakarta.validation.Valid;

import java.util.List;

/** Fixed public V1 request body for every standard table service. */
public record StandardServiceQueryRequest(
        Integer pageNo,
        Integer pageSize,
        ConditionType conditionType,
        List<String> columns,
        List<@Valid StandardFilter> filters,
        List<@Valid StandardOrder> orders,
        List<String> groups,
        List<@Valid StandardAggregator> aggregators,
        Boolean returnCount
) {

    public StandardServiceQueryRequest {
        columns = columns == null ? List.of() : List.copyOf(columns);
        filters = filters == null ? List.of() : List.copyOf(filters);
        orders = orders == null ? List.of() : List.copyOf(orders);
        groups = groups == null ? List.of() : List.copyOf(groups);
        aggregators = aggregators == null ? List.of() : List.copyOf(aggregators);
    }
}
