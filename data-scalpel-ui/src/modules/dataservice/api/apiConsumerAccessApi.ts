import { requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type {
  ApiConsumerCredential,
  ApiConsumerCredentialSecretResponse,
  ApiServiceSubscription,
  ApiServiceSubscriptionFilters,
  CreateApiConsumerCredentialRequest,
  CreateApiServiceSubscriptionRequest,
} from '../model/apiConsumerAccess';

const API_CONSUMER_PATH = '/v1/api-consumers';
const SUBSCRIPTION_PATH = '/v1/api-service-subscriptions';

export const fetchApiConsumerCredentials = (consumerId: string): Promise<ApiConsumerCredential[]> => (
  requestJson<ApiConsumerCredential[]>(`${API_CONSUMER_PATH}/${consumerId}/credentials`)
);

export const createApiConsumerCredential = (
  consumerId: string,
  request: CreateApiConsumerCredentialRequest,
): Promise<ApiConsumerCredentialSecretResponse> => requestJson<ApiConsumerCredentialSecretResponse>(
  `${API_CONSUMER_PATH}/${consumerId}/credentials`,
  { method: 'POST', body: JSON.stringify(request) },
);

export const rotateApiConsumerCredential = (
  consumerId: string,
  credentialId: string,
): Promise<ApiConsumerCredentialSecretResponse> => requestJson<ApiConsumerCredentialSecretResponse>(
  `${API_CONSUMER_PATH}/${consumerId}/credentials/${credentialId}/actions/rotate`,
  { method: 'POST' },
);

export const reconcileApiConsumerCredentialGateway = (
  consumerId: string,
  credentialId: string,
): Promise<ApiConsumerCredential> => requestJson<ApiConsumerCredential>(
  `${API_CONSUMER_PATH}/${consumerId}/credentials/${credentialId}/actions/reconcile-gateway`,
  { method: 'POST' },
);

export const deleteApiConsumerCredential = (
  consumerId: string,
  credentialId: string,
): Promise<void> => requestJson<void>(
  `${API_CONSUMER_PATH}/${consumerId}/credentials/${credentialId}/actions/delete`,
  { method: 'POST' },
);

export const fetchApiServiceSubscriptions = (
  request: SearchRequest,
  filters: ApiServiceSubscriptionFilters = {},
): Promise<PageResponse<ApiServiceSubscription>> => {
  const params = toSearchParams(request);
  if (filters.consumerId) params.set('consumerId', filters.consumerId);
  if (filters.dataServiceId) params.set('dataServiceId', filters.dataServiceId);
  const query = params.toString();
  return requestJson<PageResponse<ApiServiceSubscription>>(
    query ? `${SUBSCRIPTION_PATH}?${query}` : SUBSCRIPTION_PATH,
  );
};

export const createApiServiceSubscription = (
  request: CreateApiServiceSubscriptionRequest,
): Promise<ApiServiceSubscription> => requestJson<ApiServiceSubscription>(
  SUBSCRIPTION_PATH,
  { method: 'POST', body: JSON.stringify(request) },
);

export const syncApiServiceSubscription = (id: string): Promise<ApiServiceSubscription> => (
  requestJson<ApiServiceSubscription>(`${SUBSCRIPTION_PATH}/${id}/actions/sync`, { method: 'POST' })
);

export const reconcileApiServiceSubscriptionGateway = (
  id: string,
): Promise<ApiServiceSubscription> => (
  requestJson<ApiServiceSubscription>(
    `${SUBSCRIPTION_PATH}/${id}/actions/reconcile-gateway`,
    { method: 'POST' },
  )
);

export const revokeApiServiceSubscription = (id: string): Promise<void> => (
  requestJson<void>(`${SUBSCRIPTION_PATH}/${id}/actions/revoke`, { method: 'POST' })
);
