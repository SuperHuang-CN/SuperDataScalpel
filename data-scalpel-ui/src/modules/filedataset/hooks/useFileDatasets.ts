import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { SearchRequest } from '../../../shared/search';
import { invalidateDirectoryTree } from '../../directory';
import {
  appendFileDatasetTable,
  createFileDataset,
  deleteFileDataset,
  deleteFileDatasetFile,
  deleteFileDatasetTableSource,
  downloadFileDatasetFile,
  fetchFileDataset,
  fetchFileDatasetCanvasMetadata,
  fetchFileDatasetFiles,
  fetchFileDatasetPreview,
  fetchFileDatasetParseJobs,
  fetchFileDatasetParseQueueSummary,
  fetchFileDatasetSchema,
  fetchFileDatasets,
  fetchFileDatasetTables,
  fetchFileDatasetTableSources,
  replaceFileDatasetFile,
  replaceFileDatasetTableData,
  replaceFileDatasetTableSource,
  updateFileDataset,
  updateFileDatasetTable,
  updateFileDatasetTableSpatialReference,
  uploadFileDatasetFiles,
} from '../api/fileDatasetApi';
import type { FileDatasetTableLoadSubmission, UpdateFileDatasetRequest } from '../model/fileDataset';
import {
  fileDatasetFilePollingInterval,
  fileDatasetTablePollingInterval,
} from '../model/fileDatasetPolling';
import { fileDatasetParseJobMonitorInterval } from '../model/fileDatasetParseJob';
import { fileDatasetQueryKeys } from '../model/fileDatasetQueryKeys';

const invalidateFileDatasets = async (queryClient: ReturnType<typeof useQueryClient>) => {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: fileDatasetQueryKeys.all }),
    invalidateDirectoryTree(queryClient, 'FILE_DATASET'),
  ]);
};

const invalidateDatasetChildren = async (queryClient: ReturnType<typeof useQueryClient>, datasetId: string) => {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: fileDatasetQueryKeys.all }),
    queryClient.invalidateQueries({ queryKey: fileDatasetQueryKeys.dataset(datasetId) }),
  ]);
};

const invalidateTableLoad = async (
  queryClient: ReturnType<typeof useQueryClient>,
  datasetId: string,
  tableId: string,
) => {
  await Promise.all([
    invalidateDatasetChildren(queryClient, datasetId),
    queryClient.invalidateQueries({ queryKey: fileDatasetQueryKeys.table(datasetId, tableId) }),
    queryClient.invalidateQueries({ queryKey: fileDatasetQueryKeys.sources(datasetId, tableId) }),
    queryClient.invalidateQueries({ queryKey: fileDatasetQueryKeys.schema(datasetId, tableId) }),
    queryClient.invalidateQueries({ queryKey: fileDatasetQueryKeys.preview(datasetId, tableId, 50) }),
    queryClient.invalidateQueries({ queryKey: ['file-dataset-tables', 'canvas-metadata'] }),
  ]);
};

export const useFileDatasets = (request: SearchRequest) => useQuery({
  queryKey: fileDatasetQueryKeys.list(request),
  queryFn: () => fetchFileDatasets(request),
});

export const useFileDataset = (id: string | undefined, enabled = true) => useQuery({
  queryKey: fileDatasetQueryKeys.dataset(id as string),
  queryFn: () => fetchFileDataset(id as string),
  enabled: enabled && Boolean(id),
});

export const useFileDatasetCanvasMetadata = (
  fileDatasetTableIds: string[],
  enabled = true,
) => {
  const ids = [...new Set(fileDatasetTableIds.filter(Boolean))].sort();
  return useQuery({
    queryKey: ['file-dataset-tables', 'canvas-metadata', ids],
    queryFn: () => fetchFileDatasetCanvasMetadata(ids),
    enabled: enabled && ids.length > 0,
    staleTime: 30_000,
  });
};

export const useFileDatasetFiles = (id: string | undefined, enabled: boolean) => useQuery({
  queryKey: fileDatasetQueryKeys.files(id),
  queryFn: () => fetchFileDatasetFiles(id as string, {
    page: 0,
    size: 200,
    sort: 'createdAt,originalFileName',
  }),
  enabled: enabled && Boolean(id),
  refetchInterval: (query) => fileDatasetFilePollingInterval(query.state.data),
  refetchIntervalInBackground: false,
});

export const useFileDatasetTables = (id: string | undefined, enabled: boolean) => useQuery({
  queryKey: fileDatasetQueryKeys.tables(id),
  queryFn: () => fetchFileDatasetTables(id as string, { page: 0, size: 500, sort: 'createdAt' }),
  enabled: enabled && Boolean(id),
  refetchInterval: (query) => fileDatasetTablePollingInterval(query.state.data),
  refetchIntervalInBackground: false,
});

