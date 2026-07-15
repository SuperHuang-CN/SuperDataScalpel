import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { invalidateDirectoryTree } from '../../directory';
import type { SearchRequest } from '../../../shared/search';
import {
  createDataSource,
  deleteDataSource,
  fetchDataSourceTypes,
  fetchDataSourceNamespaces,
  fetchDataSourceTables,
  fetchTableMetadata,
  fetchTablePreview,
  fetchDataSources,
  testDraftDataSourceConnection,
  testSavedDataSourceConnection,
  updateDataSource,
} from '../api/dataSourceApi';
import type {
  TableIdentifier,
  TableQuery,
  TestDataSourceConnectionRequest,
  UpdateDataSourceRequest,
} from '../model/dataSource';

const dataSourcesQueryKey = 'data-sources';

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

export const useDataSources = (request: SearchRequest) => useQuery({
  queryKey: [dataSourcesQueryKey, request],
  queryFn: () => fetchDataSources(request),
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
