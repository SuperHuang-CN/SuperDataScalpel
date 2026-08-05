package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record ValueMappingConfiguration(
        String sourceTableName,
        String outputTableName,
        List<ValueMappingRule> rules
) {
    public ValueMappingConfiguration {
        rules = rules == null ? null : List.copyOf(rules);
    }
}
