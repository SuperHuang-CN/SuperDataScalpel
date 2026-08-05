package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record SpatialAggregateConfiguration(
        String sourceTableName,
        String outputTableName,
        List<String> groupByColumns,
        List<SpatialAggregation> aggregations
) {
    public static final int MAX_AGGREGATIONS = 32;

    public SpatialAggregateConfiguration {
        groupByColumns = groupByColumns == null ? null : List.copyOf(groupByColumns);
        aggregations = aggregations == null ? null : List.copyOf(aggregations);
    }
}
