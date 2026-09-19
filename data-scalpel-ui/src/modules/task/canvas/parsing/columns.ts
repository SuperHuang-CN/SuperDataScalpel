import { type CanvasColumnMapping } from "../canvasTypes";
import { isRecord, stringValue } from './scalars';

export const parseMappings = (value: unknown, path: string, errors: string[]): CanvasColumnMapping[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index): CanvasColumnMapping[] => {
    if (!isRecord(item)) {
      errors.push(`${path}[${index}] 必须是对象`);
      return [];
    }
    return [{
      sourceColumnName: stringValue(item.sourceColumnName),
      targetColumnName: stringValue(item.targetColumnName),
    }];
  });
};
