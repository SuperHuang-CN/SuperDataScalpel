import type { DataModelStatus, PlatformDataType } from '../../model';
import type { GatewayProvider } from './apiConsumer';
import type { GatewayReconciliationState } from './gatewayReconciliation';
import type { ScriptRequestExample } from '@superhuang/super-api-studio-script-workbench';
import type {
  CartographyField, SpatialGeometryFamily, SpatialStyleDocument, SpatialStyleMode,
} from '../../cartography';
export type {
  SpatialGeometryFamily, SpatialLinePattern, SpatialMarkerShape, SpatialStyleDocument,
  SpatialStyleMode, SpatialSymbol,
} from '../../cartography';

export type DataServiceType = 'STANDARD_TABLE' | 'SQL_QUERY' | 'SCRIPT_API' | 'SPATIAL_SERVICE';

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
  gatewayRoutePath: string;
  accessMode: DataServiceAccessMode;
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

export interface ScriptDataServiceDefinition {
  dataSourceId: string;
  script: string;
  examples: ScriptRequestExample[];
  version: number;
}

export interface SpatialDataServiceDefinition {
  modelId: string;
  version: number;
}

export interface SpatialDataServicePreview {
  available: boolean;
  message: string | null;
  qualifiedLayerName: string;
  displayCrs: string;
  initialBounds: number[];
  limits: {
    minimumWidth: number;
    maximumWidth: number;
    minimumHeight: number;
    maximumHeight: number;
  };
}

export type SpatialStyleSyncStatus = 'NOT_APPLIED' | 'OUT_OF_SYNC' | 'SYNCING' | 'IN_SYNC' | 'SYNC_FAILED';

export interface SpatialDataServiceStyle {
  mode: SpatialStyleMode;
  geometryKind: string;
  geometryFamily: SpatialGeometryFamily;
  simpleEditable: boolean;
  styleDocument: SpatialStyleDocument | null;
  defaultStyleDocument: SpatialStyleDocument | null;
  fields: CartographyField[];
  sldFileName: string | null;
  sldFileSize: number | null;
  uploadedSldText: string | null;
  styleVersion: number;
  appliedStyleVersion: number | null;
  syncStatus: SpatialStyleSyncStatus;
  syncError: string | null;
  appliedAt: string | null;
  deployed: boolean;
}

interface DataServiceBase {
  id: string;
  code: string;
  name: string;
  directoryId: string | null;
  type: DataServiceType;
  definitionConfigured: boolean;
  definitionVersion: number | null;
  engineId: string;
  contextPath: string | null;
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
  sourceName: string | null;
}

export interface DataServiceDetail extends DataServiceBase {
  standardDefinition: StandardDataServiceDefinition | null;
  sqlDefinition: SqlDataServiceDefinition | null;
  scriptDefinition: ScriptDataServiceDefinition | null;
  spatialDefinition?: SpatialDataServiceDefinition | null;
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

export interface ScriptDataServiceDefinitionRequest {
  dataSourceId: string;
  script: string;
  examples: ScriptRequestExample[];
}

export interface SpatialDataServiceDefinitionRequest {
  modelId: string;
}

interface DataServiceWriteRequest {
  name: string;
  directoryId?: string;
  engineId: string;
  contextPath: string | null;
  type: DataServiceType;
  standardDefinition: StandardDataServiceDefinitionRequest | null;
  sqlDefinition: SqlDataServiceDefinitionRequest | null;
  scriptDefinition: ScriptDataServiceDefinitionRequest | null;
  spatialDefinition?: SpatialDataServiceDefinitionRequest | null;
  description?: string;
}

export interface CreateDataServiceRequest extends DataServiceWriteRequest {
  code: string;
}

export type UpdateDataServiceRequest = DataServiceWriteRequest;

export interface PublishDataServiceRequest {
  gatewayRoutePath: string;
  accessMode: DataServiceAccessMode;
}

export interface UpdateDataServiceDefinitionRequest {
  standardDefinition: StandardDataServiceDefinitionRequest | null;
  sqlDefinition: SqlDataServiceDefinitionRequest | null;
  scriptDefinition: ScriptDataServiceDefinitionRequest | null;
  spatialDefinition?: SpatialDataServiceDefinitionRequest | null;
}

export interface SpatialDataServiceModelCandidate {
  id: string;
  code: string;
  name: string;
  status: DataModelStatus;
  dataSourceId: string;
  dataSourceCode: string | null;
  dataSourceName: string | null;
  catalog: string | null;
  schema: string | null;
  table: string;
  geometryColumn: string | null;
  geometryKind: string | null;
  epsg: number | null;
  primaryKeyColumn: string | null;
  selectable: boolean;
  unavailableReason: string | null;
  updatedAt: string;
}

export interface StandardDataServiceModelCandidate {
  id: string;
  code: string;
  name: string;
  status: DataModelStatus;
  directoryId: string | null;
  directoryName: string | null;
  warehouseLayerId: string | null;
  warehouseLayerCode: string | null;
  warehouseLayerName: string | null;
  storageDataSourceId: string;
  storageDataSourceCode: string | null;
  storageDataSourceName: string | null;
  catalogName: string | null;
  schemaName: string | null;
  physicalTableName: string;
  schemaVersion: number;
  fieldCount: number;
  selectable: boolean;
  unavailableReason: string | null;
  updatedAt: string;
}

export type DataServiceRelatedModelRole = 'PRIMARY' | 'REFERENCE';

export interface DataServiceRelatedModel {
  modelId: string;
  role: DataServiceRelatedModelRole;
  order: number;
  resolved: boolean;
  code: string | null;
  name: string | null;
  status: DataModelStatus | null;
  directoryId: string | null;
  directoryName: string | null;
  warehouseLayerId: string | null;
  warehouseLayerCode: string | null;
  warehouseLayerName: string | null;
  storageDataSourceId: string | null;
  storageDataSourceCode: string | null;
  storageDataSourceName: string | null;
  catalogName: string | null;
  schemaName: string | null;
  physicalTableName: string | null;
  schemaVersion: number | null;
  updatedAt: string | null;
}

export interface SqlServiceTestRequest extends SqlDataServiceDefinitionRequest {
  arguments: Record<string, unknown>;
  previewSize: number;
}

export interface ServiceQueryResponse {
  pageNo: number;
  pageSize: number;
  totalCount: number | null;
  items: Record<string, unknown>[];
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
  SCRIPT_API: 'Groovy 脚本',
  SPATIAL_SERVICE: '空间服务',
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
