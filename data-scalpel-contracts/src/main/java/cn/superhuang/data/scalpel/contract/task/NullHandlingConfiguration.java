package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record NullHandlingConfiguration(
        String sourceTableName,
        String outputTableName,
        List<NullHandlingRule> rules
) {
    public NullHandlingConfiguration {
        rules = rules == null ? null : List.copyOf(rules);
    }
}
