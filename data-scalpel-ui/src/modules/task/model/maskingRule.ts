export type MaskingStrategy =
  | 'PARTIAL_MASK'
  | 'POSITION_MASK'
  | 'KEEP_LENGTH_MASK'
  | 'FIXED_VALUE'
  | 'NULLIFY';

export interface MaskingRuleDefinition {
  strategy: MaskingStrategy;
  keepPrefixLength: number | null;
  keepSuffixLength: number | null;
  maskPosition: number | null;
  maskCharacter: string | null;
  fixedValue: string | null;
}

export interface DataMaskingRule {
  id: string;
  code: string;
  name: string;
  description: string | null;
  strategy: MaskingStrategy;
  definition: MaskingRuleDefinition;
  createdAt: string;
  updatedAt: string;
}

export interface CreateDataMaskingRuleRequest {
  code: string;
  name: string;
  description: string | null;
  definition: MaskingRuleDefinition;
}

export type UpdateDataMaskingRuleRequest = Omit<CreateDataMaskingRuleRequest, 'code'>;

export const maskingStrategyLabels: Record<MaskingStrategy, string> = {
  PARTIAL_MASK: '部分掩码',
  POSITION_MASK: '按位置掩码',
  KEEP_LENGTH_MASK: '等长掩码',
  FIXED_VALUE: '固定值替换',
  NULLIFY: '置为 NULL',
};

export const maskingStrategyColors: Record<MaskingStrategy, string> = {
  PARTIAL_MASK: 'blue',
  POSITION_MASK: 'cyan',
  KEEP_LENGTH_MASK: 'cyan',
  FIXED_VALUE: 'purple',
  NULLIFY: 'default',
};

export const createMaskingRuleDefinition = (
  strategy: MaskingStrategy = 'PARTIAL_MASK',
): MaskingRuleDefinition => {
  switch (strategy) {
    case 'PARTIAL_MASK':
      return {
        strategy,
        keepPrefixLength: 3,
        keepSuffixLength: 4,
        maskPosition: null,
        maskCharacter: '*',
        fixedValue: null,
      };
    case 'KEEP_LENGTH_MASK':
      return {
        strategy,
        keepPrefixLength: null,
        keepSuffixLength: null,
        maskPosition: null,
        maskCharacter: '*',
        fixedValue: null,
      };
    case 'FIXED_VALUE':
      return {
        strategy,
        keepPrefixLength: null,
        keepSuffixLength: null,
        maskPosition: null,
        maskCharacter: null,
        fixedValue: '',
      };
    case 'NULLIFY':
      return {
        strategy,
        keepPrefixLength: null,
        keepSuffixLength: null,
        maskPosition: null,
        maskCharacter: null,
        fixedValue: null,
      };
    case 'POSITION_MASK':
      return {
        strategy,
        keepPrefixLength: null,
        keepSuffixLength: null,
        maskPosition: 2,
        maskCharacter: '*',
        fixedValue: null,
      };
  }
};

export const canonicalMaskingRuleDefinition = (
  definition: MaskingRuleDefinition,
): MaskingRuleDefinition => {
  const defaults = createMaskingRuleDefinition(definition.strategy);
  switch (definition.strategy) {
    case 'PARTIAL_MASK':
      return {
        ...defaults,
        keepPrefixLength: definition.keepPrefixLength,
        keepSuffixLength: definition.keepSuffixLength,
        maskCharacter: definition.maskCharacter ?? '*',
      };
    case 'POSITION_MASK':
      return {
        ...defaults,
        maskPosition: definition.maskPosition ?? 2,
        maskCharacter: definition.maskCharacter ?? '*',
      };
    case 'KEEP_LENGTH_MASK':
      return { ...defaults, maskCharacter: definition.maskCharacter ?? '*' };
    case 'FIXED_VALUE':
      return { ...defaults, fixedValue: definition.fixedValue };
    case 'NULLIFY':
      return defaults;
  }
};

export const maskingRuleDefinitionsEqual = (
  left: MaskingRuleDefinition,
  right: MaskingRuleDefinition,
) => JSON.stringify(canonicalMaskingRuleDefinition(left))
  === JSON.stringify(canonicalMaskingRuleDefinition(right));

export const previewMaskedText = (
  value: string,
  definition: MaskingRuleDefinition,
): string | null => {
  const canonical = canonicalMaskingRuleDefinition(definition);
  const characters = Array.from(value);
  switch (canonical.strategy) {
    case 'PARTIAL_MASK': {
      const prefixLength = canonical.keepPrefixLength ?? 0;
      const suffixLength = canonical.keepSuffixLength ?? 0;
      const maskCharacter = canonical.maskCharacter ?? '*';
      if (characters.length <= prefixLength + suffixLength) {
        return maskCharacter.repeat(characters.length);
      }
      return [
        ...characters.slice(0, prefixLength),
        ...Array<string>(characters.length - prefixLength - suffixLength)
          .fill(maskCharacter),
        ...characters.slice(characters.length - suffixLength),
      ].join('');
    }
    case 'POSITION_MASK': {
      const index = (canonical.maskPosition ?? 2) - 1;
      if (index >= characters.length) return value;
      characters[index] = canonical.maskCharacter ?? '*';
      return characters.join('');
    }
    case 'KEEP_LENGTH_MASK':
      return (canonical.maskCharacter ?? '*').repeat(characters.length);
    case 'FIXED_VALUE':
      return canonical.fixedValue ?? '';
    case 'NULLIFY':
      return null;
  }
};
