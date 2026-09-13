package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/**
 * Stable executable masking definition shared by rule management and Canvas.
 *
 * <p>Only parameters used by the selected strategy may be populated.</p>
 */
@JsonClassDescription("字段脱敏的完整可执行定义；strategy 决定唯一允许的参数组合，未使用参数必须为 NULL。掩码字符 NULL 规范化为 *，POSITION_MASK 的位置 NULL 规范化为 2；这些规范化默认值不改变其他参数的禁用规则。")
public record MaskingRuleDefinition(
        @JsonPropertyDescription("脱敏策略：PARTIAL_MASK 保留两端、POSITION_MASK 替换指定位置、KEEP_LENGTH_MASK 等长全掩码、FIXED_VALUE 固定替换、NULLIFY 置空。")
        MaskingStrategy strategy,
        @JsonPropertyDescription("PARTIAL_MASK 保留的前缀 Unicode 字符数，范围 0 到 1024；其他策略必须为空。")
        Integer keepPrefixLength,
        @JsonPropertyDescription("PARTIAL_MASK 保留的后缀 Unicode 字符数，范围 0 到 1024；其他策略必须为空。")
        Integer keepSuffixLength,
        @JsonPropertyDescription("POSITION_MASK 要替换的从 1 开始计数的 Unicode 字符位置，NULL 默认 2，显式值范围 1 到 1024；字符串不足该长度时保持原值，其他策略必须为空。")
        Integer maskPosition,
        @JsonPropertyDescription("PARTIAL_MASK、POSITION_MASK 或 KEEP_LENGTH_MASK 使用的单个 Unicode 字符，NULL 规范化为 *；FIXED_VALUE 和 NULLIFY 时必须为空。")
        String maskCharacter,
        @JsonPropertyDescription("FIXED_VALUE 必填并写入的固定替换字符串，允许空字符串，按 Java String.length 计算最长 1024 个 UTF-16 代码单元；其他策略必须为空。")
        String fixedValue
) {
    public MaskingRuleDefinition canonical() {
        if (strategy == null) {
            return this;
        }
        return switch (strategy) {
            case PARTIAL_MASK -> new MaskingRuleDefinition(
                    strategy,
                    keepPrefixLength,
                    keepSuffixLength,
                    null,
                    maskCharacter == null ? "*" : maskCharacter,
                    null
            );
            case POSITION_MASK -> new MaskingRuleDefinition(
                    strategy,
                    null,
                    null,
                    maskPosition == null ? 2 : maskPosition,
                    maskCharacter == null ? "*" : maskCharacter,
                    null
            );
            case KEEP_LENGTH_MASK -> new MaskingRuleDefinition(
                    strategy,
                    null,
                    null,
                    null,
                    maskCharacter == null ? "*" : maskCharacter,
                    null
            );
            case FIXED_VALUE -> new MaskingRuleDefinition(
                    strategy,
                    null,
                    null,
                    null,
                    null,
                    fixedValue
            );
            case NULLIFY -> new MaskingRuleDefinition(strategy, null, null, null, null, null);
        };
    }
}
