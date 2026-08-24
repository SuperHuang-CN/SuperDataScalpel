package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record DeduplicateOperation(
        String operationId,
        String sourceTableName,
        ProcessorOutput output,
        List<String> keyColumns,
        DeduplicateKeepStrategy keepStrategy,
        List<SortField> orderBy
) implements ProcessorOperation {
    public DeduplicateOperation {
        keyColumns = keyColumns == null ? null : List.copyOf(keyColumns);
        orderBy = orderBy == null ? null : List.copyOf(orderBy);
    }
}
