import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { SearchRequest } from '../../../shared/search';
import { invalidateDirectoryTree } from '../../directory';
import {
  createDataModel,
  createPhysicalTableChangePlan,
  cancelPhysicalTableChangePlan,
  createPhysicalTable,
  deleteDataModel,
  executeDataModelCommand,
  executePhysicalTableChangePlan,
  fetchDataModelPreview,
  fetchDataModel,
  fetchExternalTableImportPreview,
  fetchDataModels,
  fetchPhysicalTableChangePlan,
  fetchPhysicalTableChangePlans,
  fetchPhysicalTableInspection,
  fetchPlatformTypeCapabilities,
  queryDataModelData,
  updateDataModel,
  updateDataModelFields,
  type DataModelCommand,
} from '../api/dataModelApi';
import type {
  CreateDataModelRequest,
  CreatePhysicalTableChangePlanRequest,
  ExecutePhysicalTableChangePlanRequest,
  DataModelDataQueryRequest,
  UpdateDataModelFieldsRequest,
  UpdateDataModelRequest,
} from '../model/dataModel';

const dataModelsQueryKey = 'data-models';
const physicalTableChangePlansQueryKey = 'physical-table-change-plans';

const invalidateDataModels = async (queryClient: ReturnType<typeof useQueryClient>) => {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey] }),
    invalidateDirectoryTree(queryClient, 'MODEL'),
  ]);
};

export const useDataModels = (request: SearchRequest, enabled = true) => useQuery({
  queryKey: [dataModelsQueryKey, request],
  queryFn: () => fetchDataModels(request),
  enabled,
});

export const useDataModel = (id: string | undefined, enabled: boolean) => useQuery({
  queryKey: [dataModelsQueryKey, id],
  queryFn: () => fetchDataModel(id as string),
  enabled: enabled && Boolean(id),
});

export const useExternalTableImportPreview = (
  storageDataSourceId: string | undefined,
  physicalTableName: string | undefined,
  enabled: boolean,
) => useQuery({
  queryKey: [dataModelsQueryKey, 'external-table-import-preview', storageDataSourceId, physicalTableName],
  queryFn: () => fetchExternalTableImportPreview(storageDataSourceId as string, physicalTableName as string),
  enabled: enabled && Boolean(storageDataSourceId) && Boolean(physicalTableName),
});

export const usePlatformTypeCapabilities = (storageDataSourceId: string | undefined, enabled: boolean) => useQuery({
  queryKey: [dataModelsQueryKey, 'platform-types', storageDataSourceId],
  queryFn: () => fetchPlatformTypeCapabilities(storageDataSourceId as string),
  enabled: enabled && Boolean(storageDataSourceId),
  staleTime: 5 * 60 * 1000,
});

export const usePhysicalTableInspection = (id: string | undefined, enabled: boolean) => useQuery({
  queryKey: [dataModelsQueryKey, id, 'physical-table'],
  queryFn: () => fetchPhysicalTableInspection(id as string),
  enabled: enabled && Boolean(id),
});

export const usePhysicalTableChangePlans = (id: string | undefined, request: SearchRequest, enabled: boolean) => useQuery({
  queryKey: [dataModelsQueryKey, id, physicalTableChangePlansQueryKey, request],
  queryFn: () => fetchPhysicalTableChangePlans(id as string, request),
  enabled: enabled && Boolean(id),
});

export const usePhysicalTableChangePlan = (modelId: string | undefined, planId: string | undefined, enabled: boolean) => useQuery({
  queryKey: [dataModelsQueryKey, modelId, physicalTableChangePlansQueryKey, planId],
  queryFn: () => fetchPhysicalTableChangePlan(modelId as string, planId as string),
  enabled: enabled && Boolean(modelId) && Boolean(planId),
});

export const useDataModelPreview = (id: string | undefined, enabled: boolean) => useQuery({
  queryKey: [dataModelsQueryKey, id, 'data-preview'],
  queryFn: () => fetchDataModelPreview(id as string),
  enabled: enabled && Boolean(id),
});

export const useDataModelDataQuery = () => useMutation({
  mutationFn: ({ id, request }: { id: string; request: DataModelDataQueryRequest }) => queryDataModelData(id, request),
});

export const useCreateDataModel = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateDataModelRequest) => createDataModel(request),
    onSuccess: (detail) => {
      queryClient.setQueryData([dataModelsQueryKey, detail.model.id], detail);
      return invalidateDataModels(queryClient);
    },
  });
};

export const useUpdateDataModel = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateDataModelRequest }) => updateDataModel(id, request),
    onSuccess: (detail) => {
      queryClient.setQueryData([dataModelsQueryKey, detail.model.id], detail);
      return invalidateDataModels(queryClient);
    },
  });
};

export const useUpdateDataModelFields = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateDataModelFieldsRequest }) => updateDataModelFields(id, request),
    onSuccess: (detail) => {
      queryClient.setQueryData([dataModelsQueryKey, detail.model.id], detail);
      return Promise.all([
        invalidateDataModels(queryClient),
        queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey, detail.model.id, 'physical-table'] }),
        queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey, detail.model.id, 'data-preview'] }),
      ]);
    },
  });
};

export const useCreatePhysicalTableChangePlan = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: CreatePhysicalTableChangePlanRequest }) => (
      createPhysicalTableChangePlan(id, request)
    ),
    onSuccess: async (change) => {
      queryClient.setQueryData([dataModelsQueryKey, change.modelId, physicalTableChangePlansQueryKey, change.id], change);
      await queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey, change.modelId, physicalTableChangePlansQueryKey] });
    },
  });
};

export const useCancelPhysicalTableChangePlan = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ modelId, planId }: { modelId: string; planId: string }) => cancelPhysicalTableChangePlan(modelId, planId),
    onSuccess: async (change) => {
      queryClient.setQueryData([dataModelsQueryKey, change.modelId, physicalTableChangePlansQueryKey, change.id], change);
      await queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey, change.modelId, physicalTableChangePlansQueryKey] });
    },
  });
};

export const useExecutePhysicalTableChangePlan = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      modelId,
      planId,
      request,
    }: {
      modelId: string;
      planId: string;
      request: ExecutePhysicalTableChangePlanRequest;
    }) => executePhysicalTableChangePlan(modelId, planId, request),
    onSuccess: async (change) => {
      queryClient.setQueryData([dataModelsQueryKey, change.modelId, physicalTableChangePlansQueryKey, change.id], change);
      await Promise.all([
        invalidateDataModels(queryClient),
        queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey, change.modelId] }),
        queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey, change.modelId, physicalTableChangePlansQueryKey] }),
        queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey, change.modelId, 'physical-table'] }),
        queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey, change.modelId, 'data-preview'] }),
      ]);
    },
  });
};

export const useDataModelCommand = (command: DataModelCommand) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => executeDataModelCommand(id, command),
    onSuccess: (detail) => {
      queryClient.setQueryData([dataModelsQueryKey, detail.model.id], detail);
      return invalidateDataModels(queryClient);
    },
  });
};

export const useCreatePhysicalTable = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createPhysicalTable,
    onSuccess: async (inspection, id) => {
      queryClient.setQueryData([dataModelsQueryKey, id, 'physical-table'], inspection);
      await queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey, id, 'data-preview'] });
    },
  });
};

export const useDeleteDataModel = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteDataModel,
    onSuccess: () => invalidateDataModels(queryClient),
  });
};
