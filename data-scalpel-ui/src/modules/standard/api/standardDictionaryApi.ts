import { requestBlob, requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type {
  CreateStandardDictionaryItemRequest,
  CreateStandardDictionaryRequest,
  MoveStandardDictionaryItemRequest,
  StandardDictionary,
  StandardDictionaryDetail,
  StandardDictionaryFieldReference,
  StandardDictionaryImportPreview,
  StandardDictionaryImportResult,
  StandardDictionaryItemMutation,
  StandardDictionaryTreeNode,
  UpdateStandardDictionaryItemRequest,
  UpdateStandardDictionaryRequest,
} from '../model/standardDictionary';

const PATH = '/v1/standard-dictionaries';

export const fetchStandardDictionaries = async (
  request: SearchRequest,
): Promise<PageResponse<StandardDictionary>> => {
  const query = toSearchParams(request).toString();
  return requestJson<PageResponse<StandardDictionary>>(query ? `${PATH}?${query}` : PATH);
};

export const fetchStandardDictionary = (id: string): Promise<StandardDictionaryDetail> => (
  requestJson<StandardDictionaryDetail>(`${PATH}/${id}`)
);

export const createStandardDictionary = (
  request: CreateStandardDictionaryRequest,
): Promise<StandardDictionaryDetail> => requestJson<StandardDictionaryDetail>(PATH, {
  method: 'POST',
  body: JSON.stringify(request),
});

export const updateStandardDictionary = (
  id: string,
  request: UpdateStandardDictionaryRequest,
): Promise<StandardDictionaryDetail> => requestJson<StandardDictionaryDetail>(
  `${PATH}/${id}/actions/update`,
  { method: 'POST', body: JSON.stringify(request) },
);

export type StandardDictionaryCommand = 'enable' | 'disable' | 'delete';

export const executeStandardDictionaryCommand = (
  id: string,
  command: StandardDictionaryCommand,
  expectedVersion: number,
): Promise<StandardDictionaryDetail | void> => requestJson<StandardDictionaryDetail | void>(
  `${PATH}/${id}/actions/${command}`,
  { method: 'POST', body: JSON.stringify({ expectedVersion }) },
);

export const fetchStandardDictionaryTree = (
  dictionaryId: string,
): Promise<StandardDictionaryTreeNode[]> => requestJson<StandardDictionaryTreeNode[]>(
  `${PATH}/${dictionaryId}/items/tree`,
);

export const createStandardDictionaryItem = (
  dictionaryId: string,
  request: CreateStandardDictionaryItemRequest,
): Promise<StandardDictionaryItemMutation> => requestJson<StandardDictionaryItemMutation>(
  `${PATH}/${dictionaryId}/items`,
  { method: 'POST', body: JSON.stringify(request) },
);

export const updateStandardDictionaryItem = (
  dictionaryId: string,
  itemId: string,
  request: UpdateStandardDictionaryItemRequest,
): Promise<StandardDictionaryItemMutation> => requestJson<StandardDictionaryItemMutation>(
  `${PATH}/${dictionaryId}/items/${itemId}/actions/update`,
  { method: 'POST', body: JSON.stringify(request) },
);

export const moveStandardDictionaryItem = (
  dictionaryId: string,
  itemId: string,
  request: MoveStandardDictionaryItemRequest,
): Promise<StandardDictionaryItemMutation> => requestJson<StandardDictionaryItemMutation>(
  `${PATH}/${dictionaryId}/items/${itemId}/actions/move`,
  { method: 'POST', body: JSON.stringify(request) },
);

export type StandardDictionaryItemCommand = 'enable' | 'disable' | 'delete';

export const executeStandardDictionaryItemCommand = (
  dictionaryId: string,
  itemId: string,
  command: StandardDictionaryItemCommand,
  expectedVersion: number,
): Promise<StandardDictionaryItemMutation | void> => requestJson<StandardDictionaryItemMutation | void>(
  `${PATH}/${dictionaryId}/items/${itemId}/actions/${command}`,
  { method: 'POST', body: JSON.stringify({ expectedVersion }) },
);

export const fetchStandardDictionaryFieldReferences = async (
  dictionaryId: string,
  request: SearchRequest,
): Promise<PageResponse<StandardDictionaryFieldReference>> => {
  const query = toSearchParams(request).toString();
  const path = `${PATH}/${dictionaryId}/field-references`;
  return requestJson<PageResponse<StandardDictionaryFieldReference>>(query ? `${path}?${query}` : path);
};

export const downloadStandardDictionaryTemplate = (): Promise<Blob> => (
  requestBlob(`${PATH}/metadata-import-template`)
);

export const exportStandardDictionaryMetadata = (dictionaryIds: string[]): Promise<Blob> => (
  requestBlob(`${PATH}/actions/query-export-metadata`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ dictionaryIds }),
  })
);

export const previewStandardDictionaryImport = (file: File): Promise<StandardDictionaryImportPreview> => {
  const body = new FormData();
  body.append('file', file);
  return requestJson<StandardDictionaryImportPreview>(
    `${PATH}/actions/query-import-preview`,
    { method: 'POST', body },
    60_000,
  );
};

export const importStandardDictionaryMetadata = (
  file: File,
  previewDigest: string,
): Promise<StandardDictionaryImportResult> => {
  const body = new FormData();
  body.append('file', file);
  const query = new URLSearchParams({ previewDigest });
  return requestJson<StandardDictionaryImportResult>(
    `${PATH}/actions/import-metadata?${query.toString()}`,
    { method: 'POST', body },
    60_000,
  );
};
