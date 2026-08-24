package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record MaskFieldsOperation(
        String operationId,
        String sourceTableName,
        ProcessorOutput output,
        List<MaskFieldRule> fieldRules
) implements ProcessorOperation {
    public MaskFieldsOperation {
        fieldRules = fieldRules == null ? null : List.copyOf(fieldRules);
    }
}
