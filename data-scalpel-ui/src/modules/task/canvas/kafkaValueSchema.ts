import type { DataModelField, PlatformDataType } from '../../model';
import type { KafkaValueColumn, KafkaValueSchema } from './canvasTypes';

const isRecord = (value: unknown): value is Record<string, unknown> => (
  typeof value === 'object' && value !== null && !Array.isArray(value)
);

const integerOrNull = (value: unknown) => (
  typeof value === 'number' && Number.isInteger(value) ? value : null
);

export const emptyKafkaValueColumn = (): KafkaValueColumn => ({
  name: '',
  fieldType: 'STRING',
  length: null,
  precision: null,
  scale: null,
  nullable: true,
  comment: null,
});

export const modelFieldsToKafkaValueSchema = (fields: DataModelField[]): KafkaValueSchema => ({
  columns: [...fields]
    .sort((left, right) => left.sortOrder - right.sortOrder)
    .map((field) => ({
      name: field.code,
      fieldType: field.fieldType,
      length: field.fieldType === 'STRING' ? field.length : null,
      precision: field.fieldType === 'DECIMAL' ? field.precision : null,
      scale: field.fieldType === 'DECIMAL' ? field.scale : null,
      nullable: field.nullable,
      comment: field.description,
    })),
});

const jsonSchemaType = (
  definition: Record<string, unknown>,
  path: string,
  errors: string[],
): { fieldType: PlatformDataType; nullableByType: boolean } | null => {
  const declared = definition.type;
  const types = Array.isArray(declared) ? declared : [declared];
  const nullableByType = types.includes('null');
  const concrete = types.filter((type) => type !== 'null');
  if (concrete.length !== 1 || typeof concrete[0] !== 'string') {
    errors.push(`${path}.type 必须声明一个标量类型，可额外包含 null`);
    return null;
  }
  switch (concrete[0]) {
    case 'boolean':
      return { fieldType: 'BOOLEAN', nullableByType };
    case 'integer':
      return {
        fieldType: definition.format === 'int32' ? 'INTEGER' : 'LONG',
        nullableByType,
      };
    case 'number':
      return {
        fieldType: definition.format === 'float' ? 'FLOAT' : 'DOUBLE',
        nullableByType,
      };
    case 'string':
      if (definition.format === 'date') return { fieldType: 'DATE', nullableByType };
      if (definition.format === 'date-time') return { fieldType: 'TIMESTAMP', nullableByType };
      return { fieldType: 'STRING', nullableByType };
    case 'object':
    case 'array':
      errors.push(`${path} 暂不支持嵌套 object 或 array`);
      return null;
    default:
      errors.push(`${path}.type 不是受支持的 JSON Schema 标量类型`);
      return null;
  }
};

export type JsonSchemaImportResult =
  | { success: true; schema: KafkaValueSchema }
  | { success: false; errors: string[] };

export const parseKafkaJsonSchema = (content: string): JsonSchemaImportResult => {
  let parsed: unknown;
  try {
    parsed = JSON.parse(content) as unknown;
  } catch (error) {
    return {
      success: false,
      errors: [`JSON 解析失败：${error instanceof Error ? error.message : '未知错误'}`],
    };
  }
  if (!isRecord(parsed) || parsed.type !== 'object' || !isRecord(parsed.properties)) {
    return { success: false, errors: ['根 Schema 必须是包含 properties 的 object'] };
  }
  const required = new Set(
    Array.isArray(parsed.required)
      ? parsed.required.filter((name): name is string => typeof name === 'string')
      : [],
  );
  const errors: string[] = [];
  const columns: KafkaValueColumn[] = [];
  Object.entries(parsed.properties).forEach(([name, rawDefinition]) => {
    const path = `properties.${name}`;
    if (!isRecord(rawDefinition)) {
      errors.push(`${path} 必须是字段 Schema 对象`);
      return;
    }
    const type = jsonSchemaType(rawDefinition, path, errors);
    if (!type) return;
    const length = type.fieldType === 'STRING'
      ? integerOrNull(rawDefinition.maxLength)
      : null;
    if (length !== null && length < 1) {
      errors.push(`${path}.maxLength 必须大于 0`);
      return;
    }
    columns.push({
      name,
      fieldType: type.fieldType,
      length,
      precision: null,
      scale: null,
      nullable: !required.has(name) || type.nullableByType,
      comment: typeof rawDefinition.description === 'string' ? rawDefinition.description : null,
    });
  });
  if (columns.length === 0 && errors.length === 0) {
    errors.push('JSON Schema 至少需要一个属性');
  }
  return errors.length > 0
    ? { success: false, errors }
    : { success: true, schema: { columns } };
};
