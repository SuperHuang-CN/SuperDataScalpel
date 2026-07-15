import type { DataServiceFilters } from './dataService';

const escapeDslText = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');

const contains = (field: string, value: string) => `${field}:*"${escapeDslText(value)}"*`;

const equals = (field: string, value: string | boolean) => `${field}:"${String(value)}"`;

const anyEquals = (field: string, values: string[]) => values.length === 1
  ? equals(field, values[0])
  : `(${values.map((value) => equals(field, value)).join(' OR ')})`;

export const buildDataServiceSearch = (filters: DataServiceFilters): string | undefined => {
  const conditions = [
    filters.keyword?.trim()
      ? `(${contains('name', filters.keyword.trim())} OR ${contains('code', filters.keyword.trim())})`
      : undefined,
    filters.status ? equals('status', filters.status) : undefined,
    filters.engineId ? equals('engineId', filters.engineId) : undefined,
    filters.modelId ? equals('modelId', filters.modelId) : undefined,
    filters.directoryIds?.length ? anyEquals('directoryId', filters.directoryIds) : undefined,
    filters.uncategorized ? 'directoryId:null' : undefined,
  ].filter((condition): condition is string => Boolean(condition));

  return conditions.length ? conditions.join(' AND ') : undefined;
};
