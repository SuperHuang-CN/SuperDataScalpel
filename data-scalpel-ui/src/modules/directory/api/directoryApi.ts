import { requestBlob, requestJson } from '../../../shared/api/http';
import type {
  CreateDirectoryRequest,
  DirectoryImportResult,
  DirectoryResponse,
  DirectoryScope,
  DirectoryTreeNode,
  UpdateDirectoryRequest,
} from '../model/directory';

const DIRECTORY_PATH = '/v1/directories';

export const fetchDirectoryTree = (scope: DirectoryScope): Promise<DirectoryTreeNode[]> => (
  requestJson<DirectoryTreeNode[]>(`${DIRECTORY_PATH}?scope=${scope}`)
);

export const createDirectory = (request: CreateDirectoryRequest): Promise<DirectoryResponse> => (
  requestJson<DirectoryResponse>(DIRECTORY_PATH, { method: 'POST', body: JSON.stringify(request) })
);

export const updateDirectory = (id: string, request: UpdateDirectoryRequest): Promise<DirectoryResponse> => (
  requestJson<DirectoryResponse>(`${DIRECTORY_PATH}/${id}/actions/update`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
);

export const deleteDirectory = (id: string): Promise<void> => (
  requestJson<void>(`${DIRECTORY_PATH}/${id}/actions/delete`, { method: 'POST' })
);

export const downloadDirectoryImportTemplate = (): Promise<Blob> => (
  requestBlob(`${DIRECTORY_PATH}/actions/download-import-template`)
);

export const exportDirectoryTree = (scope: DirectoryScope): Promise<Blob> => (
  requestBlob(`${DIRECTORY_PATH}/actions/export?scope=${encodeURIComponent(scope)}`)
);

export const importDirectoryTree = (scope: DirectoryScope, file: File): Promise<DirectoryImportResult> => {
  const formData = new FormData();
  formData.append('file', file);
  return requestJson<DirectoryImportResult>(
    `${DIRECTORY_PATH}/actions/import?scope=${encodeURIComponent(scope)}`,
    { method: 'POST', body: formData },
    60_000,
  );
};
