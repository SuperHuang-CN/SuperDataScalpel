import { requestBlob, requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type {
  DataEntryForm,
  DataEntryFormDetail,
  DataEntryFormStatus,
  DataEntryImportFormat,
  DataEntryImportPreview,
  DataEntryLookupInput,
  DataEntryModelCandidate,
  DataEntryMutationResponse,
  DataEntryOperationLog,
  DataEntryOptionResponse,
  DataModelDataQueryRequest,
  DataModelDataQueryResponse,
} from '../model/dataEntry';

const PATH = '/v1/data-entry-forms';

export const fetchDataEntryForms = (parameters: {
  status?: DataEntryFormStatus;
  keyword?: string;
  page: number;
  size: number;
}): Promise<PageResponse<DataEntryForm>> => {
  const query = new URLSearchParams();
  if (parameters.status) query.set('status', parameters.status);
  if (parameters.keyword) query.set('keyword', parameters.keyword);
  query.set('page', String(parameters.page));
  query.set('size', String(parameters.size));
  return requestJson<PageResponse<DataEntryForm>>(`${PATH}?${query.toString()}`);
};

export const fetchDataEntryCandidates = (keyword?: string): Promise<DataEntryModelCandidate[]> => {
  const query = keyword ? `?${new URLSearchParams({ keyword }).toString()}` : '';
  return requestJson<DataEntryModelCandidate[]>(`${PATH}/model-candidates${query}`);
};

export const fetchDataEntryForm = (id: string): Promise<DataEntryFormDetail> => requestJson(`${PATH}/${id}`);
export const createDataEntryForm = (modelId: string): Promise<DataEntryFormDetail> => requestJson(PATH, { method: 'POST', body: JSON.stringify({ modelId }) });
export const updateDataEntryLookups = (id: string, lookups: DataEntryLookupInput[]): Promise<DataEntryFormDetail> => requestJson(`${PATH}/${id}/actions/update-lookups`, { method: 'POST', body: JSON.stringify({ lookups }) });
export const executeDataEntryCommand = (id: string, command: 'publish' | 'disable'): Promise<DataEntryFormDetail> => requestJson(`${PATH}/${id}/actions/${command}`, { method: 'POST' });
export const deleteDataEntryForm = (id: string): Promise<void> => requestJson(`${PATH}/${id}/actions/delete`, { method: 'POST' });
export const submitDataEntry = (id: string, values: Record<string, unknown>): Promise<DataEntryMutationResponse> => requestJson(`${PATH}/${id}/entries`, { method: 'POST', body: JSON.stringify({ values }) });
export const downloadDataEntryImportTemplate = (id: string, format: DataEntryImportFormat): Promise<Blob> => requestBlob(
  `${PATH}/${id}/entries/import-template?${new URLSearchParams({ format }).toString()}`,
);
export const previewDataEntryImport = (id: string, file: File): Promise<DataEntryImportPreview> => {
  const body = new FormData();
  body.append('file', file);
  return requestJson<DataEntryImportPreview>(
    `${PATH}/${id}/entries/actions/preview-import`,
    { method: 'POST', body },
    300_000,
  );
};
export const importDataEntryFile = (id: string, file: File, previewDigest: string): Promise<DataEntryMutationResponse> => {
  const body = new FormData();
  body.append('file', file);
  const query = new URLSearchParams({ previewDigest });
  return requestJson<DataEntryMutationResponse>(
    `${PATH}/${id}/entries/actions/import?${query.toString()}`,
    { method: 'POST', body },
    300_000,
  );
};
export const deleteDataEntries = (id: string, keys: Record<string, unknown>[]): Promise<DataEntryMutationResponse> => requestJson(`${PATH}/${id}/entries/actions/delete-batch`, { method: 'POST', body: JSON.stringify({ keys }) });
export const queryDataEntryData = (id: string, request: DataModelDataQueryRequest): Promise<DataModelDataQueryResponse> => requestJson(`${PATH}/${id}/actions/query-data`, { method: 'POST', body: JSON.stringify(request) });
export const queryDataEntryOptions = (id: string, fieldId: string, request: { keyword?: string; pageNo?: number; pageSize?: number; values?: unknown[] }): Promise<DataEntryOptionResponse> => requestJson(`${PATH}/${id}/fields/${fieldId}/actions/query-options`, { method: 'POST', body: JSON.stringify(request) });

export const fetchDataEntryOperationLogs = (id: string, request: SearchRequest): Promise<PageResponse<DataEntryOperationLog>> => {
  const query = toSearchParams(request).toString();
  return requestJson(`${PATH}/${id}/operation-logs${query ? `?${query}` : ''}`);
};

export const fetchDataEntryOperationLog = (id: string, logId: string): Promise<DataEntryOperationLog> => requestJson(`${PATH}/${id}/operation-logs/${logId}`);
