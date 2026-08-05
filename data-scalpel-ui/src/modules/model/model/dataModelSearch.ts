import type { DataModelFilters } from './dataModel';

const escapeDslText = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');

const contains = (field: string, value: string) => `${field}:*"${escapeDslText(value)}"*`;

const equals = (field: string, value: string) => `${field}:"${escapeDslText(value)}"`;

const anyEquals = (field: string, values: string[]) => values.length === 1
  ? equals(field, values[0])
  : `(${values.map((value) => equals(field, value)).join(' OR ')})`;

export const buildDataModelSearch = (filters: DataModelFilters): string | undefined => {
  const conditions = [
    filters.keyword?.trim()
      ? `(${contains('name', filters.keyword.trim())} OR ${contains('code', filters.keyword.trim())})`
      : undefined,
    filters.status ? equals('status', filters.status) : undefined,
    filters.storageDataSourceId ? equals('storageDataSourceId', filters.storageDataSourceId) : undefined,
    filters.warehouseLayerId ? equals('warehouseLayerId', filters.warehouseLayerId) : undefined,
    filters.directoryIds?.length ? anyEquals('directoryId', filters.directoryIds) : undefined,
    filters.uncategorized ? 'directoryId:null' : undefined,
  ].filter((condition): condition is string => Boolean(condition));

  return conditions.length ? conditions.join(' AND ') : undefined;
};
