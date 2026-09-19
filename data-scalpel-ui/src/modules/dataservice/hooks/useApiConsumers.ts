import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { SearchRequest } from '../../../shared/search';
import {
  createApiConsumer,
  deleteApiConsumer,
  fetchApiConsumers,
  reconcileApiConsumerGateway,
  syncApiConsumer,
  updateApiConsumer,
} from '../api/apiConsumerApi';
import type { CreateApiConsumerRequest, UpdateApiConsumerRequest } from '../model/apiConsumer';

const apiConsumersQueryKey = ['api-consumers'] as const;

const invalidateApiConsumers = (queryClient: ReturnType<typeof useQueryClient>) => (
  queryClient.invalidateQueries({ queryKey: apiConsumersQueryKey })
);

export const useApiConsumers = (request: SearchRequest) => useQuery({
  queryKey: [...apiConsumersQueryKey, request],
  queryFn: () => fetchApiConsumers(request),
});

export const useCreateApiConsumer = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateApiConsumerRequest) => createApiConsumer(request),
    onSuccess: () => invalidateApiConsumers(queryClient),
  });
};

export const useUpdateApiConsumer = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateApiConsumerRequest }) => (
      updateApiConsumer(id, request)
    ),
    onSuccess: () => invalidateApiConsumers(queryClient),
  });
};

export const useSyncApiConsumer = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: syncApiConsumer,
    onSuccess: () => invalidateApiConsumers(queryClient),
  });
};

export const useReconcileApiConsumerGateway = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: reconcileApiConsumerGateway,
    onSuccess: () => invalidateApiConsumers(queryClient),
  });
};

export const useDeleteApiConsumer = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteApiConsumer,
    onSuccess: () => invalidateApiConsumers(queryClient),
  });
};
