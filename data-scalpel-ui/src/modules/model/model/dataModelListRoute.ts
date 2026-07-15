import type { DirectorySelection } from '../../directory';
import type { DataModelFilters, DataModelStatus } from './dataModel';

export interface DataModelListRouteState {
  filters: DataModelFilters;
  directorySelection: DirectorySelection;
  page: number;
  size: number;
}

const statuses: DataModelStatus[] = ['DRAFT', 'PUBLISHED', 'DISABLED'];

const positiveNumber = (value: string | null, fallback: number) => {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
};

export const parseDataModelListRoute = (params: URLSearchParams): DataModelListRouteState => {
  const status = params.get('status');
  const directory = params.get('directory');
  const directorySelection = directory === 'uncategorized' ? null : directory || undefined;
  return {
    filters: {
      ...(params.get('keyword') ? { keyword: params.get('keyword') ?? undefined } : {}),
      ...(status && statuses.includes(status as DataModelStatus) ? { status: status as DataModelStatus } : {}),
      ...(params.get('storage') ? { storageDataSourceId: params.get('storage') ?? undefined } : {}),
      ...(directorySelection === null ? { uncategorized: true } : {}),
    },
    directorySelection,
    page: positiveNumber(params.get('page'), 1) - 1,
    size: positiveNumber(params.get('size'), 20),
  };
};

export const serializeDataModelListRoute = (
  filters: DataModelFilters,
  directorySelection: DirectorySelection,
  page: number,
  size: number,
) => {
  const params = new URLSearchParams();
  if (filters.keyword?.trim()) params.set('keyword', filters.keyword.trim());
  if (filters.status) params.set('status', filters.status);
  if (filters.storageDataSourceId) params.set('storage', filters.storageDataSourceId);
  if (directorySelection === null) params.set('directory', 'uncategorized');
  else if (directorySelection) params.set('directory', directorySelection);
  if (page > 0) params.set('page', String(page + 1));
  if (size !== 20) params.set('size', String(size));
  return params;
};