export const useFileDatasetTableSources = (
  datasetId: string | undefined,
  tableId: string | undefined,
  enabled: boolean,
) => useQuery({
  queryKey: fileDatasetQueryKeys.sources(datasetId, tableId),
  queryFn: () => fetchFileDatasetTableSources(datasetId as string, tableId as string),
  enabled: enabled && Boolean(datasetId) && Boolean(tableId),
});

export const useFileDatasetSchema = (datasetId: string | undefined, tableId: string | undefined, enabled: boolean) => useQuery({
  queryKey: fileDatasetQueryKeys.schema(datasetId, tableId),
  queryFn: () => fetchFileDatasetSchema(datasetId as string, tableId as string),
  enabled: enabled && Boolean(datasetId) && Boolean(tableId),
});

export const useFileDatasetPreview = (datasetId: string | undefined, tableId: string | undefined, enabled: boolean) => useQuery({
  queryKey: fileDatasetQueryKeys.preview(datasetId, tableId, 50),
  queryFn: () => fetchFileDatasetPreview(datasetId as string, tableId as string),
  enabled: enabled && Boolean(datasetId) && Boolean(tableId),
});

export const useCreateFileDataset = () => {
  const queryClient = useQueryClient();
  return useMutation({ mutationFn: createFileDataset, onSuccess: () => invalidateFileDatasets(queryClient) });
};

export const useUpdateFileDataset = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateFileDatasetRequest }) => updateFileDataset(id, request),
    onSuccess: () => invalidateFileDatasets(queryClient),
  });
};

export const useUploadFileDatasetFiles = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: uploadFileDatasetFiles,
    onSuccess: (_response, input) => invalidateDatasetChildren(queryClient, input.id),
  });
};

export const useReplaceFileDatasetFile = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: replaceFileDatasetFile,
    onSuccess: (_response, input) => invalidateDatasetChildren(queryClient, input.datasetId),
  });
};

export const useDeleteFileDatasetFile = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteFileDatasetFile,
    onSuccess: (_response, input) => invalidateDatasetChildren(queryClient, input.datasetId),
  });
};

export const useDownloadFileDatasetFile = () => useMutation({ mutationFn: downloadFileDatasetFile });

export const useUpdateFileDatasetTable = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: updateFileDatasetTable,
    onSuccess: (_response, input) => invalidateDatasetChildren(queryClient, input.datasetId),
  });
};

export const useUpdateFileDatasetTableSpatialReference = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: updateFileDatasetTableSpatialReference,
    onSuccess: async (_response, input) => {
      await invalidateTableLoad(queryClient, input.datasetId, input.tableId);
    },
  });
};

const useTableLoadMutation = <TInput extends { datasetId: string; tableId: string }>(
  mutationFn: (input: TInput) => Promise<FileDatasetTableLoadSubmission>,
) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: async (_response, input) => {
      await invalidateTableLoad(queryClient, input.datasetId, input.tableId);
    },
  });
};

export const useAppendFileDatasetTable = () => useTableLoadMutation(appendFileDatasetTable);

export const useReplaceFileDatasetTableData = () => useTableLoadMutation(replaceFileDatasetTableData);

export const useReplaceFileDatasetTableSource = () => useTableLoadMutation(replaceFileDatasetTableSource);

export const useDeleteFileDatasetTableSource = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteFileDatasetTableSource,
    onSuccess: async (_response, input) => {
      await invalidateTableLoad(queryClient, input.datasetId, input.tableId);
    },
  });
};

export const useDeleteFileDataset = () => {
  const queryClient = useQueryClient();
  return useMutation({ mutationFn: deleteFileDataset, onSuccess: () => invalidateFileDatasets(queryClient) });
};

export const useFileDatasetParseQueueSummary = (enabled: boolean) => useQuery({
  queryKey: fileDatasetQueryKeys.parseJobSummary(),
  queryFn: fetchFileDatasetParseQueueSummary,
  enabled,
  refetchInterval: fileDatasetParseJobMonitorInterval(enabled),
  refetchIntervalInBackground: false,
});

export const useFileDatasetParseJobs = (request: SearchRequest, enabled: boolean) => useQuery({
  queryKey: fileDatasetQueryKeys.parseJobs(request),
  queryFn: () => fetchFileDatasetParseJobs(request),
  enabled,
  refetchInterval: fileDatasetParseJobMonitorInterval(enabled),
  refetchIntervalInBackground: false,
});
