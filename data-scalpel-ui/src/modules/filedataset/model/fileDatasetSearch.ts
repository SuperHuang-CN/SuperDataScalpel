import type { FileDatasetFilters } from './fileDataset';

const escapeDslText = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');
const contains = (field: string, value: string) => `${field}:*"${escapeDslText(value)}"*`;
const equals = (field: string, value: string) => `${field}:"${escapeDslText(value)}"`;
const anyEquals = (field: string, values: string[]) => values.length === 1
  ? equals(field, values[0])
  : `(${values.map((value) => equals(field, value)).join(' OR ')})`;

export const buildFileDatasetSearch = (filters: FileDatasetFilters): string | undefined => {
  const keyword = filters.keyword?.trim();
  const conditions = [
    keyword ? contains('name', keyword) : undefined,
    filters.directoryIds?.length ? anyEquals('directoryId', filters.directoryIds) : undefined,
    filters.uncategorized ? 'directoryId:null' : undefined,
    filters.type ? equals('type', filters.type) : undefined,
  ].filter((condition): condition is string => Boolean(condition));

  return conditions.length ? conditions.join(' AND ') : undefined;
};
