import { requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type {
  CreateServiceEngineRequest,
  ServiceEngine,
  ServiceEngineDataSourceRegistration,
  ServiceEngineDataSourceTestResult,
  ServiceEngineAccessPolicy,
  ServiceEngineTestResult,
  TestServiceEngineRequest,
  TestStoredServiceEngineRequest,
  UpdateServiceEngineRequest,
  UpdateServiceEngineAccessPolicyRequest,
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

export const fetchServiceEngine = (id: string): Promise<ServiceEngine> => (
  requestJson<ServiceEngine>(`${SERVICE_ENGINE_PATH}/${id}`)
);

export const createServiceEngine = (request: CreateServiceEngineRequest): Promise<ServiceEngine> => (
  requestJson<ServiceEngine>(SERVICE_ENGINE_PATH, { method: 'POST', body: JSON.stringify(request) })
);

export const updateServiceEngine = (id: string, request: UpdateServiceEngineRequest): Promise<ServiceEngine> => (
  requestJson<ServiceEngine>(`${SERVICE_ENGINE_PATH}/${id}/actions/update`, { method: 'POST', body: JSON.stringify(request) })
);

export const testNewServiceEngine = (request: TestServiceEngineRequest): Promise<ServiceEngineTestResult> => (
  requestJson<ServiceEngineTestResult>(`${SERVICE_ENGINE_PATH}/actions/test`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
);

export const testServiceEngine = (
  id: string,
  request?: TestStoredServiceEngineRequest,
): Promise<ServiceEngineTestResult> => (
  requestJson<ServiceEngineTestResult>(`${SERVICE_ENGINE_PATH}/${id}/actions/test`, {
    method: 'POST',
    body: request ? JSON.stringify(request) : undefined,
  })
);

export const deleteServiceEngine = (id: string): Promise<void> => (
  requestJson<void>(`${SERVICE_ENGINE_PATH}/${id}/actions/delete`, { method: 'POST' })
);

export const fetchServiceEngineAccessPolicy = (id: string): Promise<ServiceEngineAccessPolicy> => (
  requestJson<ServiceEngineAccessPolicy>(`${SERVICE_ENGINE_PATH}/${id}/access-policy`)
);

export const updateServiceEngineAccessPolicy = (
  id: string,
  request: UpdateServiceEngineAccessPolicyRequest,
): Promise<ServiceEngineAccessPolicy> => requestJson<ServiceEngineAccessPolicy>(
  `${SERVICE_ENGINE_PATH}/${id}/actions/update-access-policy`,
  { method: 'POST', body: JSON.stringify(request) },
);

export const syncServiceEngineAccessPolicy = (id: string): Promise<ServiceEngineAccessPolicy> => (
  requestJson<ServiceEngineAccessPolicy>(`${SERVICE_ENGINE_PATH}/${id}/actions/sync-access-policy`, {
    method: 'POST', body: JSON.stringify({}),
  })
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
