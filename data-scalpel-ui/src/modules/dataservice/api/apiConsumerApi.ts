import { requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type {
  ApiConsumer,
  CreateApiConsumerRequest,
  UpdateApiConsumerRequest,
} from '../model/apiConsumer';

const API_CONSUMER_PATH = '/v1/api-consumers';

const searchPath = (request: SearchRequest) => {
  const query = toSearchParams(request).toString();
  return query ? `${API_CONSUMER_PATH}?${query}` : API_CONSUMER_PATH;
};

export const fetchApiConsumers = (request: SearchRequest): Promise<PageResponse<ApiConsumer>> => (
  requestJson<PageResponse<ApiConsumer>>(searchPath(request))
);

export const createApiConsumer = (request: CreateApiConsumerRequest): Promise<ApiConsumer> => (
  requestJson<ApiConsumer>(API_CONSUMER_PATH, {
    method: 'POST',
    body: JSON.stringify(request),
  })
);

export const updateApiConsumer = (id: string, request: UpdateApiConsumerRequest): Promise<ApiConsumer> => (
  requestJson<ApiConsumer>(`${API_CONSUMER_PATH}/${id}/actions/update`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
);

export const syncApiConsumer = (id: string): Promise<ApiConsumer> => (
  requestJson<ApiConsumer>(`${API_CONSUMER_PATH}/${id}/actions/sync`, { method: 'POST' })
);

export const reconcileApiConsumerGateway = (id: string): Promise<ApiConsumer> => (
  requestJson<ApiConsumer>(`${API_CONSUMER_PATH}/${id}/actions/reconcile-gateway`, { method: 'POST' })
);

export const deleteApiConsumer = (id: string): Promise<void> => (
  requestJson<void>(`${API_CONSUMER_PATH}/${id}/actions/delete`, { method: 'POST' })
);
