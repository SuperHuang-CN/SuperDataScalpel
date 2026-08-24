import { requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type {
  ApiResource,
  ApiResourceTestResult,
  ConnectionTestResult,
  CreateApiResourceRequest,
  CreateDataSourceRequest,
  DataSource,
  DataSourceTypeDefinition,
  DataSourceNamespace,
  TableIdentifier,
  TableListResult,
  TableMetadata,
  TablePreview,
  TableQuery,
  TestDataSourceConnectionRequest,
  HttpApiRuntimeParameter,
  JdbcQueryInspection,
  KafkaTopic,
  TdEngineTmqTopic,
  TdEngineTmqTopicDetail,
  UpdateApiResourceRequest,
  UpdateDataSourceRequest,
  CreateSpatialFeatureResourceRequest,
  SpatialCatalogEntry,
  SpatialFeaturePreview,
  SpatialFeatureResource,
  UpdateSpatialFeatureResourceRequest,
  DataSourceRelationKind,
  DataSourceTaskRelationRole,
  DataSourceRelatedModel,
  DataSourceRelatedTask,
  DataSourceRelatedService,
} from '../model/dataSource';

const DATA_SOURCE_PATH = '/v1/data-sources';

export const fetchDataSourceTypes = (): Promise<DataSourceTypeDefinition[]> => (
  requestJson<DataSourceTypeDefinition[]>('/v1/data-source-types')
);

export const fetchDataSources = async (request: SearchRequest): Promise<PageResponse<DataSource>> => {
  const query = toSearchParams(request).toString();
  const path = query ? `${DATA_SOURCE_PATH}?${query}` : DATA_SOURCE_PATH;
  return requestJson<PageResponse<DataSource>>(path);
};

export const fetchDataSource = (id: string): Promise<DataSource> => (
  requestJson<DataSource>(`${DATA_SOURCE_PATH}/${id}`)
);

export const fetchDataSourceRelatedModels = async (
  id: string,
  request: SearchRequest,
): Promise<PageResponse<DataSourceRelatedModel>> => {
  const query = toSearchParams(request).toString();
  return requestJson<PageResponse<DataSourceRelatedModel>>(
    `${DATA_SOURCE_PATH}/${id}/related-models${query ? `?${query}` : ''}`,
  );
};

export const fetchDataSourceRelatedTasks = async (
  id: string,
  role: DataSourceTaskRelationRole | undefined,
  relationKind: DataSourceRelationKind | undefined,
  request: SearchRequest,
): Promise<PageResponse<DataSourceRelatedTask>> => {
  const query = toSearchParams(request);
  if (role) query.set('role', role);
  if (relationKind) query.set('relationKind', relationKind);
  return requestJson<PageResponse<DataSourceRelatedTask>>(
    `${DATA_SOURCE_PATH}/${id}/related-tasks${query.size ? `?${query.toString()}` : ''}`,
  );
};

export const fetchDataSourceRelatedServices = async (
  id: string,
  relationKind: DataSourceRelationKind | undefined,
  request: SearchRequest,
): Promise<PageResponse<DataSourceRelatedService>> => {
  const query = toSearchParams(request);
  if (relationKind) query.set('relationKind', relationKind);
  return requestJson<PageResponse<DataSourceRelatedService>>(
    `${DATA_SOURCE_PATH}/${id}/related-services${query.size ? `?${query.toString()}` : ''}`,
  );
};

export const createDataSource = (request: CreateDataSourceRequest): Promise<DataSource> => (
  requestJson<DataSource>(DATA_SOURCE_PATH, { method: 'POST', body: JSON.stringify(request) })
);

export const updateDataSource = (id: string, request: UpdateDataSourceRequest): Promise<DataSource> => (
  requestJson<DataSource>(`${DATA_SOURCE_PATH}/${id}/actions/update`, { method: 'POST', body: JSON.stringify(request) })
);

export const deleteDataSource = (id: string): Promise<void> => (
  requestJson<void>(`${DATA_SOURCE_PATH}/${id}/actions/delete`, { method: 'POST' })
);

export const testDraftDataSourceConnection = (
  request: TestDataSourceConnectionRequest,
): Promise<ConnectionTestResult> => (
  requestJson<ConnectionTestResult>(`${DATA_SOURCE_PATH}/actions/test`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
);

export const testSavedDataSourceConnection = (id: string): Promise<ConnectionTestResult> => (
  requestJson<ConnectionTestResult>(`${DATA_SOURCE_PATH}/${id}/actions/test`, { method: 'POST' })
);

const apiResourcePath = (dataSourceId: string) => `${DATA_SOURCE_PATH}/${dataSourceId}/api-resources`;

export const fetchApiResources = (dataSourceId: string): Promise<ApiResource[]> => (
  requestJson<ApiResource[]>(apiResourcePath(dataSourceId))
);

export const fetchApiResource = (dataSourceId: string, resourceId: string): Promise<ApiResource> => (
  requestJson<ApiResource>(`${apiResourcePath(dataSourceId)}/${resourceId}`)
);

export const createApiResource = (
  dataSourceId: string,
  request: CreateApiResourceRequest,
): Promise<ApiResource> => requestJson<ApiResource>(apiResourcePath(dataSourceId), {
  method: 'POST',
  body: JSON.stringify(request),
});

export const updateApiResource = (
  dataSourceId: string,
  resourceId: string,
  request: UpdateApiResourceRequest,
): Promise<ApiResource> => requestJson<ApiResource>(
  `${apiResourcePath(dataSourceId)}/${resourceId}/actions/update`,
  { method: 'POST', body: JSON.stringify(request) },
);

export const deleteApiResource = (dataSourceId: string, resourceId: string): Promise<void> => (
  requestJson<void>(`${apiResourcePath(dataSourceId)}/${resourceId}/actions/delete`, { method: 'POST' })
);

export const testApiResource = (
  dataSourceId: string,
  resourceId: string,
  runtimeParameters: HttpApiRuntimeParameter[],
): Promise<ApiResourceTestResult> => requestJson<ApiResourceTestResult>(
  `${apiResourcePath(dataSourceId)}/${resourceId}/actions/test`,
  { method: 'POST', body: JSON.stringify({ runtimeParameters }) },
  60_000,
);

const spatialResourcePath = (dataSourceId: string) => `${DATA_SOURCE_PATH}/${dataSourceId}/spatial-resources`;

export const fetchSpatialCatalog = (dataSourceId: string, parent?: string): Promise<SpatialCatalogEntry[]> => {
  const suffix = parent ? `?${new URLSearchParams({ parent }).toString()}` : '';
  return requestJson<SpatialCatalogEntry[]>(`${spatialResourcePath(dataSourceId)}/catalog${suffix}`);
};

export const fetchSpatialFeatureResources = (dataSourceId: string): Promise<SpatialFeatureResource[]> => (
  requestJson<SpatialFeatureResource[]>(spatialResourcePath(dataSourceId))
);

export const createSpatialFeatureResource = (
  dataSourceId: string,
  request: CreateSpatialFeatureResourceRequest,
): Promise<SpatialFeatureResource> => requestJson<SpatialFeatureResource>(spatialResourcePath(dataSourceId), {
  method: 'POST', body: JSON.stringify(request),
});

export const updateSpatialFeatureResource = (
  dataSourceId: string,
  resourceId: string,
  request: UpdateSpatialFeatureResourceRequest,
): Promise<SpatialFeatureResource> => requestJson<SpatialFeatureResource>(
  `${spatialResourcePath(dataSourceId)}/${resourceId}/actions/update`,
  { method: 'POST', body: JSON.stringify(request) },
);

export const refreshSpatialFeatureResourceSchema = (dataSourceId: string, resourceId: string): Promise<SpatialFeatureResource> => (
  requestJson<SpatialFeatureResource>(`${spatialResourcePath(dataSourceId)}/${resourceId}/actions/refresh-schema`, { method: 'POST' })
);

export const deleteSpatialFeatureResource = (dataSourceId: string, resourceId: string): Promise<void> => (
  requestJson<void>(`${spatialResourcePath(dataSourceId)}/${resourceId}/actions/delete`, { method: 'POST' })
);

export const previewSpatialFeatureResource = (
  dataSourceId: string, resourceId: string, limit = 50,
): Promise<SpatialFeaturePreview> => requestJson<SpatialFeaturePreview>(
  `${spatialResourcePath(dataSourceId)}/${resourceId}/actions/query-preview?${new URLSearchParams({ limit: String(limit) })}`,
  { method: 'POST' }, 60_000,
);

export const fetchDataSourceNamespaces = (id: string): Promise<DataSourceNamespace[]> => (
  requestJson<DataSourceNamespace[]>(`${DATA_SOURCE_PATH}/${id}/namespaces`)
);

export const fetchDataSourceTables = (id: string, query: TableQuery): Promise<TableListResult> => {
  const searchParams = new URLSearchParams();
  if (query.catalog) searchParams.set('catalog', query.catalog);
  if (query.schema) searchParams.set('schema', query.schema);
  if (query.keyword) searchParams.set('keyword', query.keyword);
  if (query.includeViews) searchParams.set('includeViews', 'true');
  if (query.limit !== undefined) searchParams.set('limit', String(query.limit));
  const suffix = searchParams.size ? `?${searchParams.toString()}` : '';
  return requestJson<TableListResult>(`${DATA_SOURCE_PATH}/${id}/tables${suffix}`);
};

const tableSearchParams = (table: TableIdentifier) => {
  const searchParams = new URLSearchParams({ table: table.table });
  if (table.catalog) searchParams.set('catalog', table.catalog);
  if (table.schema) searchParams.set('schema', table.schema);
  return searchParams;
};

export const fetchTableMetadata = (id: string, table: TableIdentifier): Promise<TableMetadata> => (
  requestJson<TableMetadata>(`${DATA_SOURCE_PATH}/${id}/table-metadata?${tableSearchParams(table).toString()}`)
);

export const inspectJdbcQuery = (id: string, sql: string): Promise<JdbcQueryInspection> => (
  requestJson<JdbcQueryInspection>(`${DATA_SOURCE_PATH}/${id}/actions/inspect-query`, {
    method: 'POST',
    body: JSON.stringify({ sql }),
  }, 60_000)
);

export const fetchTablePreview = (id: string, table: TableIdentifier, limit = 50): Promise<TablePreview> => {
  const searchParams = tableSearchParams(table);
  searchParams.set('limit', String(limit));
  return requestJson<TablePreview>(`${DATA_SOURCE_PATH}/${id}/table-preview?${searchParams.toString()}`);
};

export const fetchKafkaTopics = (
  id: string,
  keyword?: string,
  includeInternal = false,
): Promise<KafkaTopic[]> => {
  const searchParams = new URLSearchParams();
  if (keyword?.trim()) searchParams.set('keyword', keyword.trim());
  if (includeInternal) searchParams.set('includeInternal', 'true');
  const suffix = searchParams.size ? `?${searchParams.toString()}` : '';
  return requestJson<KafkaTopic[]>(`${DATA_SOURCE_PATH}/${id}/kafka-topics${suffix}`, {}, 60_000);
};

export const fetchTdEngineTmqTopics = (
  id: string,
  keyword?: string,
): Promise<TdEngineTmqTopic[]> => {
  const searchParams = new URLSearchParams();
  if (keyword?.trim()) searchParams.set('keyword', keyword.trim());
  const suffix = searchParams.size ? `?${searchParams.toString()}` : '';
  return requestJson<TdEngineTmqTopic[]>(`${DATA_SOURCE_PATH}/${id}/tmq-topics${suffix}`, {}, 60_000);
};

export const fetchTdEngineTmqTopic = (
  id: string,
  topic: string,
): Promise<TdEngineTmqTopicDetail> => requestJson<TdEngineTmqTopicDetail>(
  `${DATA_SOURCE_PATH}/${id}/tmq-topic?${new URLSearchParams({ topic }).toString()}`,
  {},
  60_000,
);
