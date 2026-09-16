import { type JoinCondition, type JoinOutputColumn, type JoinType, type StreamJoinType } from "../canvasTypes";
import { isRecord, stringValue } from './scalars';

export const parseJoinType = (value: unknown, path: string, errors: string[]): JoinType | null => {
  if (value === null || value === undefined || value === '') return null;
  if (value === 'INNER' || value === 'LEFT' || value === 'RIGHT' || value === 'FULL') return value;
  errors.push(`${path} 不是受支持的 Join 类型`);
  return null;
};

export const parseStreamJoinType = (
  value: unknown,
  path: string,
  errors: string[],
): StreamJoinType | null => {
  if (value === null || value === undefined || value === '') return null;
  if (value === 'INNER' || value === 'LEFT') return value;
  errors.push(`${path} 不是受支持的流 Join 类型`);
  return null;
};

export const parseJoinConditions = (value: unknown, path: string, errors: string[]): JoinCondition[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index): JoinCondition[] => {
    if (!isRecord(item)) {
      errors.push(`${path}[${index}] 必须是对象`);
      return [];
    }
    if (item.operator !== undefined && item.operator !== 'EQUALS') {
      errors.push(`${path}[${index}].operator 仅支持 EQUALS`);
    }
    return [{
      leftColumnName: stringValue(item.leftColumnName),
      operator: 'EQUALS',
      rightColumnName: stringValue(item.rightColumnName),
    }];
  });
};

export const parseJoinOutputColumns = (
  value: unknown,
  path: string,
  errors: string[],
): JoinOutputColumn[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index): JoinOutputColumn[] => {
    if (!isRecord(item)) {
      errors.push(`${path}[${index}] 必须是对象`);
      return [];
    }
    if (item.sourceSide !== 'LEFT' && item.sourceSide !== 'RIGHT') {
      errors.push(`${path}[${index}].sourceSide 仅支持 LEFT 或 RIGHT`);
    }
    if (typeof item.included !== 'boolean') {
      errors.push(`${path}[${index}].included 必须是布尔值`);
    }
    return [{
      sourceSide: item.sourceSide === 'RIGHT' ? 'RIGHT' : 'LEFT',
      sourceColumnName: stringValue(item.sourceColumnName),
      outputColumnName: stringValue(item.outputColumnName),
      included: typeof item.included === 'boolean' ? item.included : true,
    }];
  });
};
