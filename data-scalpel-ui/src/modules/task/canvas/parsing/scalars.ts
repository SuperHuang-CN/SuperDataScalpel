

export const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export const sensitiveRuntimeParameterPattern = /(password|passwd|secret|token|credential|api[-_.]?key|access[-_.]?key|signature)/i;

export const isSensitiveRuntimeParameterName = (name: string) => (
  sensitiveRuntimeParameterPattern.test(name)
);

export const isRecord = (value: unknown): value is Record<string, unknown> => (
  typeof value === 'object' && value !== null && !Array.isArray(value)
);

export const stringValue = (value: unknown) => typeof value === 'string' ? value : '';

export const validateOptionalUuid = (value: string, path: string, errors: string[]) => {
  if (value && !uuidPattern.test(value)) errors.push(`${path} 必须是 UUID`);
  return value;
};

export const legacyTableName = (value: unknown) => isRecord(value) ? stringValue(value.tableName) : '';

export const parseStringArray = (value: unknown, path: string, errors: string[]): string[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.map((item, index) => {
    if (typeof item !== 'string') {
      errors.push(`${path}[${index}] 必须是字符串`);
      return '';
    }
    return item;
  });
};

export const platformDataTypes = new Set([
  'BOOLEAN',
  'BYTE',
  'SHORT',
  'INTEGER',
  'LONG',
  'FLOAT',
  'DOUBLE',
  'DECIMAL',
  'STRING',
  'BINARY',
  'DATE',
  'TIMESTAMP',
  'TIMESTAMP_NTZ',
]);

export const optionalInteger = (value: unknown, path: string, errors: string[]) => {
  if (value === null || value === undefined) return null;
  if (typeof value !== 'number' || !Number.isInteger(value)) {
    errors.push(`${path} 必须是整数或 null`);
    return null;
  }
  return value;
};
