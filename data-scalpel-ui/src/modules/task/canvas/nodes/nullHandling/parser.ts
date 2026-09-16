import { parseCanvasLiteral, parseStringArray, stringValue } from "../../canvasValueParsers";
import { CANVAS_NULL_HANDLING_MAX_RULES, type NullHandlingRule } from "../../canvasTypes";
import { parseConfiguration, isRecord, type Configuration, parseProcessorOperations } from '../configurationParsing';

export const parseNullHandlingRules = (
  value: unknown,
  path: string,
  errors: string[],
): NullHandlingRule[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_NULL_HANDLING_MAX_RULES) {
    errors.push(`${path} 不能超过 ${CANVAS_NULL_HANDLING_MAX_RULES} 项`);
  }
  return value.slice(0, CANVAS_NULL_HANDLING_MAX_RULES)
    .flatMap((item, index): NullHandlingRule[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      if (item.kind === 'DROP_ROW') {
        if (item.matchMode !== 'ANY_NULL' && item.matchMode !== 'ALL_NULL') {
          errors.push(`${itemPath}.matchMode 仅支持 ANY_NULL 或 ALL_NULL`);
        }
        return [{
          kind: 'DROP_ROW',
          columnNames: parseStringArray(item.columnNames, `${itemPath}.columnNames`, errors),
          matchMode: item.matchMode === 'ALL_NULL' ? 'ALL_NULL' : 'ANY_NULL',
        }];
      }
      if (item.kind === 'FILL_LITERAL') {
        return [{
          kind: 'FILL_LITERAL',
          columnName: stringValue(item.columnName),
          value: parseCanvasLiteral(item.value, `${itemPath}.value`, errors),
        }];
      }
      errors.push(`${itemPath}.kind 仅支持 DROP_ROW 或 FILL_LITERAL`);
      return [];
    });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'NULL_HANDLING'>>(value, path, (configuration, errors) => ({
      operations: parseProcessorOperations(configuration.operations, `${path}.operations`, errors,
        (operation, operationPath) => ({ rules: parseNullHandlingRules(operation.rules, `${operationPath}.rules`, errors) })),
    }))
  );
