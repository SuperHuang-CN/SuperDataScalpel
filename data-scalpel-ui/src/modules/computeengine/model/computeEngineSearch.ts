import type { ComputeEngineFilters } from './computeEngine';

const escapeDslText = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');
const contains = (field: string, value: string) => `${field}:*"${escapeDslText(value)}"*`;
const equals = (field: string, value: string) => `${field}:"${escapeDslText(value)}"`;

export const buildComputeEngineSearch = (filters: ComputeEngineFilters): string | undefined => {
  const keyword = filters.keyword?.trim();
  const conditions = [
    keyword ? contains('name', keyword) : undefined,
    filters.expectedBackendType ? equals('expectedBackendType', filters.expectedBackendType) : undefined,
    filters.registrationState ? equals('registrationState', filters.registrationState) : undefined,
    filters.healthState ? equals('healthState', filters.healthState) : undefined,
  ].filter((condition): condition is string => Boolean(condition));
  return conditions.length ? conditions.join(' AND ') : undefined;
};
