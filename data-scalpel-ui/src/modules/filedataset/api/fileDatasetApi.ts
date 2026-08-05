import { requestBlob, requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type {
  CreateFileDatasetRequest,
  FileDataset,
  FileDatasetCanvasMetadata,
  FileDatasetFile,
  FileDatasetPreview,
  FileDatasetSchema,
  FileDatasetTable,
  FileDatasetTableLoadSubmission,
  FileDatasetTableSource,
  FileDatasetUploadResult,
  UpdateFileDatasetRequest,
} from '../model/fileDataset';
import type {
  FileDatasetParseJob,
  FileDatasetParseQueueSummary,
} from '../model/fileDatasetParseJob';

const FILE_DATASET_PATH = '/v1/file-datasets';
const FILE_DATASET_TABLE_PATH = '/v1/file-dataset-tables';
const FILE_DATASET_PARSE_JOB_PATH = '/v1/file-dataset-parse-jobs';
const FILE_OPERATION_TIMEOUT = 5 * 60 * 1000;

const listPath = (path: string, request: SearchRequest): string => {
  const query = toSearchParams(request).toString();
  return query ? `${path}?${query}` : path;
};

export const fetchFileDatasets = (request: SearchRequest): Promise<PageResponse<FileDataset>> => (
  requestJson<PageResponse<FileDataset>>(listPath(FILE_DATASET_PATH, request))
);

export const fetchFileDataset = (id: string): Promise<FileDataset> => (
  requestJson<FileDataset>(`${FILE_DATASET_PATH}/${id}`)
);

export const fetchFileDatasetCanvasMetadata = (
  fileDatasetTableIds: string[],
): Promise<FileDatasetCanvasMetadata> => (
  requestJson<FileDatasetCanvasMetadata>(
    `${FILE_DATASET_TABLE_PATH}/actions/query-canvas-metadata`,
    {
      method: 'POST',
      body: JSON.stringify({ fileDatasetTableIds: [...new Set(fileDatasetTableIds)] }),
    },
  )
);

export const createFileDataset = (request: CreateFileDatasetRequest): Promise<FileDataset> => (
  requestJson<FileDataset>(FILE_DATASET_PATH, { method: 'POST', body: JSON.stringify(request) })
);

export const updateFileDataset = (id: string, request: UpdateFileDatasetRequest): Promise<FileDataset> => (
  requestJson<FileDataset>(`${FILE_DATASET_PATH}/${id}/actions/update`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
);

export const fetchFileDatasetFiles = (id: string, request: SearchRequest): Promise<PageResponse<FileDatasetFile>> => (
  requestJson<PageResponse<FileDatasetFile>>(listPath(`${FILE_DATASET_PATH}/${id}/files`, request))
);

export const uploadFileDatasetFiles = (input: { id: string; files: File[] }): Promise<FileDatasetUploadResult> => {
  const body = new FormData();
  input.files.forEach((file) => body.append('files', file));
  return requestJson<FileDatasetUploadResult>(`${FILE_DATASET_PATH}/${input.id}/files`, {
    method: 'POST', body,
  }, FILE_OPERATION_TIMEOUT);
};

export const replaceFileDatasetFile = (input: { datasetId: string; fileId: string; file: File }): Promise<FileDatasetUploadResult> => {
  const body = new FormData();
  body.append('file', input.file);
  return requestJson<FileDatasetUploadResult>(`${FILE_DATASET_PATH}/${input.datasetId}/files/${input.fileId}/actions/replace`, {
    method: 'POST', body,
  }, FILE_OPERATION_TIMEOUT);
};

export const deleteFileDatasetFile = (input: { datasetId: string; fileId: string }): Promise<void> => (
  requestJson<void>(`${FILE_DATASET_PATH}/${input.datasetId}/files/${input.fileId}/actions/delete`, { method: 'POST' })
);

export const downloadFileDatasetFile = (input: { datasetId: string; fileId: string }): Promise<Blob> => (
  requestBlob(`${FILE_DATASET_PATH}/${input.datasetId}/files/${input.fileId}/content`, {}, FILE_OPERATION_TIMEOUT)
);

export const fetchFileDatasetTables = (id: string, request: SearchRequest): Promise<PageResponse<FileDatasetTable>> => (
  requestJson<PageResponse<FileDatasetTable>>(listPath(`${FILE_DATASET_PATH}/${id}/tables`, request))
);

export const updateFileDatasetTable = (input: { datasetId: string; tableId: string; name: string }): Promise<FileDatasetTable> => (
  requestJson<FileDatasetTable>(`${FILE_DATASET_PATH}/${input.datasetId}/tables/${input.tableId}/actions/update`, {
    method: 'POST', body: JSON.stringify({ name: input.name }),
  })
);

export const updateFileDatasetTableSpatialReference = (input: {
  datasetId: string;
  tableId: string;
  epsgCode: number;
}): Promise<FileDatasetTable> => requestJson<FileDatasetTable>(
  `${FILE_DATASET_PATH}/${input.datasetId}/tables/${input.tableId}/actions/update-spatial-reference`,
  {
    method: 'POST',
    body: JSON.stringify({ authority: 'EPSG', code: input.epsgCode }),
  },
  FILE_OPERATION_TIMEOUT,
);

export const fetchFileDatasetTableSources = (
  datasetId: string,
  tableId: string,
): Promise<FileDatasetTableSource[]> => (
  requestJson<FileDatasetTableSource[]>(`${FILE_DATASET_PATH}/${datasetId}/tables/${tableId}/sources`)
);

const submitTableFile = (
  path: string,
  file: File,
): Promise<FileDatasetTableLoadSubmission> => {
  const body = new FormData();
  body.append('file', file);
  return requestJson<FileDatasetTableLoadSubmission>(path, { method: 'POST', body }, FILE_OPERATION_TIMEOUT);
};

export const appendFileDatasetTable = (input: {
  datasetId: string;
  tableId: string;
  file: File;
}): Promise<FileDatasetTableLoadSubmission> => submitTableFile(
  `${FILE_DATASET_PATH}/${input.datasetId}/tables/${input.tableId}/actions/append`,
  input.file,
);

export const replaceFileDatasetTableData = (input: {
  datasetId: string;
  tableId: string;
  file: File;
}): Promise<FileDatasetTableLoadSubmission> => submitTableFile(
  `${FILE_DATASET_PATH}/${input.datasetId}/tables/${input.tableId}/actions/replace-data`,
  input.file,
);

export const replaceFileDatasetTableSource = (input: {
  datasetId: string;
  tableId: string;
  sourceId: string;
  file: File;
}): Promise<FileDatasetTableLoadSubmission> => submitTableFile(
  `${FILE_DATASET_PATH}/${input.datasetId}/tables/${input.tableId}/sources/${input.sourceId}/actions/replace`,
  input.file,
);

export const deleteFileDatasetTableSource = (input: {
  datasetId: string;
  tableId: string;
  sourceId: string;
}): Promise<void> => requestJson<void>(
  `${FILE_DATASET_PATH}/${input.datasetId}/tables/${input.tableId}/sources/${input.sourceId}/actions/delete`,
  { method: 'POST' },
);

export const fetchFileDatasetSchema = (datasetId: string, tableId: string): Promise<FileDatasetSchema> => (
  requestJson<FileDatasetSchema>(`${FILE_DATASET_PATH}/${datasetId}/tables/${tableId}/schema`)
);

export const fetchFileDatasetPreview = (datasetId: string, tableId: string, limit = 50): Promise<FileDatasetPreview> => (
  requestJson<FileDatasetPreview>(
    `${FILE_DATASET_PATH}/${datasetId}/tables/${tableId}/preview?limit=${limit}`,
    {},
    FILE_OPERATION_TIMEOUT,
  )
);

export const deleteFileDataset = (id: string): Promise<void> => (
  requestJson<void>(`${FILE_DATASET_PATH}/${id}/actions/delete`, { method: 'POST' })
);

export const fetchFileDatasetParseQueueSummary = (): Promise<FileDatasetParseQueueSummary> => (
  requestJson<FileDatasetParseQueueSummary>(`${FILE_DATASET_PARSE_JOB_PATH}/summary`)
);

export const fetchFileDatasetParseJobs = (
  request: SearchRequest,
): Promise<PageResponse<FileDatasetParseJob>> => (
  requestJson<PageResponse<FileDatasetParseJob>>(listPath(FILE_DATASET_PARSE_JOB_PATH, request))
);
