package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record DeduplicateConfiguration(
        String sourceTableName,
        String outputTableName,
        List<String> keyColumns,
        DeduplicateKeepStrategy keepStrategy,
        List<SortField> orderBy
) {
    public DeduplicateConfiguration {
        keyColumns = keyColumns == null ? null : List.copyOf(keyColumns);
        orderBy = orderBy == null ? null : List.copyOf(orderBy);
    }
}
