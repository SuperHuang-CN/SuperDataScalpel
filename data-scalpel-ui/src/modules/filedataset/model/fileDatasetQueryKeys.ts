import type { SearchRequest } from '../../../shared/search';

const root = ['file-datasets'] as const;

/** Stable, table-aware TanStack Query keys for the file dataset resource tree. */
export const fileDatasetQueryKeys = {
  all: root,
  list: (request: SearchRequest) => [...root, 'list', request] as const,
  dataset: (datasetId: string | undefined) => [...root, datasetId] as const,
  files: (datasetId: string | undefined) => [...root, datasetId, 'files'] as const,
  tables: (datasetId: string | undefined) => [...root, datasetId, 'tables'] as const,
  table: (datasetId: string, tableId: string) => [...root, datasetId, 'tables', tableId] as const,
  sources: (datasetId: string | undefined, tableId: string | undefined) => (
    [...root, datasetId, 'tables', tableId, 'sources'] as const
  ),
  schema: (datasetId: string | undefined, tableId: string | undefined) => (
    [...root, datasetId, 'tables', tableId, 'schema'] as const
  ),
  preview: (datasetId: string | undefined, tableId: string | undefined, limit: number) => (
    [...root, datasetId, 'tables', tableId, 'preview', limit] as const
  ),
  parseJobSummary: () => [...root, 'parse-jobs', 'summary'] as const,
  parseJobs: (request: SearchRequest) => [...root, 'parse-jobs', 'list', request] as const,
};
