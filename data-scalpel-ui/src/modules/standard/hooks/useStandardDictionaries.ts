import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { SearchRequest } from '../../../shared/search';
import {
  createStandardDictionary,
  createStandardDictionaryItem,
  downloadStandardDictionaryTemplate,
  executeStandardDictionaryCommand,
  executeStandardDictionaryItemCommand,
  exportStandardDictionaryMetadata,
  fetchStandardDictionaries,
  fetchStandardDictionary,
  fetchStandardDictionaryFieldReferences,
  fetchStandardDictionaryTree,
  importStandardDictionaryMetadata,
  moveStandardDictionaryItem,
  previewStandardDictionaryImport,
  updateStandardDictionary,
  updateStandardDictionaryItem,
  type StandardDictionaryCommand,
  type StandardDictionaryItemCommand,
} from '../api/standardDictionaryApi';
import type {
  CreateStandardDictionaryItemRequest,
  CreateStandardDictionaryRequest,
  MoveStandardDictionaryItemRequest,
  UpdateStandardDictionaryItemRequest,
  UpdateStandardDictionaryRequest,
} from '../model/standardDictionary';

const queryKey = ['standard-dictionaries'] as const;

const invalidate = (queryClient: ReturnType<typeof useQueryClient>) => Promise.all([
  queryClient.invalidateQueries({ queryKey }),
  queryClient.invalidateQueries({ queryKey: ['data-models'] }),
  queryClient.invalidateQueries({ queryKey: ['model-field-templates'] }),
]);

export const useStandardDictionaries = (request: SearchRequest, enabled = true) => useQuery({
  queryKey: [...queryKey, 'list', request],
  queryFn: () => fetchStandardDictionaries(request),
  enabled,
});

export const useStandardDictionary = (id: string | undefined, enabled = true) => useQuery({
  queryKey: [...queryKey, 'detail', id],
  queryFn: () => fetchStandardDictionary(id as string),
  enabled: enabled && Boolean(id),
});

export const useStandardDictionaryTree = (id: string | undefined, enabled = true) => useQuery({
  queryKey: [...queryKey, 'tree', id],
  queryFn: () => fetchStandardDictionaryTree(id as string),
  enabled: enabled && Boolean(id),
});

export const useStandardDictionaryFieldReferences = (
  id: string | undefined,
  request: SearchRequest,
  enabled = true,
) => useQuery({
  queryKey: [...queryKey, 'references', id, request],
  queryFn: () => fetchStandardDictionaryFieldReferences(id as string, request),
  enabled: enabled && Boolean(id),
});

export const useCreateStandardDictionary = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateStandardDictionaryRequest) => createStandardDictionary(request),
    onSuccess: () => invalidate(queryClient),
  });
};

export const useUpdateStandardDictionary = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateStandardDictionaryRequest }) => (
      updateStandardDictionary(id, request)
    ),
    onSuccess: () => invalidate(queryClient),
  });
};

export const useStandardDictionaryCommand = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      id,
      command,
      expectedVersion,
    }: {
      id: string;
      command: StandardDictionaryCommand;
      expectedVersion: number;
    }) => executeStandardDictionaryCommand(id, command, expectedVersion),
    onSuccess: () => invalidate(queryClient),
  });
};

export const useCreateStandardDictionaryItem = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      dictionaryId,
      request,
    }: {
      dictionaryId: string;
      request: CreateStandardDictionaryItemRequest;
    }) => createStandardDictionaryItem(dictionaryId, request),
    onSuccess: () => invalidate(queryClient),
  });
};

export const useUpdateStandardDictionaryItem = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      dictionaryId,
      itemId,
      request,
    }: {
      dictionaryId: string;
      itemId: string;
      request: UpdateStandardDictionaryItemRequest;
    }) => updateStandardDictionaryItem(dictionaryId, itemId, request),
    onSuccess: () => invalidate(queryClient),
  });
};

export const useMoveStandardDictionaryItem = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      dictionaryId,
      itemId,
      request,
    }: {
      dictionaryId: string;
      itemId: string;
      request: MoveStandardDictionaryItemRequest;
    }) => moveStandardDictionaryItem(dictionaryId, itemId, request),
    onSuccess: () => invalidate(queryClient),
  });
};

export const useStandardDictionaryItemCommand = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      dictionaryId,
      itemId,
      command,
      expectedVersion,
    }: {
      dictionaryId: string;
      itemId: string;
      command: StandardDictionaryItemCommand;
      expectedVersion: number;
    }) => executeStandardDictionaryItemCommand(dictionaryId, itemId, command, expectedVersion),
    onSuccess: () => invalidate(queryClient),
  });
};

export const useDownloadStandardDictionaryTemplate = () => useMutation({
  mutationFn: downloadStandardDictionaryTemplate,
});

export const useExportStandardDictionaryMetadata = () => useMutation({
  mutationFn: exportStandardDictionaryMetadata,
});

export const usePreviewStandardDictionaryImport = () => useMutation({
  mutationFn: previewStandardDictionaryImport,
});

export const useImportStandardDictionaryMetadata = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ file, previewDigest }: { file: File; previewDigest: string }) => (
      importStandardDictionaryMetadata(file, previewDigest)
    ),
    onSuccess: () => invalidate(queryClient),
  });
};

export const invalidateStandardDictionaries = (
  queryClient: ReturnType<typeof useQueryClient>,
) => queryClient.invalidateQueries({ queryKey });
