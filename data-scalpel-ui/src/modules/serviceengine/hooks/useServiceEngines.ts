import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { SearchRequest } from '../../../shared/search';
import {
  createServiceEngine,
  createServiceEngineDataSourceRegistration,
  deleteServiceEngine,
  deleteServiceEngineDataSourceRegistration,
  fetchServiceEngines,
  fetchServiceEngineDataSourceRegistrations,
  syncServiceEngineDataSourceRegistration,
  testServiceEngine,
  testServiceEngineDataSourceRegistration,
  updateServiceEngine,
} from '../api/serviceEngineApi';
import type { CreateServiceEngineRequest, UpdateServiceEngineRequest } from '../model/serviceEngine';

const serviceEnginesQueryKey = 'service-engines';
const serviceEngineDataSourcesQueryKey = 'service-engine-data-sources';

const invalidateServiceEngines = (queryClient: ReturnType<typeof useQueryClient>) => (
  queryClient.invalidateQueries({ queryKey: [serviceEnginesQueryKey] })
);

export const useServiceEngines = (request: SearchRequest, enabled = true) => useQuery({
  queryKey: [serviceEnginesQueryKey, request],
  queryFn: () => fetchServiceEngines(request),
  enabled,
});

export const useCreateServiceEngine = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateServiceEngineRequest) => createServiceEngine(request),
    onSuccess: () => invalidateServiceEngines(queryClient),
  });
};

export const useUpdateServiceEngine = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateServiceEngineRequest }) => updateServiceEngine(id, request),
    onSuccess: () => invalidateServiceEngines(queryClient),
  });
};

export const useTestServiceEngine = () => useMutation({ mutationFn: testServiceEngine });

export const useDeleteServiceEngine = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteServiceEngine,
    onSuccess: async () => {
      await Promise.all([
        invalidateServiceEngines(queryClient),
        queryClient.invalidateQueries({ queryKey: ['data-services'] }),
      ]);
    },
  });
};

const invalidateServiceEngineDataSources = (queryClient: ReturnType<typeof useQueryClient>) => (
  queryClient.invalidateQueries({ queryKey: [serviceEngineDataSourcesQueryKey] })
);

export const useServiceEngineDataSourceRegistrations = (request: SearchRequest, enabled = true) => useQuery({
  queryKey: [serviceEngineDataSourcesQueryKey, request],
  queryFn: () => fetchServiceEngineDataSourceRegistrations(request),
  enabled,
});

export const useCreateServiceEngineDataSourceRegistration = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ engineId, dataSourceId }: { engineId: string; dataSourceId: string }) => (
      createServiceEngineDataSourceRegistration(engineId, dataSourceId)
    ),
    onSuccess: () => invalidateServiceEngineDataSources(queryClient),
  });
};

export const useSyncServiceEngineDataSourceRegistration = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: syncServiceEngineDataSourceRegistration,
    onSuccess: () => invalidateServiceEngineDataSources(queryClient),
  });
};

export const useTestServiceEngineDataSourceRegistration = () => useMutation({
  mutationFn: testServiceEngineDataSourceRegistration,
});

export const useDeleteServiceEngineDataSourceRegistration = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteServiceEngineDataSourceRegistration,
    onSuccess: async () => {
      await Promise.all([
        invalidateServiceEngineDataSources(queryClient),
        queryClient.invalidateQueries({ queryKey: ['data-services'] }),
      ]);
    },
  });
};
