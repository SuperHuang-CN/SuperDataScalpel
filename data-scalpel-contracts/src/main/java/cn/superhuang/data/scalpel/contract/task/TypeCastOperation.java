package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record TypeCastOperation(
        String operationId,
        String sourceTableName,
        ProcessorOutput output,
        List<ColumnTypeCast> casts
) implements ProcessorOperation {
    public TypeCastOperation {
        casts = casts == null ? null : List.copyOf(casts);
    }
}
