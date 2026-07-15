import { requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type {
  CreateDataServiceRequest,
  DataService,
  UpdateDataServiceRequest,
} from '../model/dataService';

const DATA_SERVICE_PATH = '/v1/data-services';

const searchPath = (path: string, request: SearchRequest) => {
  const query = toSearchParams(request).toString();
  return query ? `${path}?${query}` : path;
};

export const fetchDataServices = (request: SearchRequest): Promise<PageResponse<DataService>> => (
  requestJson<PageResponse<DataService>>(searchPath(DATA_SERVICE_PATH, request))
);

export const createDataService = (request: CreateDataServiceRequest): Promise<DataService> => (
  requestJson<DataService>(DATA_SERVICE_PATH, { method: 'POST', body: JSON.stringify(request) })
);

export const updateDataService = (id: string, request: UpdateDataServiceRequest): Promise<DataService> => (
  requestJson<DataService>(`${DATA_SERVICE_PATH}/${id}/actions/update`, { method: 'POST', body: JSON.stringify(request) })
);

export const publishDataService = (id: string): Promise<DataService> => (
  requestJson<DataService>(`${DATA_SERVICE_PATH}/${id}/actions/publish`, { method: 'POST' })
);

export const disableDataService = (id: string): Promise<DataService> => (
  requestJson<DataService>(`${DATA_SERVICE_PATH}/${id}/actions/disable`, { method: 'POST' })
);

export const deleteDataService = (id: string): Promise<void> => (
  requestJson<void>(`${DATA_SERVICE_PATH}/${id}/actions/delete`, { method: 'POST' })
);
