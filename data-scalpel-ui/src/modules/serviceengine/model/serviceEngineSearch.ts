import type { ServiceEngineFilters } from './serviceEngine';

const escapeDslText = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');

const contains = (field: string, value: string) => `${field}:*"${escapeDslText(value)}"*`;

const equals = (field: string, value: string | boolean) => `${field}:"${String(value)}"`;

export const buildServiceEngineSearch = (filters: ServiceEngineFilters): string | undefined => {
  const conditions = [
    filters.keyword?.trim()
      ? `(${contains('name', filters.keyword.trim())} OR ${contains('code', filters.keyword.trim())})`
      : undefined,
    filters.enabled === undefined ? undefined : equals('enabled', filters.enabled),
    filters.type ? equals('type', filters.type) : undefined,
  ].filter((condition): condition is string => Boolean(condition));

  return conditions.length ? conditions.join(' AND ') : undefined;
};
