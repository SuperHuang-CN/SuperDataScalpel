import { requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type {
  ComputeEngine,
  ComputeEngineTestResult,
  CreateComputeEngineRequest,
  DetachComputeEngineRequest,
  UpdateComputeEngineRequest,
} from '../model/computeEngine';

const COMPUTE_ENGINE_PATH = '/v1/compute-engines';

export const fetchComputeEngines = (request: SearchRequest): Promise<PageResponse<ComputeEngine>> => {
  const query = toSearchParams(request).toString();
  return requestJson<PageResponse<ComputeEngine>>(query ? `${COMPUTE_ENGINE_PATH}?${query}` : COMPUTE_ENGINE_PATH);
};

export const createComputeEngine = (request: CreateComputeEngineRequest): Promise<ComputeEngine> => (
  requestJson<ComputeEngine>(COMPUTE_ENGINE_PATH, { method: 'POST', body: JSON.stringify(request) })
);

export const updateComputeEngine = (id: string, request: UpdateComputeEngineRequest): Promise<ComputeEngine> => (
  requestJson<ComputeEngine>(`${COMPUTE_ENGINE_PATH}/${id}/actions/update`, {
    method: 'POST', body: JSON.stringify(request),
  })
);

export const reconfigureComputeEngine = (id: string, request: UpdateComputeEngineRequest): Promise<ComputeEngine> => (
  requestJson<ComputeEngine>(`${COMPUTE_ENGINE_PATH}/${id}/actions/reconfigure`, {
    method: 'POST', body: JSON.stringify(request),
  })
);

export const testComputeEngine = (id: string): Promise<ComputeEngineTestResult> => (
  requestJson<ComputeEngineTestResult>(`${COMPUTE_ENGINE_PATH}/${id}/actions/test`, { method: 'POST' })
);

export type ComputeEngineCommand = 'register' | 'drain';

export const executeComputeEngineCommand = (id: string, command: ComputeEngineCommand): Promise<ComputeEngine> => (
  requestJson<ComputeEngine>(`${COMPUTE_ENGINE_PATH}/${id}/actions/${command}`, { method: 'POST' })
);

export const deactivateComputeEngine = (id: string, force: boolean): Promise<ComputeEngine> => (
  requestJson<ComputeEngine>(`${COMPUTE_ENGINE_PATH}/${id}/actions/deactivate`, {
    method: 'POST', body: JSON.stringify({ force }),
  })
);

export const detachComputeEngine = (id: string, request: DetachComputeEngineRequest): Promise<ComputeEngine> => (
  requestJson<ComputeEngine>(`${COMPUTE_ENGINE_PATH}/${id}/actions/detach`, {
    method: 'POST', body: JSON.stringify(request),
  })
);

export const deleteComputeEngine = (id: string): Promise<void> => (
  requestJson<void>(`${COMPUTE_ENGINE_PATH}/${id}/actions/delete`, { method: 'POST' })
);
