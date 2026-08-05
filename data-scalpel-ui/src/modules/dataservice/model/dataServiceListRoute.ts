import type { DirectorySelection } from '../../directory';
import type { DataServiceFilters, DataServiceStatus, DataServiceType } from './dataService';

export interface DataServiceListRouteState {
  filters: DataServiceFilters;
  directorySelection: DirectorySelection;
  page: number;
  size: number;
}

const statuses: DataServiceStatus[] = ['DRAFT', 'ENABLED', 'DISABLED'];
const types: DataServiceType[] = ['STANDARD_TABLE', 'SQL_QUERY', 'SCRIPT_API'];
const pageSizes = [10, 20, 50, 100];

const positiveNumber = (value: string | null, fallback: number) => {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
};

export const parseDataServiceListRoute = (params: URLSearchParams): DataServiceListRouteState => {
  const status = params.get('status');
  const type = params.get('type');
  const directory = params.get('directory');
  const directorySelection = directory === 'uncategorized' ? null : directory || undefined;
  return {
    filters: {
      ...(params.get('keyword') ? { keyword: params.get('keyword') ?? undefined } : {}),
      ...(status && statuses.includes(status as DataServiceStatus) ? { status: status as DataServiceStatus } : {}),
      ...(type && types.includes(type as DataServiceType) ? { type: type as DataServiceType } : {}),
      ...(params.get('engine') ? { engineId: params.get('engine') ?? undefined } : {}),
      ...(directorySelection === null ? { uncategorized: true } : {}),
    },
    directorySelection,
    page: positiveNumber(params.get('page'), 1) - 1,
    size: pageSizes.includes(positiveNumber(params.get('size'), 20))
      ? positiveNumber(params.get('size'), 20)
      : 20,
  };
};

export const serializeDataServiceListRoute = (
  filters: DataServiceFilters,
  directorySelection: DirectorySelection,
  page: number,
  size: number,
): URLSearchParams => {
  const params = new URLSearchParams();
  if (filters.keyword?.trim()) params.set('keyword', filters.keyword.trim());
  if (filters.status) params.set('status', filters.status);
  if (filters.type) params.set('type', filters.type);
  if (filters.engineId) params.set('engine', filters.engineId);
  if (directorySelection === null) params.set('directory', 'uncategorized');
  else if (directorySelection) params.set('directory', directorySelection);
  if (page > 0) params.set('page', String(page + 1));
  if (size !== 20) params.set('size', String(size));
  return params;
};
