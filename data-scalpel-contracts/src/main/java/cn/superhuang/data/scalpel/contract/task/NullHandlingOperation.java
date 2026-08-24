package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record NullHandlingOperation(
        String operationId,
        String sourceTableName,
        ProcessorOutput output,
        List<NullHandlingRule> rules
) implements ProcessorOperation {
    public NullHandlingOperation {
        rules = rules == null ? null : List.copyOf(rules);
    }
}
