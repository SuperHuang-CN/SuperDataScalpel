import { requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type {
  CreateServiceEngineRequest,
  ServiceEngine,
  ServiceEngineDataSourceRegistration,
  ServiceEngineDataSourceTestResult,
  ServiceEngineTestResult,
  UpdateServiceEngineRequest,
} from '../model/serviceEngine';

const SERVICE_ENGINE_PATH = '/v1/service-engines';
const SERVICE_ENGINE_DATA_SOURCE_PATH = '/v1/service-engine-data-sources';

const searchPath = (path: string, request: SearchRequest) => {
  const query = toSearchParams(request).toString();
  return query ? `${path}?${query}` : path;
};

export const fetchServiceEngines = (request: SearchRequest): Promise<PageResponse<ServiceEngine>> => (
  requestJson<PageResponse<ServiceEngine>>(searchPath(SERVICE_ENGINE_PATH, request))
);

export const createServiceEngine = (request: CreateServiceEngineRequest): Promise<ServiceEngine> => (
  requestJson<ServiceEngine>(SERVICE_ENGINE_PATH, { method: 'POST', body: JSON.stringify(request) })
);

export const updateServiceEngine = (id: string, request: UpdateServiceEngineRequest): Promise<ServiceEngine> => (
  requestJson<ServiceEngine>(`${SERVICE_ENGINE_PATH}/${id}/actions/update`, { method: 'POST', body: JSON.stringify(request) })
);

export const testServiceEngine = (id: string): Promise<ServiceEngineTestResult> => (
  requestJson<ServiceEngineTestResult>(`${SERVICE_ENGINE_PATH}/${id}/actions/test`, { method: 'POST' })
);

export const deleteServiceEngine = (id: string): Promise<void> => (
  requestJson<void>(`${SERVICE_ENGINE_PATH}/${id}/actions/delete`, { method: 'POST' })
);

export const fetchServiceEngineDataSourceRegistrations = (
  request: SearchRequest,
): Promise<PageResponse<ServiceEngineDataSourceRegistration>> => (
  requestJson<PageResponse<ServiceEngineDataSourceRegistration>>(searchPath(SERVICE_ENGINE_DATA_SOURCE_PATH, request))
);

export const createServiceEngineDataSourceRegistration = (
  engineId: string,
  dataSourceId: string,
): Promise<ServiceEngineDataSourceRegistration> => (
  requestJson<ServiceEngineDataSourceRegistration>(SERVICE_ENGINE_DATA_SOURCE_PATH, {
    method: 'POST',
    body: JSON.stringify({ engineId, dataSourceId }),
  })
);

export const syncServiceEngineDataSourceRegistration = (
  id: string,
): Promise<ServiceEngineDataSourceRegistration> => (
  requestJson<ServiceEngineDataSourceRegistration>(`${SERVICE_ENGINE_DATA_SOURCE_PATH}/${id}/actions/sync`, {
    method: 'POST',
    body: JSON.stringify({}),
  })
);

export const testServiceEngineDataSourceRegistration = (
  id: string,
): Promise<ServiceEngineDataSourceTestResult> => (
  requestJson<ServiceEngineDataSourceTestResult>(`${SERVICE_ENGINE_DATA_SOURCE_PATH}/${id}/actions/test`, {
    method: 'POST',
    body: JSON.stringify({}),
  })
);

export const deleteServiceEngineDataSourceRegistration = (id: string): Promise<void> => (
  requestJson<void>(`${SERVICE_ENGINE_DATA_SOURCE_PATH}/${id}/actions/delete`, {
    method: 'POST',
    body: JSON.stringify({}),
  })
);
