import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { SearchRequest } from '../../../shared/search';
import {
  createServiceEngine,
  createServiceEngineDataSourceRegistration,
  deleteServiceEngine,
  deleteServiceEngineDataSourceRegistration,
  fetchServiceEngine,
  fetchServiceEngines,
  fetchServiceEngineDataSourceRegistrations,
  fetchServiceEngineAccessPolicy,
  syncServiceEngineDataSourceRegistration,
  testNewServiceEngine,
  testServiceEngine,
  testServiceEngineDataSourceRegistration,
  updateServiceEngine,
  updateServiceEngineAccessPolicy,
  syncServiceEngineAccessPolicy,
} from '../api/serviceEngineApi';
import type {
  CreateServiceEngineRequest,
  TestServiceEngineRequest,
  TestStoredServiceEngineRequest,
  UpdateServiceEngineRequest,
  UpdateServiceEngineAccessPolicyRequest,
} from '../model/serviceEngine';

const serviceEnginesQueryKey = 'service-engines';
const serviceEngineDataSourcesQueryKey = 'service-engine-data-sources';
const serviceEngineAccessPolicyQueryKey = 'service-engine-access-policy';

const invalidateServiceEngines = (queryClient: ReturnType<typeof useQueryClient>) => (
  queryClient.invalidateQueries({ queryKey: [serviceEnginesQueryKey] })
);

export const useServiceEngines = (request: SearchRequest, enabled = true) => useQuery({
  queryKey: [serviceEnginesQueryKey, request],
  queryFn: () => fetchServiceEngines(request),
  enabled,
});

export const useServiceEngine = (id: string | undefined, enabled = true) => useQuery({
  queryKey: [serviceEnginesQueryKey, id],
  queryFn: () => fetchServiceEngine(id as string),
  enabled: enabled && Boolean(id),
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

export const useTestNewServiceEngine = () => useMutation({
  mutationFn: (request: TestServiceEngineRequest) => testNewServiceEngine(request),
});

export const useTestServiceEngine = () => useMutation({
  mutationFn: ({ id, request }: { id: string; request?: TestStoredServiceEngineRequest }) => (
    testServiceEngine(id, request)
  ),
});

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

export const useServiceEngineAccessPolicy = (engineId: string | undefined, enabled = true) => useQuery({
  queryKey: [serviceEngineAccessPolicyQueryKey, engineId],
  queryFn: () => fetchServiceEngineAccessPolicy(engineId as string),
  enabled: enabled && Boolean(engineId),
});

export const useUpdateServiceEngineAccessPolicy = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateServiceEngineAccessPolicyRequest }) => (
      updateServiceEngineAccessPolicy(id, request)
    ),
    onSettled: (_result, _error, variables) => queryClient.invalidateQueries({
      queryKey: [serviceEngineAccessPolicyQueryKey, variables.id],
    }),
  });
};

export const useSyncServiceEngineAccessPolicy = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: syncServiceEngineAccessPolicy,
    onSettled: (_result, _error, id) => queryClient.invalidateQueries({
      queryKey: [serviceEngineAccessPolicyQueryKey, id],
    }),
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
