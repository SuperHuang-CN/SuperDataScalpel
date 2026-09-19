import type { DataServiceAccessMode } from './dataService';
import type { GatewayProvider } from './apiConsumer';
import type { GatewayReconciliationState } from './gatewayReconciliation';

export type GatewayCredentialStatus =
  | 'SYNC_PENDING'
  | 'ACTIVE'
  | 'SYNC_FAILED'
  | 'DELETE_PENDING'
  | 'DELETE_FAILED';

export interface GatewayCredentialBinding extends GatewayReconciliationState {
  id: string;
  provider: GatewayProvider;
  externalId: string | null;
  syncedRevision: number;
  status: GatewayCredentialStatus;
  lastError: string | null;
  operationId: string | null;
  operationStartedAt: string | null;
  lastSyncedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ApiConsumerCredential {
  id: string;
  consumerId: string;
  name: string;
  secretHint: string;
  revision: number;
  gatewayBindings: GatewayCredentialBinding[];
  createdAt: string;
  updatedAt: string;
}

export interface ApiConsumerCredentialSecretResponse {
  credential: ApiConsumerCredential;
  secret: string | null;
}

export interface CreateApiConsumerCredentialRequest {
  name: string;
}

export type ApiServiceSubscriptionDesiredState = 'GRANTED' | 'REVOKED';

export type GatewaySubscriptionStatus =
  | 'GRANT_PENDING'
  | 'GRANTED'
  | 'GRANT_FAILED'
  | 'REVOKE_PENDING'
  | 'REVOKE_FAILED';

export interface GatewaySubscriptionBinding extends GatewayReconciliationState {
  id: string;
  provider: GatewayProvider;
  externalMembershipId: string | null;
  status: GatewaySubscriptionStatus;
  lastError: string | null;
  operationId: string | null;
  operationStartedAt: string | null;
  grantedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ApiServiceSubscription {
  id: string;
  consumerId: string;
  consumerCode: string;
  consumerName: string;
  dataServiceId: string;
  dataServiceCode: string;
  dataServiceName: string;
  routePath: string | null;
  accessMode: DataServiceAccessMode | null;
  desiredState: ApiServiceSubscriptionDesiredState;
  gatewayBindings: GatewaySubscriptionBinding[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateApiServiceSubscriptionRequest {
  consumerId: string;
  dataServiceId: string;
}

export interface ApiServiceSubscriptionFilters {
  consumerId?: string;
  dataServiceId?: string;
}

export const gatewayCredentialStatusLabels: Record<GatewayCredentialStatus, string> = {
  SYNC_PENDING: '同步中',
  ACTIVE: '可用',
  SYNC_FAILED: '同步失败',
  DELETE_PENDING: '删除中',
  DELETE_FAILED: '删除失败',
};

export const gatewaySubscriptionStatusLabels: Record<GatewaySubscriptionStatus, string> = {
  GRANT_PENDING: '授权中',
  GRANTED: '已授权',
  GRANT_FAILED: '授权失败',
  REVOKE_PENDING: '撤回中',
  REVOKE_FAILED: '撤回失败',
};

export const apiServiceSubscriptionDesiredStateLabels: Record<
  ApiServiceSubscriptionDesiredState,
  string
> = {
  GRANTED: '已授权',
  REVOKED: '正在撤回',
};
