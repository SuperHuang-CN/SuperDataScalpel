import type { PlatformDataType } from '../../model';
import type { GatewayProvider } from './apiConsumer';
import type { GatewayReconciliationState } from './gatewayReconciliation';

export type DataServiceType = 'STANDARD_TABLE' | 'SQL_QUERY';

export type DataServiceStatus = 'DRAFT' | 'ENABLED' | 'DISABLED';

export type DataServiceAccessMode = 'PUBLIC' | 'SUBSCRIPTION_REQUIRED';

export type DataServiceDeploymentStatus = 'PENDING' | 'DEPLOYED' | 'FAILED' | 'REMOVING' | 'REMOVED';

export type GatewayServicePublicationStatus =
  | 'PUBLISHING'
  | 'PUBLISHED'
  | 'PUBLISH_FAILED'
  | 'REMOVING'
  | 'REMOVE_FAILED';

export interface GatewayServiceBinding extends GatewayReconciliationState {
  id: string;
  provider: GatewayProvider;
  externalServiceId: string | null;
  externalRouteId: string | null;
  publishedRevision: number;
  publicationStatus: GatewayServicePublicationStatus;
  gatewayUrl: string | null;
  lastError: string | null;
  operationStartedAt: string | null;
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PlatformTypeDefinition {
  type: PlatformDataType;
  length: number | null;
  precision: number | null;
  scale: number | null;
}

export interface SqlServiceParameterDefinition {
  name: string;
  typeDefinition: PlatformTypeDefinition;
  required: boolean;
  description: string | null;
}

export interface SqlServiceResultFieldDefinition {
  name: string;
  typeDefinition: PlatformTypeDefinition;
  nullable: boolean;
}

export interface StandardDataServiceDefinition {
  modelId: string;
  version: number;
}

export interface SqlDataServiceDefinition {
  dataSourceId: string;
  modelIds: string[];
  sqlText: string;
  parameters: SqlServiceParameterDefinition[];
  version: number;
}

interface DataServiceBase {
  id: string;
  code: string;
  name: string;
  directoryId: string | null;
  type: DataServiceType;
  engineId: string;
  routePath: string;
  accessMode: DataServiceAccessMode;
  status: DataServiceStatus;
  revision: number;
  deploymentStatus: DataServiceDeploymentStatus | null;
  deploymentError: string | null;
  deployedAt: string | null;
  gatewayBindings: GatewayServiceBinding[];
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DataServiceSummary extends DataServiceBase {
  sourceId: string | null;
  sourceName: string;
}

export interface DataServiceDetail extends DataServiceBase {
  standardDefinition: StandardDataServiceDefinition | null;
  sqlDefinition: SqlDataServiceDefinition | null;
}

export interface StandardDataServiceDefinitionRequest {
  modelId: string;
}

export interface SqlDataServiceDefinitionRequest {
  dataSourceId: string;
  modelIds: string[];
  sqlText: string;
  parameters: SqlServiceParameterDefinition[];
}

interface DataServiceWriteRequest {
  name: string;
  directoryId?: string;
  engineId: string;
  routePath: string;
  accessMode: DataServiceAccessMode;
  type: DataServiceType;
  standardDefinition: StandardDataServiceDefinitionRequest | null;
  sqlDefinition: SqlDataServiceDefinitionRequest | null;
  description?: string;
}

export interface CreateDataServiceRequest extends DataServiceWriteRequest {
  code: string;
}

export type UpdateDataServiceRequest = DataServiceWriteRequest;

export interface SqlServiceTestRequest extends SqlDataServiceDefinitionRequest {
  arguments: Record<string, unknown>;
  previewSize: number;
}

export interface ServiceQueryResponse {
  pageNo: number;
  pageSize: number;
  totalCount: number | null;
  resultList: Record<string, unknown>[];
}

export interface SqlServiceTestProblem {
  code: string;
  message: string;
  subject: string | null;
}

export interface SqlServiceTestResponse {
  valid: boolean;
  problems: SqlServiceTestProblem[];
  resultFields: SqlServiceResultFieldDefinition[];
  preview: ServiceQueryResponse | null;
  elapsedMs: number;
}

export interface DataServiceFilters {
  keyword?: string;
  status?: DataServiceStatus;
  type?: DataServiceType;
  engineId?: string;
  directoryIds?: string[];
  uncategorized?: boolean;
}

export const dataServiceTypeLabels: Record<DataServiceType, string> = {
  STANDARD_TABLE: '标准单表',
  SQL_QUERY: 'SQL 查询',
};

export const dataServiceStatusLabels: Record<DataServiceStatus, string> = {
  DRAFT: '草稿',
  ENABLED: '已启用',
  DISABLED: '已停用',
};

export const dataServiceAccessModeLabels: Record<DataServiceAccessMode, string> = {
  PUBLIC: '公开访问',
  SUBSCRIPTION_REQUIRED: '订阅访问',
};

export const dataServiceDeploymentStatusLabels: Record<DataServiceDeploymentStatus, string> = {
  PENDING: '启用中',
  DEPLOYED: 'Engine 已部署',
  FAILED: 'Engine 操作失败',
  REMOVING: 'Engine 停用中',
  REMOVED: 'Engine 已移除',
};

export const gatewayServicePublicationStatusLabels: Record<GatewayServicePublicationStatus, string> = {
  PUBLISHING: '发布中',
  PUBLISHED: '已发布',
  PUBLISH_FAILED: '发布失败',
  REMOVING: '撤回中',
  REMOVE_FAILED: '撤回失败',
};
