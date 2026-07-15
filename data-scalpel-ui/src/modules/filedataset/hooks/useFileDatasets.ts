import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { SearchRequest } from '../../../shared/search';
import { invalidateDirectoryTree } from '../../directory';
import {
  createFileDataset,
  configureFileDatasetParsing,
  deleteFileDataset,
  downloadFileDatasetContent,
  fetchFileDatasets,
  fetchFileDatasetParsing,
  fetchFileDatasetPreview,
  parseFileDataset,
  replaceFileDatasetContent,
  updateFileDataset,
} from '../api/fileDatasetApi';
import type { UpdateFileDatasetRequest } from '../model/fileDataset';

const fileDatasetsQueryKey = 'file-datasets';

const invalidateFileDatasets = async (queryClient: ReturnType<typeof useQueryClient>) => {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: [fileDatasetsQueryKey] }),
    invalidateDirectoryTree(queryClient, 'FILE_DATASET'),
  ]);
};

export const useFileDatasets = (request: SearchRequest) => useQuery({
  queryKey: [fileDatasetsQueryKey, request],
  queryFn: () => fetchFileDatasets(request),
});

export const useFileDatasetParsing = (id: string | undefined, enabled: boolean) => useQuery({
  queryKey: [fileDatasetsQueryKey, id, 'parsing'],
  queryFn: () => fetchFileDatasetParsing(id as string),
  enabled: enabled && Boolean(id),
});

export const useFileDatasetPreview = (id: string | undefined, enabled: boolean) => useQuery({
  queryKey: [fileDatasetsQueryKey, id, 'preview', 50],
  queryFn: () => fetchFileDatasetPreview(id as string),
  enabled: enabled && Boolean(id),
});

export const useCreateFileDataset = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createFileDataset,
    onSuccess: () => invalidateFileDatasets(queryClient),
  });
};

export const useUpdateFileDataset = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateFileDatasetRequest }) => updateFileDataset(id, request),
    onSuccess: () => invalidateFileDatasets(queryClient),
  });
};

export const useReplaceFileDatasetContent = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: replaceFileDatasetContent,
    onSuccess: () => invalidateFileDatasets(queryClient),
  });
};

export const useConfigureFileDatasetParsing = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: configureFileDatasetParsing,
    onSuccess: async (response) => {
      queryClient.setQueryData([fileDatasetsQueryKey, response.id, 'parsing'], response);
      await queryClient.invalidateQueries({ queryKey: [fileDatasetsQueryKey] });
    },
  });
};

export const useParseFileDataset = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: parseFileDataset,
    onSuccess: async (response) => {
      queryClient.setQueryData([fileDatasetsQueryKey, response.id, 'parsing'], response);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: [fileDatasetsQueryKey] }),
        queryClient.invalidateQueries({ queryKey: [fileDatasetsQueryKey, response.id, 'preview'] }),
      ]);
    },
  });
};

export const useDownloadFileDatasetContent = () => useMutation({ mutationFn: downloadFileDatasetContent });

export const useDeleteFileDataset = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteFileDataset,
    onSuccess: () => invalidateFileDatasets(queryClient),
  });
};
