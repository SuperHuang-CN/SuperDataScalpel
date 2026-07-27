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
  KafkaTopic,
  UpdateApiResourceRequest,
  UpdateDataSourceRequest,
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

export const fetchDataSourceNamespaces = (id: string): Promise<DataSourceNamespace[]> => (
  requestJson<DataSourceNamespace[]>(`${DATA_SOURCE_PATH}/${id}/namespaces`)
);

export const fetchDataSourceTables = (id: string, query: TableQuery): Promise<TableListResult> => {
  const searchParams = new URLSearchParams();
  if (query.catalog) searchParams.set('catalog', query.catalog);
  if (query.schema) searchParams.set('schema', query.schema);
  if (query.keyword) searchParams.set('keyword', query.keyword);
  if (query.includeViews) searchParams.set('includeViews', 'true');
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

export const fetchTablePreview = (id: string, table: TableIdentifier, limit = 50): Promise<TablePreview> => {
  const searchParams = tableSearchParams(table);
  searchParams.set('limit', String(limit));
  return requestJson<TablePreview>(`${DATA_SOURCE_PATH}/${id}/table-preview?${searchParams.toString()}`);
};

export const fetchKafkaTopics = (id: string, keyword?: string): Promise<KafkaTopic[]> => {
  const searchParams = new URLSearchParams();
  if (keyword?.trim()) searchParams.set('keyword', keyword.trim());
  const suffix = searchParams.size ? `?${searchParams.toString()}` : '';
  return requestJson<KafkaTopic[]>(`${DATA_SOURCE_PATH}/${id}/kafka-topics${suffix}`);
};
