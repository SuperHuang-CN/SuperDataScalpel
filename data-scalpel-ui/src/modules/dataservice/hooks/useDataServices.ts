import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { SearchRequest } from '../../../shared/search';
import { invalidateDirectoryTree } from '../../directory';
import {
  createDataService,
  cleanupDataServiceDeployment,
  deleteDataService,
  disableDataService,
  enableDataService,
  executeScriptDraft,
  fetchDataServices,
  fetchDataService,
  fetchScriptCompletion,
  publishDataService,
  reconcileDataServiceGateway,
  testSqlDataService,
  unpublishDataService,
  updateDataService,
  type ExecuteScriptDraftRequest,
} from '../api/dataServiceApi';
import type {
  CreateDataServiceRequest,
  SqlServiceTestRequest,
  UpdateDataServiceRequest,
} from '../model/dataService';

const dataServicesQueryKey = 'data-services';

const invalidateDataServices = async (queryClient: ReturnType<typeof useQueryClient>) => {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: [dataServicesQueryKey] }),
    invalidateDirectoryTree(queryClient, 'DATA_SERVICE'),
  ]);
};

export const useDataServices = (request: SearchRequest) => useQuery({
  queryKey: [dataServicesQueryKey, request],
  queryFn: () => fetchDataServices(request),
});

export const useDataService = (id: string | undefined, enabled = true) => useQuery({
  queryKey: [dataServicesQueryKey, id],
  queryFn: () => fetchDataService(id as string),
  enabled: enabled && Boolean(id),
});

export const useCreateDataService = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateDataServiceRequest) => createDataService(request),
    onSuccess: () => invalidateDataServices(queryClient),
  });
};

export const useUpdateDataService = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateDataServiceRequest }) => updateDataService(id, request),
    onSuccess: () => invalidateDataServices(queryClient),
  });
};

export const usePublishDataService = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: publishDataService,
    onSuccess: () => invalidateDataServices(queryClient),
  });
};

export const useReconcileDataServiceGateway = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: reconcileDataServiceGateway,
    onSuccess: () => invalidateDataServices(queryClient),
  });
};

export const useEnableDataService = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: enableDataService,
    onSuccess: () => invalidateDataServices(queryClient),
  });
};

export const useUnpublishDataService = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: unpublishDataService,
    onSuccess: () => invalidateDataServices(queryClient),
  });
};

export const useDisableDataService = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: disableDataService,
    onSuccess: () => invalidateDataServices(queryClient),
  });
};

export const useTestSqlDataService = () => useMutation({
  mutationFn: (request: SqlServiceTestRequest) => testSqlDataService(request),
});

export const useExecuteScriptDraft = () => useMutation({
  mutationFn: (request: ExecuteScriptDraftRequest) => executeScriptDraft(request),
});

export const useScriptCompletion = (
  engineId: string | undefined,
  dataSourceId: string | undefined,
  enabled = true,
) => useQuery({
  queryKey: [dataServicesQueryKey, 'script-completion', engineId, dataSourceId],
  queryFn: () => fetchScriptCompletion(engineId as string, dataSourceId as string),
  enabled: enabled && Boolean(engineId) && Boolean(dataSourceId),
  staleTime: 5 * 60 * 1000,
});

export const useCleanupDataServiceDeployment = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: cleanupDataServiceDeployment,
    onSuccess: () => invalidateDataServices(queryClient),
  });
};

export const useDeleteDataService = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteDataService,
    onSuccess: () => invalidateDataServices(queryClient),
  });
};
