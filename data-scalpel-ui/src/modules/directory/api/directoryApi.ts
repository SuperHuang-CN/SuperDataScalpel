import { requestJson } from '../../../shared/api/http';
import type {
  CreateDirectoryRequest,
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
