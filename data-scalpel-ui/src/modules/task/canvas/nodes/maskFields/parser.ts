import { stringValue, validateOptionalUuid } from "../../canvasValueParsers";
import { CANVAS_MASKING_MAX_FIELD_RULES, type MaskFieldRule, type MaskingRuleDefinition } from "../../canvasTypes";
import { createMaskingRuleDefinition } from "../../../model/maskingRule";
import { parseConfiguration, isRecord, type Configuration, parseProcessorOperations } from '../configurationParsing';

export const parseMaskingDefinition = (
  value: unknown,
  path: string,
  errors: string[],
): MaskingRuleDefinition => {
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象`);
    return createMaskingRuleDefinition();
  }
  const supportedStrategies = new Set([
    'PARTIAL_MASK',
    'POSITION_MASK',
    'KEEP_LENGTH_MASK',
    'FIXED_VALUE',
    'NULLIFY',
  ]);
  const rawStrategy = stringValue(value.strategy);
  if (!supportedStrategies.has(rawStrategy)) {
    errors.push(`${path}.strategy 不是受支持的脱敏策略`);
  }
  const strategy = supportedStrategies.has(rawStrategy)
    ? rawStrategy as MaskingRuleDefinition['strategy']
    : 'PARTIAL_MASK';
  const defaults = createMaskingRuleDefinition(strategy);
  const parseKeepLength = (raw: unknown, fieldPath: string) => {
    if (typeof raw !== 'number' || !Number.isInteger(raw) || raw < 0 || raw > 1024) {
      errors.push(`${fieldPath} 必须是 0..1024 的整数`);
      return 0;
    }
    return raw;
  };
  const parseMaskPosition = (raw: unknown, fieldPath: string) => {
    if (raw === null || raw === undefined) return 2;
    if (typeof raw !== 'number' || !Number.isInteger(raw) || raw < 1 || raw > 1024) {
      errors.push(`${fieldPath} 必须是 1..1024 的整数`);
      return 2;
    }
    return raw;
  };
  if (strategy === 'PARTIAL_MASK') {
    const maskCharacter = value.maskCharacter === null
      || value.maskCharacter === undefined
      ? '*' : stringValue(value.maskCharacter);
    if (Array.from(maskCharacter).length !== 1) {
      errors.push(`${path}.maskCharacter 必须是一个 Unicode 字符`);
    }
    if (value.maskPosition !== null && value.maskPosition !== undefined
      || value.fixedValue !== null && value.fixedValue !== undefined) {
      errors.push(`${path} 包含不适用于 PARTIAL_MASK 的参数`);
    }
    return {
      ...defaults,
      keepPrefixLength: parseKeepLength(
        value.keepPrefixLength,
        `${path}.keepPrefixLength`,
      ),
      keepSuffixLength: parseKeepLength(
        value.keepSuffixLength,
        `${path}.keepSuffixLength`,
      ),
      maskCharacter,
    };
  }
  if (strategy === 'POSITION_MASK') {
    const maskCharacter = value.maskCharacter === null
      || value.maskCharacter === undefined
      ? '*' : stringValue(value.maskCharacter);
    if (Array.from(maskCharacter).length !== 1) {
      errors.push(`${path}.maskCharacter 必须是一个 Unicode 字符`);
    }
    if (value.keepPrefixLength !== null && value.keepPrefixLength !== undefined
      || value.keepSuffixLength !== null && value.keepSuffixLength !== undefined
      || value.fixedValue !== null && value.fixedValue !== undefined) {
      errors.push(`${path} 包含不适用于 POSITION_MASK 的参数`);
    }
    return {
      ...defaults,
      maskPosition: parseMaskPosition(value.maskPosition, `${path}.maskPosition`),
      maskCharacter,
    };
  }
  if (strategy === 'KEEP_LENGTH_MASK') {
    const maskCharacter = value.maskCharacter === null
      || value.maskCharacter === undefined
      ? '*' : stringValue(value.maskCharacter);
    if (Array.from(maskCharacter).length !== 1) {
      errors.push(`${path}.maskCharacter 必须是一个 Unicode 字符`);
    }
    if (value.keepPrefixLength !== null && value.keepPrefixLength !== undefined
      || value.keepSuffixLength !== null && value.keepSuffixLength !== undefined
      || value.maskPosition !== null && value.maskPosition !== undefined
      || value.fixedValue !== null && value.fixedValue !== undefined) {
      errors.push(`${path} 包含不适用于 KEEP_LENGTH_MASK 的参数`);
    }
    return { ...defaults, maskCharacter };
  }
  if (strategy === 'FIXED_VALUE') {
    if (typeof value.fixedValue !== 'string') {
      errors.push(`${path}.fixedValue 必须是字符串`);
    } else if (value.fixedValue.length > 1024) {
      errors.push(`${path}.fixedValue 不能超过 1024 个字符`);
    }
    if (value.keepPrefixLength !== null && value.keepPrefixLength !== undefined
      || value.keepSuffixLength !== null && value.keepSuffixLength !== undefined
      || value.maskPosition !== null && value.maskPosition !== undefined
      || value.maskCharacter !== null && value.maskCharacter !== undefined) {
      errors.push(`${path} 包含不适用于 FIXED_VALUE 的参数`);
    }
    return {
      ...defaults,
      fixedValue: typeof value.fixedValue === 'string' ? value.fixedValue : '',
    };
  }
  if (value.keepPrefixLength !== null && value.keepPrefixLength !== undefined
    || value.keepSuffixLength !== null && value.keepSuffixLength !== undefined
    || value.maskPosition !== null && value.maskPosition !== undefined
    || value.maskCharacter !== null && value.maskCharacter !== undefined
    || value.fixedValue !== null && value.fixedValue !== undefined) {
    errors.push(`${path} 包含不适用于 NULLIFY 的参数`);
  }
  return defaults;
};

export const parseMaskFieldRules = (
  value: unknown,
  path: string,
  errors: string[],
): MaskFieldRule[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_MASKING_MAX_FIELD_RULES) {
    errors.push(`${path} 不能超过 ${CANVAS_MASKING_MAX_FIELD_RULES} 项`);
  }
  return value.slice(0, CANVAS_MASKING_MAX_FIELD_RULES)
    .flatMap((item, index): MaskFieldRule[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      const ruleSource = item.ruleSource === 'GLOBAL' ? 'GLOBAL' : 'INLINE';
      if (item.ruleSource !== 'GLOBAL' && item.ruleSource !== 'INLINE') {
        errors.push(`${itemPath}.ruleSource 仅支持 GLOBAL 或 INLINE`);
      }
      let sourceRuleRef: MaskFieldRule['sourceRuleRef'] = null;
      if (ruleSource === 'GLOBAL') {
        if (!isRecord(item.sourceRuleRef)) {
          errors.push(`${itemPath}.sourceRuleRef 必须是对象`);
        } else {
          sourceRuleRef = {
            ruleId: validateOptionalUuid(
              stringValue(item.sourceRuleRef.ruleId),
              `${itemPath}.sourceRuleRef.ruleId`,
              errors,
            ),
            ruleCode: stringValue(item.sourceRuleRef.ruleCode),
            ruleName: stringValue(item.sourceRuleRef.ruleName),
          };
        }
      } else if (item.sourceRuleRef !== null && item.sourceRuleRef !== undefined) {
        errors.push(`${itemPath}.sourceRuleRef 仅允许 GLOBAL 规则配置`);
      }
      return [{
        fieldName: stringValue(item.fieldName),
        ruleSource,
        sourceRuleRef,
        definition: parseMaskingDefinition(
          item.definition,
          `${itemPath}.definition`,
          errors,
        ),
      }];
    });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'MASK_FIELDS'>>(value, path, (configuration, errors) => ({
      operations: parseProcessorOperations(configuration.operations, `${path}.operations`, errors,
        (operation, operationPath) => ({ fieldRules: parseMaskFieldRules(operation.fieldRules, `${operationPath}.fieldRules`, errors) })),
    }))
  );
