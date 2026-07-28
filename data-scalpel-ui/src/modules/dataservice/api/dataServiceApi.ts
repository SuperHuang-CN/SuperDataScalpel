import { requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type {
  CreateDataServiceRequest,
  DataServiceDetail,
  DataServiceSummary,
  SqlServiceTestRequest,
  SqlServiceTestResponse,
  UpdateDataServiceRequest,
} from '../model/dataService';

const DATA_SERVICE_PATH = '/v1/data-services';

const searchPath = (path: string, request: SearchRequest) => {
  const query = toSearchParams(request).toString();
  return query ? `${path}?${query}` : path;
};

export const fetchDataServices = (request: SearchRequest): Promise<PageResponse<DataServiceSummary>> => (
  requestJson<PageResponse<DataServiceSummary>>(searchPath(DATA_SERVICE_PATH, request))
);

export const fetchDataService = (id: string): Promise<DataServiceDetail> => (
  requestJson<DataServiceDetail>(`${DATA_SERVICE_PATH}/${id}`)
);

export const createDataService = (request: CreateDataServiceRequest): Promise<DataServiceDetail> => (
  requestJson<DataServiceDetail>(DATA_SERVICE_PATH, { method: 'POST', body: JSON.stringify(request) })
);

export const updateDataService = (id: string, request: UpdateDataServiceRequest): Promise<DataServiceDetail> => (
  requestJson<DataServiceDetail>(`${DATA_SERVICE_PATH}/${id}/actions/update`, { method: 'POST', body: JSON.stringify(request) })
);

export const testSqlDataService = (request: SqlServiceTestRequest): Promise<SqlServiceTestResponse> => (
  requestJson<SqlServiceTestResponse>(`${DATA_SERVICE_PATH}/actions/test-sql`, {
    method: 'POST', body: JSON.stringify(request),
  })
);

export const enableDataService = (id: string): Promise<DataServiceDetail> => (
  requestJson<DataServiceDetail>(`${DATA_SERVICE_PATH}/${id}/actions/enable`, { method: 'POST' })
);

export const publishDataService = (id: string): Promise<DataServiceDetail> => (
  requestJson<DataServiceDetail>(`${DATA_SERVICE_PATH}/${id}/actions/publish`, { method: 'POST' })
);

export const reconcileDataServiceGateway = (id: string): Promise<DataServiceDetail> => (
  requestJson<DataServiceDetail>(`${DATA_SERVICE_PATH}/${id}/actions/reconcile-gateway`, { method: 'POST' })
);

export const unpublishDataService = (id: string): Promise<DataServiceDetail> => (
  requestJson<DataServiceDetail>(`${DATA_SERVICE_PATH}/${id}/actions/unpublish`, { method: 'POST' })
);

export const disableDataService = (id: string): Promise<DataServiceDetail> => (
  requestJson<DataServiceDetail>(`${DATA_SERVICE_PATH}/${id}/actions/disable`, { method: 'POST' })
);

export const cleanupDataServiceDeployment = (id: string): Promise<DataServiceDetail> => (
  requestJson<DataServiceDetail>(`${DATA_SERVICE_PATH}/${id}/actions/cleanup-deployment`, { method: 'POST' })
);

export const deleteDataService = (id: string): Promise<void> => (
  requestJson<void>(`${DATA_SERVICE_PATH}/${id}/actions/delete`, { method: 'POST' })
);
