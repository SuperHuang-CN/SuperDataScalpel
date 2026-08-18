import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { SearchRequest } from '../../../shared/search';
import {
  createDataEntryForm,
  deleteDataEntries,
  deleteDataEntryForm,
  executeDataEntryCommand,
  fetchDataEntryCandidates,
  fetchDataEntryForm,
  fetchDataEntryForms,
  fetchDataEntryOperationLog,
  fetchDataEntryOperationLogs,
  importDataEntryFile,
  previewDataEntryImport,
  queryDataEntryOptions,
  submitDataEntry,
  updateDataEntryLookups,
} from '../api/dataEntryApi';
import type { DataEntryFormStatus, DataEntryLookupInput } from '../model/dataEntry';

const key = ['data-entry'] as const;

export const useDataEntryForms = (parameters: { status?: DataEntryFormStatus; keyword?: string; page: number; size: number }) => useQuery({
  queryKey: [...key, 'forms', parameters], queryFn: () => fetchDataEntryForms(parameters),
});
export const useDataEntryCandidates = (enabled: boolean, keyword?: string) => useQuery({
  queryKey: [...key, 'candidates', keyword], queryFn: () => fetchDataEntryCandidates(keyword), enabled,
});
export const useDataEntryForm = (id?: string) => useQuery({
  queryKey: [...key, id], queryFn: () => fetchDataEntryForm(id as string), enabled: Boolean(id),
});
export const useDataEntryOperationLogs = (id: string | undefined, request: SearchRequest) => useQuery({
  queryKey: [...key, id, 'logs', request], queryFn: () => fetchDataEntryOperationLogs(id as string, request), enabled: Boolean(id),
});
export const useDataEntryOperationLog = (id?: string, logId?: string) => useQuery({
  queryKey: [...key, id, 'logs', logId], queryFn: () => fetchDataEntryOperationLog(id as string, logId as string), enabled: Boolean(id && logId),
});

export const useCreateDataEntryForm = () => {
  const client = useQueryClient();
  return useMutation({ mutationFn: createDataEntryForm, onSuccess: () => client.invalidateQueries({ queryKey: key }) });
};
export const useUpdateDataEntryLookups = () => {
  const client = useQueryClient();
  return useMutation({ mutationFn: ({ id, lookups }: { id: string; lookups: DataEntryLookupInput[] }) => updateDataEntryLookups(id, lookups), onSuccess: () => client.invalidateQueries({ queryKey: key }) });
};
export const useDataEntryCommand = () => {
  const client = useQueryClient();
  return useMutation({ mutationFn: ({ id, command }: { id: string; command: 'publish' | 'disable' }) => executeDataEntryCommand(id, command), onSuccess: () => client.invalidateQueries({ queryKey: key }) });
};
export const useDeleteDataEntryForm = () => {
  const client = useQueryClient();
  return useMutation({ mutationFn: deleteDataEntryForm, onSuccess: () => client.invalidateQueries({ queryKey: key }) });
};
export const useSubmitDataEntry = () => {
  const client = useQueryClient();
  return useMutation({ mutationFn: ({ id, values }: { id: string; values: Record<string, unknown> }) => submitDataEntry(id, values), onSuccess: () => client.invalidateQueries({ queryKey: key }) });
};
export const usePreviewDataEntryImport = () => useMutation({
  mutationFn: ({ id, file }: { id: string; file: File }) => previewDataEntryImport(id, file),
});
export const useImportDataEntryFile = () => {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, file, previewDigest }: { id: string; file: File; previewDigest: string }) => (
      importDataEntryFile(id, file, previewDigest)
    ),
    onSuccess: () => client.invalidateQueries({ queryKey: key }),
  });
};
export const useDeleteDataEntries = () => {
  const client = useQueryClient();
  return useMutation({ mutationFn: ({ id, keys }: { id: string; keys: Record<string, unknown>[] }) => deleteDataEntries(id, keys), onSuccess: () => client.invalidateQueries({ queryKey: key }) });
};
export const useDataEntryOptions = (id: string, fieldId: string) => useMutation({ mutationFn: (request: { keyword?: string; pageNo?: number; pageSize?: number; values?: unknown[] }) => queryDataEntryOptions(id, fieldId, request) });
