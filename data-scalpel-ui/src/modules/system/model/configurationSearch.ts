import type { SystemConfigurationFilters } from './systemConfiguration';

const escapeDslText = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');

const contains = (field: string, value: string) => `${field}:*"${escapeDslText(value)}"*`;

/** Creates the Search DSL for the two filters exposed by the configuration page. */
export const buildSystemConfigurationSearch = (filters: SystemConfigurationFilters): string | undefined => {
  const conditions = [
    filters.name?.trim() ? contains('name', filters.name.trim()) : undefined,
    filters.configKey?.trim() ? contains('configKey', filters.configKey.trim()) : undefined,
  ].filter((condition): condition is string => Boolean(condition));

  return conditions.length ? conditions.join(' AND ') : undefined;
};
