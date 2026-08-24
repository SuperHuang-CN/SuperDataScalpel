package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record TopNOperation(
        String operationId,
        String sourceTableName,
        ProcessorOutput output,
        List<String> partitionByColumns,
        List<SortField> orderBy,
        int limit,
        TopNTieStrategy tieStrategy
) implements ProcessorOperation {
    public TopNOperation {
        partitionByColumns = partitionByColumns == null ? null : List.copyOf(partitionByColumns);
        orderBy = orderBy == null ? null : List.copyOf(orderBy);
    }
}
