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
  fetchDataServiceSpatialPreview,
  fetchDataServiceSpatialStyle,
  fetchDataServiceFieldLineage,
  fetchDataServiceTableLineage,
  queryDataServiceFieldLineage,
  fetchScriptCompletion,
  fetchStandardDataServiceModelCandidates,
  fetchSpatialDataServiceModelCandidates,
  publishDataService,
  reconcileDataServiceGateway,
  testSqlDataService,
  unpublishDataService,
  updateDataService,
  updateDataServiceDefinition,
  updateDataServiceSpatialStyle,
  uploadDataServiceSpatialSld,
  applyDataServiceSpatialStyle,
  queryDataServiceSpatialStyleFieldProfile,
  type ExecuteScriptDraftRequest,
} from '../api/dataServiceApi';
import type {
  CreateDataServiceRequest,
  PublishDataServiceRequest,
  SqlServiceTestRequest,
  UpdateDataServiceDefinitionRequest,
  UpdateDataServiceRequest,
} from '../model/dataService';
import type { FieldProfileRequest, SpatialStyleDocument, SpatialStyleMode } from '../../cartography';
import type { LineageGranularity } from '../../model';
import { invalidateDataModelLineage } from '../../model';

const dataServicesQueryKey = 'data-services';

const invalidateDataServices = async (queryClient: ReturnType<typeof useQueryClient>) => {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: [dataServicesQueryKey] }),
    invalidateDirectoryTree(queryClient, 'DATA_SERVICE'),
    invalidateDataModelLineage(queryClient),
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

export const useDataServiceSpatialPreview = (id: string | undefined, enabled = true) => useQuery({
  queryKey: [dataServicesQueryKey, id, 'spatial-preview'],
  queryFn: () => fetchDataServiceSpatialPreview(id as string),
  enabled: enabled && Boolean(id),
});

export const useDataServiceSpatialStyle = (id: string | undefined, enabled = true) => useQuery({
  queryKey: [dataServicesQueryKey, id, 'spatial-style'],
  queryFn: () => fetchDataServiceSpatialStyle(id as string),
  enabled: enabled && Boolean(id),
});

export const useUpdateDataServiceSpatialStyle = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, styleDocument, mode = 'CARTOGRAPHY' }: {
      id: string;
      styleDocument: SpatialStyleDocument | null;
      mode?: SpatialStyleMode;
    }) => (
      updateDataServiceSpatialStyle(id, styleDocument, mode)
    ),
    onSuccess: (style, { id }) => {
      queryClient.setQueryData([dataServicesQueryKey, id, 'spatial-style'], style);
    },
  });
};

export const useProfileDataServiceSpatialStyleField = () => useMutation({
  mutationFn: ({ id, request }: { id: string; request: FieldProfileRequest }) => (
    queryDataServiceSpatialStyleFieldProfile(id, request)
  ),
});

export const useUploadDataServiceSpatialSld = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, file }: { id: string; file: File }) => uploadDataServiceSpatialSld(id, file),
    onSuccess: (style, { id }) => {
      queryClient.setQueryData([dataServicesQueryKey, id, 'spatial-style'], style);
    },
  });
};

export const useApplyDataServiceSpatialStyle = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: applyDataServiceSpatialStyle,
    onSuccess: async (style, id) => {
      queryClient.setQueryData([dataServicesQueryKey, id, 'spatial-style'], style);
      await queryClient.invalidateQueries({ queryKey: [dataServicesQueryKey, id, 'spatial-preview'] });
    },
    onSettled: async (_style, _error, id) => {
      await queryClient.invalidateQueries({ queryKey: [dataServicesQueryKey, id, 'spatial-style'] });
    },
  });
};

export const useDataServiceLineage = (
  serviceId: string,
  granularity: LineageGranularity,
  fieldId: string | undefined,
  depth: 1 | 2,
  enabled = true,
) => useQuery({
  queryKey: [dataServicesQueryKey, serviceId, 'lineage', granularity, fieldId ?? null, depth],
  queryFn: () => granularity === 'TABLE'
    ? fetchDataServiceTableLineage(serviceId, depth)
    : fetchDataServiceFieldLineage(serviceId, fieldId as string, depth),
  enabled: enabled && (granularity === 'TABLE' || Boolean(fieldId)),
});

export const useDataServiceFieldLineage = (
  serviceId: string,
  fieldIds: string[] | null,
  depth: 1 | 2,
  enabled = true,
) => useQuery({
  queryKey: [dataServicesQueryKey, serviceId, 'lineage', 'FIELD_BATCH', fieldIds, depth],
  queryFn: ({ signal }) => queryDataServiceFieldLineage(serviceId, fieldIds, depth, signal),
  enabled,
  staleTime: 30_000,
  placeholderData: (previous) => previous,
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

export const useUpdateDataServiceDefinition = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateDataServiceDefinitionRequest }) => (
      updateDataServiceDefinition(id, request)
    ),
    onSuccess: () => invalidateDataServices(queryClient),
  });
};

export const useStandardDataServiceModelCandidates = (
  id: string | undefined,
  request: SearchRequest,
  includeUnavailable: boolean,
  enabled = true,
) => useQuery({
  queryKey: [dataServicesQueryKey, id, 'standard-model-candidates', request, includeUnavailable],
  queryFn: () => fetchStandardDataServiceModelCandidates(id as string, request, includeUnavailable),
  enabled: enabled && Boolean(id),
  placeholderData: (previous) => previous,
});

export const useSpatialDataServiceModelCandidates = (
  id: string | undefined,
  request: SearchRequest,
  includeUnavailable: boolean,
  enabled = true,
) => useQuery({
  queryKey: [dataServicesQueryKey, id, 'spatial-model-candidates', request, includeUnavailable],
  queryFn: () => fetchSpatialDataServiceModelCandidates(id as string, request, includeUnavailable),
  enabled: enabled && Boolean(id),
  placeholderData: (previous) => previous,
});

export const usePublishDataService = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: PublishDataServiceRequest }) => publishDataService(id, request),
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
