import { parseStringArray, parseUnionMode, stringValue } from "../../canvasValueParsers";
import { type UnionMergeTable } from "../../canvasTypes";
import { parseConfiguration, isRecord, type Configuration } from '../configurationParsing';

export const unionMergeFieldActions = new Set(['MATCH', 'RENAME', 'REMOVE']);

export const parseUnionMergingTables = (
  value: unknown,
  path: string,
  errors: string[],
): UnionMergeTable[] | null => {
  if (value === undefined || value === null) return null;
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组或 null`);
    return [];
  }
  return value.flatMap((item, tableIndex): UnionMergeTable[] => {
    const tablePath = `${path}[${tableIndex}]`;
    if (!isRecord(item)) {
      errors.push(`${tablePath} 必须是对象`);
      return [];
    }
    if (!Array.isArray(item.fieldRules)) {
      errors.push(`${tablePath}.fieldRules 必须是数组`);
    }
    const fieldRules = Array.isArray(item.fieldRules)
      ? item.fieldRules.flatMap((rule, ruleIndex): UnionMergeTable['fieldRules'] => {
        const rulePath = `${tablePath}.fieldRules[${ruleIndex}]`;
        if (!isRecord(rule)) {
          errors.push(`${rulePath} 必须是对象`);
          return [];
        }
        const action = stringValue(rule.action);
        if (!unionMergeFieldActions.has(action)) {
          errors.push(`${rulePath}.action 仅支持 MATCH、RENAME 或 REMOVE`);
        }
        if (rule.targetColumnName !== undefined && rule.targetColumnName !== null
            && typeof rule.targetColumnName !== 'string') {
          errors.push(`${rulePath}.targetColumnName 必须是字符串或 null`);
        }
        return [{
          sourceColumnName: stringValue(rule.sourceColumnName),
          action: unionMergeFieldActions.has(action)
            ? action as UnionMergeTable['fieldRules'][number]['action']
            : null,
          targetColumnName: typeof rule.targetColumnName === 'string'
            ? rule.targetColumnName
            : null,
        }];
      })
      : [];
    return [{ tableName: stringValue(item.tableName), fieldRules }];
  });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'UNION'>>(value, path, (configuration, errors) => ({
      inputTableNames: parseStringArray(
        configuration.inputTableNames,
        `${path}.inputTableNames`,
        errors,
      ),
      outputTableName: stringValue(configuration.outputTableName),
      mode: parseUnionMode(configuration.mode, `${path}.mode`, errors),
      mergingTables: parseUnionMergingTables(
        configuration.mergingTables,
        `${path}.mergingTables`,
        errors,
      ),
    }))
  );
