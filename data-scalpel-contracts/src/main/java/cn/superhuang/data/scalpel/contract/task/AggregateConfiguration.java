package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record AggregateConfiguration(
        String sourceTableName,
        String outputTableName,
        List<String> groupByColumns,
        List<AggregateItem> aggregations
) {
    public AggregateConfiguration {
        groupByColumns = groupByColumns == null ? null : List.copyOf(groupByColumns);
        aggregations = aggregations == null ? null : List.copyOf(aggregations);
    }
}
