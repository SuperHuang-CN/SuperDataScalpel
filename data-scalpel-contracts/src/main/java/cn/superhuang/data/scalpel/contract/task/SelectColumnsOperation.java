package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record SelectColumnsOperation(
        String operationId,
        String sourceTableName,
        ProcessorOutput output,
        List<String> columns
) implements ProcessorOperation {
    public SelectColumnsOperation {
        columns = columns == null ? null : List.copyOf(columns);
    }
}
