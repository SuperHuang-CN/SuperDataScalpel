import { type KafkaValueColumn, type KafkaValueSchema } from "../canvasTypes";
import { isRecord, stringValue, platformDataTypes, optionalInteger } from './scalars';

export const parseKafkaValueSchema = (
  value: unknown,
  path: string,
  errors: string[],
): KafkaValueSchema => {
  if (value === undefined || value === null) return { columns: [] };
  if (!isRecord(value) || !Array.isArray(value.columns)) {
    errors.push(`${path}.columns 必须是数组`);
    return { columns: [] };
  }
  return {
    columns: value.columns.flatMap((item, index) => {
      const columnPath = `${path}.columns[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${columnPath} 必须是对象`);
        return [];
      }
      const fieldType = stringValue(item.fieldType);
      if (!platformDataTypes.has(fieldType)) {
        errors.push(`${columnPath}.fieldType 不是受支持的平台类型`);
      }
      if (typeof item.nullable !== 'boolean') {
        errors.push(`${columnPath}.nullable 必须是 Boolean`);
      }
      if (item.comment !== null && item.comment !== undefined && typeof item.comment !== 'string') {
        errors.push(`${columnPath}.comment 必须是字符串或 null`);
      }
      return [{
        name: stringValue(item.name),
        fieldType: platformDataTypes.has(fieldType)
          ? fieldType as KafkaValueColumn['fieldType']
          : 'STRING',
        length: optionalInteger(item.length, `${columnPath}.length`, errors),
        precision: optionalInteger(item.precision, `${columnPath}.precision`, errors),
        scale: optionalInteger(item.scale, `${columnPath}.scale`, errors),
        nullable: typeof item.nullable === 'boolean' ? item.nullable : true,
        comment: typeof item.comment === 'string' ? item.comment : null,
      }];
    }),
  };
};
