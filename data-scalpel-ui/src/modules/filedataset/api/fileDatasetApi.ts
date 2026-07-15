import { requestBlob, requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type {
  CreateFileDatasetRequest,
  FileDataset,
  FileDatasetFormat,
  FileDatasetParsing,
  FileDatasetParsingOptions,
  FileDatasetPreview,
  UpdateFileDatasetRequest,
} from '../model/fileDataset';

const FILE_DATASET_PATH = '/v1/file-datasets';
const FILE_OPERATION_TIMEOUT = 5 * 60 * 1000;

const multipartBody = (request: object, file: File): FormData => {
  const body = new FormData();
  body.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }));
  body.append('file', file);
  return body;
};

export const fetchFileDatasets = async (request: SearchRequest): Promise<PageResponse<FileDataset>> => {
  const query = toSearchParams(request).toString();
  return requestJson<PageResponse<FileDataset>>(query ? `${FILE_DATASET_PATH}?${query}` : FILE_DATASET_PATH);
};

export const createFileDataset = (
  input: { request: CreateFileDatasetRequest; file: File },
): Promise<FileDataset> => requestJson<FileDataset>(FILE_DATASET_PATH, {
  method: 'POST',
  body: multipartBody(input.request, input.file),
}, FILE_OPERATION_TIMEOUT);

export const updateFileDataset = (
  id: string,
  request: UpdateFileDatasetRequest,
): Promise<FileDataset> => requestJson<FileDataset>(`${FILE_DATASET_PATH}/${id}/actions/update`, {
  method: 'POST',
  body: JSON.stringify(request),
});

export const fetchFileDatasetParsing = (id: string): Promise<FileDatasetParsing> => (
  requestJson<FileDatasetParsing>(`${FILE_DATASET_PATH}/${id}/parsing`)
);

export const configureFileDatasetParsing = (
  input: { id: string; options: FileDatasetParsingOptions },
): Promise<FileDatasetParsing> => requestJson<FileDatasetParsing>(`${FILE_DATASET_PATH}/${input.id}/actions/configure-parsing`, {
  method: 'POST',
  body: JSON.stringify({ options: input.options }),
});

export const parseFileDataset = (id: string): Promise<FileDatasetParsing> => (
  requestJson<FileDatasetParsing>(`${FILE_DATASET_PATH}/${id}/actions/parse`, { method: 'POST' }, FILE_OPERATION_TIMEOUT)
);

export const fetchFileDatasetPreview = (id: string, limit = 50): Promise<FileDatasetPreview> => (
  requestJson<FileDatasetPreview>(`${FILE_DATASET_PATH}/${id}/preview?limit=${limit}`, {}, FILE_OPERATION_TIMEOUT)
);

export const replaceFileDatasetContent = (
  input: { id: string; format: FileDatasetFormat; file: File },
): Promise<FileDataset> => requestJson<FileDataset>(`${FILE_DATASET_PATH}/${input.id}/actions/replace-content`, {
  method: 'POST',
  body: multipartBody({ format: input.format }, input.file),
}, FILE_OPERATION_TIMEOUT);

export const downloadFileDatasetContent = (id: string): Promise<Blob> => (
  requestBlob(`${FILE_DATASET_PATH}/${id}/content`, {}, FILE_OPERATION_TIMEOUT)
);

export const deleteFileDataset = (id: string): Promise<void> => (
  requestJson<void>(`${FILE_DATASET_PATH}/${id}/actions/delete`, { method: 'POST' })
);
