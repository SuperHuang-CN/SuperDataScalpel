import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import {
  createDirectory,
  deleteDirectory,
  downloadDirectoryImportTemplate,
  exportDirectoryTree,
  fetchDirectoryTree,
  importDirectoryTree,
  updateDirectory,
} from '../api/directoryApi';
import type { CreateDirectoryRequest, DirectoryScope, UpdateDirectoryRequest } from '../model/directory';

export const directoryTreeQueryKey = (scope: DirectoryScope) => ['directories', scope] as const;

export const invalidateDirectoryTree = (queryClient: QueryClient, scope: DirectoryScope) => (
  queryClient.invalidateQueries({ queryKey: directoryTreeQueryKey(scope) })
);

export const useDirectoryTree = (scope: DirectoryScope, enabled = true) => useQuery({
  queryKey: directoryTreeQueryKey(scope),
  queryFn: () => fetchDirectoryTree(scope),
  enabled,
});

export const useCreateDirectory = (scope: DirectoryScope) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateDirectoryRequest) => createDirectory(request),
    onSuccess: () => invalidateDirectoryTree(queryClient, scope),
  });
};

export const useUpdateDirectory = (scope: DirectoryScope) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateDirectoryRequest }) => updateDirectory(id, request),
    onSuccess: () => invalidateDirectoryTree(queryClient, scope),
  });
};

export const useDeleteDirectory = (scope: DirectoryScope) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteDirectory,
    onSuccess: () => invalidateDirectoryTree(queryClient, scope),
  });
};

export const useDownloadDirectoryImportTemplate = () => useMutation({
  mutationFn: downloadDirectoryImportTemplate,
});

export const useExportDirectoryTree = () => useMutation({
  mutationFn: exportDirectoryTree,
});

export const useImportDirectoryTree = (scope: DirectoryScope) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => importDirectoryTree(scope, file),
    onSuccess: () => invalidateDirectoryTree(queryClient, scope),
  });
};
