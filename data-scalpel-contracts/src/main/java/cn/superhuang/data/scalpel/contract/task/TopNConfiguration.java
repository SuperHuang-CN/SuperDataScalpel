package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record TopNConfiguration(
        String sourceTableName,
        String outputTableName,
        List<String> partitionByColumns,
        List<SortField> orderBy,
        int limit,
        TopNTieStrategy tieStrategy
) {
    public TopNConfiguration {
        partitionByColumns = partitionByColumns == null ? null : List.copyOf(partitionByColumns);
        orderBy = orderBy == null ? null : List.copyOf(orderBy);
    }
}
