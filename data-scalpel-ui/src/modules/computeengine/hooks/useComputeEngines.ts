import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { SearchRequest } from '../../../shared/search';
import {
  createComputeEngine,
  deactivateComputeEngine,
  detachComputeEngine,
  deleteComputeEngine,
  executeComputeEngineCommand,
  fetchComputeEngines,
  reconfigureComputeEngine,
  testComputeEngine,
  updateComputeEngine,
  type ComputeEngineCommand,
} from '../api/computeEngineApi';
import type {
  CreateComputeEngineRequest,
  DetachComputeEngineRequest,
  UpdateComputeEngineRequest,
} from '../model/computeEngine';

const computeEnginesKey = 'compute-engines';

const invalidateComputeEngines = (queryClient: ReturnType<typeof useQueryClient>) => (
  queryClient.invalidateQueries({ queryKey: [computeEnginesKey] })
);

export const useComputeEngines = (request: SearchRequest, enabled = true) => useQuery({
  queryKey: [computeEnginesKey, request],
  queryFn: () => fetchComputeEngines(request),
  enabled,
});

export const useCreateComputeEngine = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateComputeEngineRequest) => createComputeEngine(request),
    onSuccess: () => invalidateComputeEngines(queryClient),
  });
};

export const useUpdateComputeEngine = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateComputeEngineRequest }) => updateComputeEngine(id, request),
    onSuccess: () => invalidateComputeEngines(queryClient),
  });
};

export const useReconfigureComputeEngine = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateComputeEngineRequest }) => (
      reconfigureComputeEngine(id, request)
    ),
    onSettled: () => invalidateComputeEngines(queryClient),
  });
};

export const useTestComputeEngine = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: testComputeEngine,
    onSettled: () => invalidateComputeEngines(queryClient),
  });
};

export const useComputeEngineCommand = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, command }: { id: string; command: ComputeEngineCommand }) => (
      executeComputeEngineCommand(id, command)
    ),
    onSuccess: () => invalidateComputeEngines(queryClient),
  });
};

export const useDeactivateComputeEngine = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, force }: { id: string; force: boolean }) => deactivateComputeEngine(id, force),
    onSettled: () => invalidateComputeEngines(queryClient),
  });
};

export const useDetachComputeEngine = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: DetachComputeEngineRequest }) => (
      detachComputeEngine(id, request)
    ),
    onSuccess: () => invalidateComputeEngines(queryClient),
  });
};

export const useDeleteComputeEngine = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteComputeEngine,
    onSuccess: async () => {
      await Promise.all([
        invalidateComputeEngines(queryClient),
        queryClient.invalidateQueries({ queryKey: ['tasks'] }),
      ]);
    },
  });
};
