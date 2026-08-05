package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record MaskFieldsConfiguration(
        String sourceTableName,
        String outputTableName,
        List<MaskFieldRule> fieldRules
) {
    public MaskFieldsConfiguration {
        fieldRules = fieldRules == null ? null : List.copyOf(fieldRules);
    }
}
