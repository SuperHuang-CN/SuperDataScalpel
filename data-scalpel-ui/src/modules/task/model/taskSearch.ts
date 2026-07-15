import type { TaskFilters } from './task';

const escapeDslText = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');

const equals = (field: string, value: string) => `${field}:"${escapeDslText(value)}"`;

export const buildTaskSearch = (filters: TaskFilters): string | undefined => {
  const conditions = [
    filters.keyword?.trim()
      ? `(name:*"${escapeDslText(filters.keyword.trim())}"* OR code:*"${escapeDslText(filters.keyword.trim())}"*)`
      : undefined,
    filters.status ? equals('status', filters.status) : undefined,
    filters.directoryIds?.length
      ? filters.directoryIds.length === 1
        ? equals('directoryId', filters.directoryIds[0])
        : `(${filters.directoryIds.map((id) => equals('directoryId', id)).join(' OR ')})`
      : undefined,
    filters.uncategorized ? 'directoryId:null' : undefined,
  ].filter((condition): condition is string => Boolean(condition));

  return conditions.length ? conditions.join(' AND ') : undefined;
};
