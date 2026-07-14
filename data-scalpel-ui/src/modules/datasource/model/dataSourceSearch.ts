import type { DataSourceFilters } from './dataSource';

const escapeDslText = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');

const contains = (field: string, value: string) => `${field}:*"${escapeDslText(value)}"*`;

const equals = (field: string, value: string | boolean) => `${field}:"${String(value)}"`;

const anyEquals = (field: string, values: string[]) => values.length === 1
  ? equals(field, values[0])
  : `(${values.map((value) => equals(field, value)).join(' OR ')})`;

export const buildDataSourceSearch = (filters: DataSourceFilters): string | undefined => {
  const conditions = [
    filters.keyword?.trim()
      ? `(${contains('name', filters.keyword.trim())} OR ${contains('code', filters.keyword.trim())})`
      : undefined,
    filters.directoryIds?.length ? anyEquals('directoryId', filters.directoryIds) : undefined,
    filters.uncategorized ? 'directoryId:null' : undefined,
    filters.purpose === 'SOURCE' ? equals('sourceEnabled', true) : undefined,
    filters.purpose === 'STORAGE' ? equals('storageEnabled', true) : undefined,
    filters.purpose === 'DISTRIBUTION' ? equals('distributionEnabled', true) : undefined,
    filters.purpose === 'BOTH'
      ? `${equals('sourceEnabled', true)} AND ${equals('storageEnabled', true)}`
      : undefined,
    filters.databaseType ? equals('databaseType', filters.databaseType) : undefined,
    filters.enabled === undefined ? undefined : equals('enabled', filters.enabled),
  ].filter((condition): condition is string => Boolean(condition));

  return conditions.length ? conditions.join(' AND ') : undefined;
};
