import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { invalidateDirectoryTree } from '../../directory';
import type { SearchRequest } from '../../../shared/search';
import {
  createApiResource,
  createDataSource,
  deleteApiResource,
  deleteDataSource,
  fetchApiResource,
  fetchApiResources,
  fetchDataSource,
  fetchDataSourceTypes,
  fetchDataSourceNamespaces,
  fetchDataSourceTables,
  fetchTableMetadata,
  fetchTablePreview,
  fetchDataSources,
  testDraftDataSourceConnection,
  testApiResource,
  testSavedDataSourceConnection,
  updateApiResource,
  updateDataSource,
  createSpatialFeatureResource,
  deleteSpatialFeatureResource,
  fetchSpatialCatalog,
  fetchSpatialFeatureResources,
  previewSpatialFeatureResource,
  refreshSpatialFeatureResourceSchema,
  updateSpatialFeatureResource,
} from '../api/dataSourceApi';
import type {
  CreateApiResourceRequest,
  HttpApiRuntimeParameter,
  TableIdentifier,
  TableQuery,
  TestDataSourceConnectionRequest,
  UpdateApiResourceRequest,
  UpdateDataSourceRequest,
  CreateSpatialFeatureResourceRequest,
  UpdateSpatialFeatureResourceRequest,
} from '../model/dataSource';

const dataSourcesQueryKey = 'data-sources';
const apiResourcesQueryKey = 'api-resources';
const spatialResourcesQueryKey = 'spatial-resources';

export const useDataSourceTypes = () => useQuery({
  queryKey: ['data-source-types'],
  queryFn: fetchDataSourceTypes,
  staleTime: 5 * 60 * 1000,
});

const invalidateDataSources = async (queryClient: ReturnType<typeof useQueryClient>) => {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: [dataSourcesQueryKey] }),
    invalidateDirectoryTree(queryClient, 'DATA_SOURCE'),
  ]);
};

export const useDataSources = (request: SearchRequest, enabled = true) => useQuery({
  queryKey: [dataSourcesQueryKey, request],
  queryFn: () => fetchDataSources(request),
  enabled,
});

export const useDataSource = (id: string | undefined, enabled = true) => useQuery({
  queryKey: [dataSourcesQueryKey, id],
  queryFn: () => fetchDataSource(id as string),
  enabled: enabled && Boolean(id),
});

export const useCreateDataSource = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createDataSource,
    onSuccess: () => invalidateDataSources(queryClient),
  });
};

export const useUpdateDataSource = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateDataSourceRequest }) => updateDataSource(id, request),
    onSuccess: () => invalidateDataSources(queryClient),
  });
};

export const useDeleteDataSource = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteDataSource,
    onSuccess: () => invalidateDataSources(queryClient),
  });
};

export const useTestDraftDataSourceConnection = () => useMutation({
  mutationFn: (request: TestDataSourceConnectionRequest) => testDraftDataSourceConnection(request),
});

export const useTestSavedDataSourceConnection = () => useMutation({
  mutationFn: (id: string) => testSavedDataSourceConnection(id),
});

export const useApiResources = (dataSourceId: string | undefined, enabled = true) => useQuery({
  queryKey: [apiResourcesQueryKey, dataSourceId],
  queryFn: () => fetchApiResources(dataSourceId as string),
  enabled: enabled && Boolean(dataSourceId),
});

export const useApiResource = (
  dataSourceId: string | undefined,
  resourceId: string | undefined,
  enabled = true,
) => useQuery({
  queryKey: [apiResourcesQueryKey, dataSourceId, resourceId],
  queryFn: () => fetchApiResource(dataSourceId as string, resourceId as string),
  enabled: enabled && Boolean(dataSourceId) && Boolean(resourceId),
});

const invalidateApiResources = async (
  queryClient: ReturnType<typeof useQueryClient>,
  dataSourceId: string,
) => queryClient.invalidateQueries({ queryKey: [apiResourcesQueryKey, dataSourceId] });

export const useCreateApiResource = (dataSourceId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateApiResourceRequest) => createApiResource(dataSourceId, request),
    onSuccess: () => invalidateApiResources(queryClient, dataSourceId),
  });
};

export const useUpdateApiResource = (dataSourceId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ resourceId, request }: { resourceId: string; request: UpdateApiResourceRequest }) => (
      updateApiResource(dataSourceId, resourceId, request)
    ),
    onSuccess: () => invalidateApiResources(queryClient, dataSourceId),
  });
};

