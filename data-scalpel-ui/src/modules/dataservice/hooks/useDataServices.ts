import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { SearchRequest } from '../../../shared/search';
import { invalidateDirectoryTree } from '../../directory';
import {
  createDataService,
  deleteDataService,
  disableDataService,
  fetchDataServices,
  publishDataService,
  updateDataService,
} from '../api/dataServiceApi';
import type {
  CreateDataServiceRequest,
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

export const useDisableDataService = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: disableDataService,
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
