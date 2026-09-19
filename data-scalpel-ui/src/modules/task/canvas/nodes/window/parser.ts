import { parseCanvasLiteral, parseSortFields, parseStringArray, stringValue } from "../../canvasValueParsers";
import { CANVAS_WINDOW_MAX_FUNCTIONS, type RowsFrameBoundary, type RowsWindowFrame, type WindowFunctionItem } from "../../canvasTypes";
import { parseConfiguration, isRecord, type Configuration } from '../configurationParsing';

export const parseRowsFrameBoundary = (
  value: unknown,
  path: string,
  errors: string[],
): RowsFrameBoundary => {
  if (!isRecord(value)) {
    errors.push(`${path} 必须是 Frame 边界对象`);
    return { kind: 'CURRENT_ROW' };
  }
  if (value.kind === 'UNBOUNDED_PRECEDING'
    || value.kind === 'CURRENT_ROW'
    || value.kind === 'UNBOUNDED_FOLLOWING') {
    return { kind: value.kind };
  }
  if (value.kind === 'PRECEDING' || value.kind === 'FOLLOWING') {
    if (typeof value.offset !== 'number' || !Number.isInteger(value.offset)) {
      errors.push(`${path}.offset 必须是整数`);
    }
    return {
      kind: value.kind,
      offset: typeof value.offset === 'number' && Number.isInteger(value.offset)
        ? value.offset : 0,
    };
  }
  errors.push(`${path}.kind 不是受支持的 Frame 边界`);
  return { kind: 'CURRENT_ROW' };
};

export const parseRowsWindowFrame = (
  value: unknown,
  path: string,
  errors: string[],
): RowsWindowFrame => {
  if (!isRecord(value)) {
    errors.push(`${path} 必须是 ROWS Frame 对象`);
    return {
      type: 'ROWS',
      start: { kind: 'UNBOUNDED_PRECEDING' },
      end: { kind: 'CURRENT_ROW' },
    };
  }
  if (value.type !== 'ROWS') errors.push(`${path}.type 仅支持 ROWS`);
  return {
    type: 'ROWS',
    start: parseRowsFrameBoundary(value.start, `${path}.start`, errors),
    end: parseRowsFrameBoundary(value.end, `${path}.end`, errors),
  };
};

export const parseWindowFunctions = (
  value: unknown,
  path: string,
  errors: string[],
): WindowFunctionItem[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_WINDOW_MAX_FUNCTIONS) {
    errors.push(`${path} 不能超过 ${CANVAS_WINDOW_MAX_FUNCTIONS} 项`);
  }
  return value.slice(0, CANVAS_WINDOW_MAX_FUNCTIONS)
    .flatMap((item, index): WindowFunctionItem[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      const outputColumnName = stringValue(item.outputColumnName);
      if (item.kind === 'ROW_NUMBER' || item.kind === 'RANK' || item.kind === 'DENSE_RANK') {
        return [{ kind: item.kind, outputColumnName }];
      }
      if (item.kind === 'LAG' || item.kind === 'LEAD') {
        if (typeof item.offset !== 'number' || !Number.isInteger(item.offset)) {
          errors.push(`${itemPath}.offset 必须是整数`);
        }
        return [{
          kind: item.kind,
          sourceColumnName: stringValue(item.sourceColumnName),
          offset: typeof item.offset === 'number' && Number.isInteger(item.offset)
            ? item.offset : 0,
          defaultValue: item.defaultValue === null || item.defaultValue === undefined
            ? null
            : parseCanvasLiteral(
              item.defaultValue,
              `${itemPath}.defaultValue`,
              errors,
            ),
          outputColumnName,
        }];
      }
      if (item.kind === 'COUNT'
        || item.kind === 'SUM'
        || item.kind === 'AVG'
        || item.kind === 'MIN'
        || item.kind === 'MAX') {
        if (item.sourceColumnName !== null
          && item.sourceColumnName !== undefined
          && typeof item.sourceColumnName !== 'string') {
          errors.push(`${itemPath}.sourceColumnName 必须是字符串或 null`);
        }
        return [{
          kind: item.kind,
          sourceColumnName: typeof item.sourceColumnName === 'string'
            ? item.sourceColumnName : null,
          outputColumnName,
          frame: parseRowsWindowFrame(item.frame, `${itemPath}.frame`, errors),
        }];
      }
      if (item.kind === 'FIRST_VALUE' || item.kind === 'LAST_VALUE') {
        if (typeof item.ignoreNulls !== 'boolean') {
          errors.push(`${itemPath}.ignoreNulls 必须是布尔值`);
        }
        return [{
          kind: item.kind,
          sourceColumnName: stringValue(item.sourceColumnName),
          ignoreNulls: item.ignoreNulls === true,
          outputColumnName,
          frame: parseRowsWindowFrame(item.frame, `${itemPath}.frame`, errors),
        }];
      }
      errors.push(`${itemPath}.kind 不是受支持的窗口函数`);
      return [];
    });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'WINDOW'>>(value, path, (configuration, errors) => ({
      sourceTableName: stringValue(configuration.sourceTableName),
      outputTableName: stringValue(configuration.outputTableName),
      partitionByColumns: parseStringArray(
        configuration.partitionByColumns,
        `${path}.partitionByColumns`,
        errors,
      ),
      orderBy: parseSortFields(configuration.orderBy, `${path}.orderBy`, errors),
      functions: parseWindowFunctions(configuration.functions, `${path}.functions`, errors),
    }))
  );