export const useDeleteApiResource = (dataSourceId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (resourceId: string) => deleteApiResource(dataSourceId, resourceId),
    onSuccess: () => invalidateApiResources(queryClient, dataSourceId),
  });
};

export const useTestApiResource = (dataSourceId: string) => useMutation({
  mutationFn: ({ resourceId, runtimeParameters }: {
    resourceId: string;
    runtimeParameters: HttpApiRuntimeParameter[];
  }) => testApiResource(dataSourceId, resourceId, runtimeParameters),
});

export const useSpatialCatalog = (dataSourceId: string | undefined, parent: string | undefined, enabled = true) => useQuery({
  queryKey: [spatialResourcesQueryKey, dataSourceId, 'catalog', parent],
  queryFn: () => fetchSpatialCatalog(dataSourceId as string, parent),
  enabled: enabled && Boolean(dataSourceId),
});

export const useSpatialFeatureResources = (dataSourceId: string | undefined, enabled = true) => useQuery({
  queryKey: [spatialResourcesQueryKey, dataSourceId],
  queryFn: () => fetchSpatialFeatureResources(dataSourceId as string),
  enabled: enabled && Boolean(dataSourceId),
});

const invalidateSpatialResources = (queryClient: ReturnType<typeof useQueryClient>, dataSourceId: string) => (
  queryClient.invalidateQueries({ queryKey: [spatialResourcesQueryKey, dataSourceId] })
);

export const useCreateSpatialFeatureResource = (dataSourceId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateSpatialFeatureResourceRequest) => createSpatialFeatureResource(dataSourceId, request),
    onSuccess: () => invalidateSpatialResources(queryClient, dataSourceId),
  });
};

export const useUpdateSpatialFeatureResource = (dataSourceId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ resourceId, request }: { resourceId: string; request: UpdateSpatialFeatureResourceRequest }) => (
      updateSpatialFeatureResource(dataSourceId, resourceId, request)
    ),
    onSuccess: () => invalidateSpatialResources(queryClient, dataSourceId),
  });
};

export const useRefreshSpatialFeatureResourceSchema = (dataSourceId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (resourceId: string) => refreshSpatialFeatureResourceSchema(dataSourceId, resourceId),
    onSuccess: () => invalidateSpatialResources(queryClient, dataSourceId),
  });
};

export const useDeleteSpatialFeatureResource = (dataSourceId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (resourceId: string) => deleteSpatialFeatureResource(dataSourceId, resourceId),
    onSuccess: () => invalidateSpatialResources(queryClient, dataSourceId),
  });
};

export const useSpatialFeaturePreview = (dataSourceId: string, resourceId: string | undefined, enabled = true) => useQuery({
  queryKey: [spatialResourcesQueryKey, dataSourceId, resourceId, 'preview', 50],
  queryFn: () => previewSpatialFeatureResource(dataSourceId, resourceId as string),
  enabled: enabled && Boolean(resourceId),
  staleTime: 0,
});

export const useDataSourceNamespaces = (id: string | undefined, enabled: boolean) => useQuery({
  queryKey: [dataSourcesQueryKey, id, 'namespaces'],
  queryFn: () => fetchDataSourceNamespaces(id as string),
  enabled: enabled && Boolean(id),
});

export const useDataSourceTables = (
  id: string | undefined,
  query: TableQuery,
  enabled: boolean,
) => useQuery({
  queryKey: [dataSourcesQueryKey, id, 'tables', query],
  queryFn: () => fetchDataSourceTables(id as string, query),
  enabled: enabled && Boolean(id),
});

export const useTableMetadata = (
  id: string | undefined,
  table: TableIdentifier | undefined,
  enabled: boolean,
) => useQuery({
  queryKey: [dataSourcesQueryKey, id, 'table-metadata', table],
  queryFn: () => fetchTableMetadata(id as string, table as TableIdentifier),
  enabled: enabled && Boolean(id) && Boolean(table),
});

export const useTablePreview = (
  id: string | undefined,
  table: TableIdentifier | undefined,
  enabled: boolean,
) => useQuery({
  queryKey: [dataSourcesQueryKey, id, 'table-preview', table, 50],
  queryFn: () => fetchTablePreview(id as string, table as TableIdentifier, 50),
  enabled: enabled && Boolean(id) && Boolean(table),
});
