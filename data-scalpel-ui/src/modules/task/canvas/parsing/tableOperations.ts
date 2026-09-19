import { type AggregateFunction, type AggregateItem, type UnionMode, type DeduplicateKeepStrategy, type NullOrdering, type SortDirection, type SortField } from "../canvasTypes";
import { isRecord, stringValue } from './scalars';

export const aggregateFunctions = new Set<AggregateFunction>([
  'COUNT',
  'SUM',
  'AVG',
  'MIN',
  'MAX',
]);

export const parseAggregations = (
  value: unknown,
  path: string,
  errors: string[],
): AggregateItem[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index): AggregateItem[] => {
    const itemPath = `${path}[${index}]`;
    if (!isRecord(item)) {
      errors.push(`${itemPath} 必须是对象`);
      return [];
    }
    const rawFunction = stringValue(item.function);
    const aggregateFunction = aggregateFunctions.has(rawFunction as AggregateFunction)
      ? rawFunction as AggregateFunction
      : null;
    if (item.function !== undefined
      && item.function !== null
      && aggregateFunction === null) {
      errors.push(`${itemPath}.function 不是受支持的聚合函数`);
    }
    if (item.sourceColumnName !== undefined
      && item.sourceColumnName !== null
      && typeof item.sourceColumnName !== 'string') {
      errors.push(`${itemPath}.sourceColumnName 必须是字符串或 null`);
    }
    if (item.distinct !== undefined && typeof item.distinct !== 'boolean') {
      errors.push(`${itemPath}.distinct 必须是布尔值`);
    }
    return [{
      function: aggregateFunction,
      sourceColumnName: typeof item.sourceColumnName === 'string'
        ? item.sourceColumnName
        : null,
      outputColumnName: stringValue(item.outputColumnName),
      distinct: item.distinct === true,
    }];
  });
};

export const parseUnionMode = (
  value: unknown,
  path: string,
  errors: string[],
): UnionMode | null => {
  if (value === null || value === undefined || value === '') return null;
  if (value === 'ALL' || value === 'DISTINCT') return value;
  errors.push(`${path} 仅支持 ALL 或 DISTINCT`);
  return null;
};

export const parseDeduplicateKeepStrategy = (
  value: unknown,
  path: string,
  errors: string[],
): DeduplicateKeepStrategy | null => {
  if (value === null || value === undefined || value === '') return null;
  if (value === 'ANY' || value === 'FIRST' || value === 'LAST') return value;
  errors.push(`${path} 仅支持 ANY、FIRST 或 LAST`);
  return null;
};

export const parseSortFields = (
  value: unknown,
  path: string,
  errors: string[],
): SortField[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index): SortField[] => {
    const itemPath = `${path}[${index}]`;
    if (!isRecord(item)) {
      errors.push(`${itemPath} 必须是对象`);
      return [];
    }
    const direction: SortDirection | null = item.direction === 'ASC'
      || item.direction === 'DESC'
      ? item.direction
      : null;
    const nullOrdering: NullOrdering | null = item.nullOrdering === 'FIRST'
      || item.nullOrdering === 'LAST'
      ? item.nullOrdering
      : null;
    if (item.direction !== undefined && item.direction !== null && direction === null) {
      errors.push(`${itemPath}.direction 仅支持 ASC 或 DESC`);
    }
    if (item.nullOrdering !== undefined
      && item.nullOrdering !== null
      && nullOrdering === null) {
      errors.push(`${itemPath}.nullOrdering 仅支持 FIRST 或 LAST`);
    }
    return [{
      columnName: stringValue(item.columnName),
      direction,
      nullOrdering,
    }];
  });
};
