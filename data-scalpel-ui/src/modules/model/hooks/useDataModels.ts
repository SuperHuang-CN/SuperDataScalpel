import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { SearchRequest } from '../../../shared/search';
import { invalidateDirectoryTree } from '../../directory';
import {
  createDataModel,
  deleteDataModel,
  executeDataModelCommand,
  fetchDataModel,
  fetchDataModels,
  updateDataModel,
  updateDataModelFields,
  type DataModelCommand,
} from '../api/dataModelApi';
import type { CreateDataModelRequest, UpdateDataModelFieldsRequest, UpdateDataModelRequest } from '../model/dataModel';

const dataModelsQueryKey = 'data-models';

const invalidateDataModels = async (queryClient: ReturnType<typeof useQueryClient>) => {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey] }),
    invalidateDirectoryTree(queryClient, 'MODEL'),
  ]);
};

export const useDataModels = (request: SearchRequest) => useQuery({
  queryKey: [dataModelsQueryKey, request],
  queryFn: () => fetchDataModels(request),
});

export const useDataModel = (id: string | undefined, enabled: boolean) => useQuery({
  queryKey: [dataModelsQueryKey, id],
  queryFn: () => fetchDataModel(id as string),
  enabled: enabled && Boolean(id),
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
      return invalidateDataModels(queryClient);
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

export const useDeleteDataModel = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteDataModel,
    onSuccess: () => invalidateDataModels(queryClient),
  });
};
