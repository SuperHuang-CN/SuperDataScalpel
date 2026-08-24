package cn.superhuang.data.scalpel.contract.task;

/**
 * Stable executable masking definition shared by rule management and Canvas.
 *
 * <p>Only parameters used by the selected strategy may be populated.</p>
 */
public record MaskingRuleDefinition(
        MaskingStrategy strategy,
        Integer keepPrefixLength,
        Integer keepSuffixLength,
        Integer maskPosition,
        String maskCharacter,
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
