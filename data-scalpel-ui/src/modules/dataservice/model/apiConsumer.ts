export type GatewayProvider = 'KONG' | 'APISIX' | 'DATASCALPEL';

export type GatewayConsumerSyncStatus =
  | 'SYNC_PENDING'
  | 'SYNCED'
  | 'SYNC_FAILED'
  | 'DELETE_PENDING'
  | 'DELETE_FAILED';

export interface GatewayConsumerBinding extends GatewayReconciliationState {
  id: string;
  provider: GatewayProvider;
  externalId: string | null;
  syncedRevision: number;
  syncStatus: GatewayConsumerSyncStatus;
  lastError: string | null;
  operationStartedAt: string | null;
  lastSyncedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ApiConsumer {
  id: string;
  code: string;
  name: string;
  description: string | null;
  revision: number;
  gatewayBindings: GatewayConsumerBinding[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateApiConsumerRequest {
  code: string;
  name: string;
  description?: string;
}

export interface UpdateApiConsumerRequest {
  name: string;
  description?: string;
}

export interface ApiConsumerFilters {
  keyword?: string;
}

export const gatewayProviderLabels: Record<GatewayProvider, string> = {
  KONG: 'Kong',
  APISIX: 'APISIX',
  DATASCALPEL: 'DataScalpel',
};

export const gatewayConsumerSyncStatusLabels: Record<GatewayConsumerSyncStatus, string> = {
  SYNC_PENDING: '同步中',
  SYNCED: '已同步',
  SYNC_FAILED: '同步失败',
  DELETE_PENDING: '删除中',
  DELETE_FAILED: '删除失败',
};
import type { GatewayReconciliationState } from './gatewayReconciliation';
