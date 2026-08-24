package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record ValueMappingOperation(
        String operationId,
        String sourceTableName,
        ProcessorOutput output,
        List<ValueMappingRule> rules
) implements ProcessorOperation {
    public ValueMappingOperation {
        rules = rules == null ? null : List.copyOf(rules);
    }
}
