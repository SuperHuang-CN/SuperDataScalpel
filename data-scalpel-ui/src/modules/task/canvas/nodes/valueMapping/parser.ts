import { parseCanvasLiteral, stringValue } from "../../canvasValueParsers";
import { CANVAS_VALUE_MAPPING_MAX_ENTRIES_PER_RULE, CANVAS_VALUE_MAPPING_MAX_RULES, CANVAS_VALUE_MAPPING_MAX_TOTAL_ENTRIES, type ValueMappingRule } from "../../canvasTypes";
import { parseConfiguration, isRecord, type Configuration, parseProcessorOperations } from '../configurationParsing';

export const parseValueMappingRules = (
  value: unknown,
  path: string,
  errors: string[],
): ValueMappingRule[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_VALUE_MAPPING_MAX_RULES) {
    errors.push(`${path} 不能超过 ${CANVAS_VALUE_MAPPING_MAX_RULES} 项`);
  }
  let totalEntries = 0;
  const rules = value.slice(0, CANVAS_VALUE_MAPPING_MAX_RULES)
    .flatMap((item, index): ValueMappingRule[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      const rawEntries = item.entries;
      if (rawEntries !== undefined && rawEntries !== null && !Array.isArray(rawEntries)) {
        errors.push(`${itemPath}.entries 必须是数组`);
      }
      const entries = Array.isArray(rawEntries)
        ? rawEntries.slice(0, CANVAS_VALUE_MAPPING_MAX_ENTRIES_PER_RULE)
          .flatMap((entry, entryIndex) => {
            const entryPath = `${itemPath}.entries[${entryIndex}]`;
            if (!isRecord(entry)) {
              errors.push(`${entryPath} 必须是对象`);
              return [];
            }
            return [{
              sourceValue: parseCanvasLiteral(
                entry.sourceValue,
                `${entryPath}.sourceValue`,
                errors,
              ),
              targetValue: entry.targetValue === null || entry.targetValue === undefined
                ? null
                : parseCanvasLiteral(
                  entry.targetValue,
                  `${entryPath}.targetValue`,
                  errors,
                ),
            }];
          })
        : [];
      if (Array.isArray(rawEntries)
        && rawEntries.length > CANVAS_VALUE_MAPPING_MAX_ENTRIES_PER_RULE) {
        errors.push(
          `${itemPath}.entries 不能超过 ${CANVAS_VALUE_MAPPING_MAX_ENTRIES_PER_RULE} 项`,
        );
      }
      totalEntries += entries.length;
      const unmatchedStrategies = new Set(['KEEP', 'SET_NULL', 'SET_LITERAL', 'ERROR']);
      if (!unmatchedStrategies.has(stringValue(item.unmatchedStrategy))) {
        errors.push(`${itemPath}.unmatchedStrategy 不是受支持的策略`);
      }
      const unmatchedStrategy = unmatchedStrategies.has(stringValue(item.unmatchedStrategy))
        ? stringValue(item.unmatchedStrategy) as ValueMappingRule['unmatchedStrategy']
        : 'KEEP';
      return [{
        columnName: stringValue(item.columnName),
        entries,
        unmatchedStrategy,
        unmatchedValue: item.unmatchedValue === null || item.unmatchedValue === undefined
          ? null
          : parseCanvasLiteral(
            item.unmatchedValue,
            `${itemPath}.unmatchedValue`,
            errors,
          ),
      }];
    });
  if (totalEntries > CANVAS_VALUE_MAPPING_MAX_TOTAL_ENTRIES) {
    errors.push(`${path} 映射项总数不能超过 ${CANVAS_VALUE_MAPPING_MAX_TOTAL_ENTRIES}`);
  }
  return rules;
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'VALUE_MAPPING'>>(value, path, (configuration, errors) => ({
      operations: parseProcessorOperations(configuration.operations, `${path}.operations`, errors,
        (operation, operationPath) => ({ rules: parseValueMappingRules(operation.rules, `${operationPath}.rules`, errors) })),
    }))
  );
