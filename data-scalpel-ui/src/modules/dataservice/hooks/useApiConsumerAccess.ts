import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { SearchRequest } from '../../../shared/search';
import {
  createApiConsumerCredential,
  createApiServiceSubscription,
  deleteApiConsumerCredential,
  fetchApiConsumerCredentials,
  fetchApiServiceSubscriptions,
  reconcileApiConsumerCredentialGateway,
  reconcileApiServiceSubscriptionGateway,
  revokeApiServiceSubscription,
  rotateApiConsumerCredential,
  syncApiServiceSubscription,
} from '../api/apiConsumerAccessApi';
import type {
  ApiServiceSubscriptionFilters,
  CreateApiConsumerCredentialRequest,
  CreateApiServiceSubscriptionRequest,
} from '../model/apiConsumerAccess';

const credentialQueryKey = (consumerId: string) => ['api-consumer-credentials', consumerId] as const;
const subscriptionQueryKey = ['api-service-subscriptions'] as const;

export const useApiConsumerCredentials = (consumerId: string | undefined, enabled = true) => useQuery({
  queryKey: credentialQueryKey(consumerId ?? ''),
  queryFn: () => fetchApiConsumerCredentials(consumerId as string),
  enabled: enabled && Boolean(consumerId),
});

export const useCreateApiConsumerCredential = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      consumerId,
      request,
    }: {
      consumerId: string;
      request: CreateApiConsumerCredentialRequest;
    }) => createApiConsumerCredential(consumerId, request),
    onSuccess: (_, variables) => queryClient.invalidateQueries({
      queryKey: credentialQueryKey(variables.consumerId),
    }),
  });
};

export const useRotateApiConsumerCredential = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ consumerId, credentialId }: { consumerId: string; credentialId: string }) => (
      rotateApiConsumerCredential(consumerId, credentialId)
    ),
    onSuccess: (_, variables) => queryClient.invalidateQueries({
      queryKey: credentialQueryKey(variables.consumerId),
    }),
  });
};

export const useReconcileApiConsumerCredentialGateway = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ consumerId, credentialId }: { consumerId: string; credentialId: string }) => (
      reconcileApiConsumerCredentialGateway(consumerId, credentialId)
    ),
    onSuccess: (_, variables) => queryClient.invalidateQueries({
      queryKey: credentialQueryKey(variables.consumerId),
    }),
  });
};

export const useDeleteApiConsumerCredential = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ consumerId, credentialId }: { consumerId: string; credentialId: string }) => (
      deleteApiConsumerCredential(consumerId, credentialId)
    ),
    onSuccess: (_, variables) => queryClient.invalidateQueries({
      queryKey: credentialQueryKey(variables.consumerId),
    }),
  });
};

export const useApiServiceSubscriptions = (
  request: SearchRequest,
  filters: ApiServiceSubscriptionFilters,
  enabled = true,
) => useQuery({
  queryKey: [...subscriptionQueryKey, request, filters],
  queryFn: () => fetchApiServiceSubscriptions(request, filters),
  enabled,
});

const invalidateSubscriptions = (queryClient: ReturnType<typeof useQueryClient>) => (
  queryClient.invalidateQueries({ queryKey: subscriptionQueryKey })
);

export const useCreateApiServiceSubscription = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateApiServiceSubscriptionRequest) => createApiServiceSubscription(request),
    onSuccess: () => invalidateSubscriptions(queryClient),
  });
};

export const useSyncApiServiceSubscription = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: syncApiServiceSubscription,
    onSuccess: () => invalidateSubscriptions(queryClient),
  });
};

export const useReconcileApiServiceSubscriptionGateway = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: reconcileApiServiceSubscriptionGateway,
    onSuccess: () => invalidateSubscriptions(queryClient),
  });
};

export const useRevokeApiServiceSubscription = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: revokeApiServiceSubscription,
    onSuccess: () => invalidateSubscriptions(queryClient),
  });
};
