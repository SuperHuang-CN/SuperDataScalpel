package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record RenameOperation(
        String operationId,
        String sourceTableName,
        ProcessorOutput output,
        List<RenameColumnMapping> columnMappings
) implements ProcessorOperation {
    public RenameOperation {
        columnMappings = columnMappings == null ? null : List.copyOf(columnMappings);
    }
}
