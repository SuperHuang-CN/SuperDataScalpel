package cn.superhuang.data.scalpel.contract.task;

public record MaskFieldRule(
        String fieldName,
        MaskingRuleSource ruleSource,
        MaskingSourceRuleReference sourceRuleRef,
        MaskingRuleDefinition definition
) {
}
