import { describe, expect, it } from 'vitest';
import type { PageResponse } from '../../../shared/api/pageResponse';
import type {
  FileDatasetFile,
  FileDatasetParseStatus,
  FileDatasetTable,
} from './fileDataset';
import {
  FILE_DATASET_TABLE_POLL_INTERVAL_MS,
  fileDatasetFilePollingInterval,
  fileDatasetTablePollingInterval,
} from './fileDatasetPolling';

const page = (...statuses: FileDatasetParseStatus[]): PageResponse<FileDatasetTable> => ({
  content: statuses.map((parseStatus, index) => ({
    id: `table-${index}`,
    fileDatasetId: 'dataset-1',
    code: `table_${index}`,
    name: `Table ${index}`,
    parseStatus,
    sourceCount: parseStatus === 'READY' ? 1 : 0,
    totalRowCount: 0,
    currentLoadJobId: parseStatus === 'QUEUED' || parseStatus === 'PARSING' ? `job-${index}` : null,
    sampledRecordCount: 0,
    truncated: false,
    previewSupported: parseStatus !== 'SCHEMA_READY',
    sourceMetadata: {},
    createdAt: '2026-07-19T00:00:00Z',
    updatedAt: '2026-07-19T00:00:00Z',
  })),
  totalElements: statuses.length,
  totalPages: statuses.length === 0 ? 0 : 1,
  page: 0,
  size: 500,
});

const filePage = (status: FileDatasetFile['status']): PageResponse<FileDatasetFile> => ({
  content: [{
    id: 'file-1', fileDatasetId: 'dataset-1', originalFileName: 'city.zip', format: 'GDB',
    compression: 'ZIP', contentType: 'application/zip', sizeBytes: 100, status,
    storageKind: 'GDB_DIRECTORY', materializedSizeBytes: null, materializedEntryCount: null,
    currentPreparationJobId: status === 'PREPARING' ? 'job-1' : null,
    createdAt: '2026-07-19T00:00:00Z', updatedAt: '2026-07-19T00:00:00Z',
  }],
  totalElements: 1, totalPages: 1, page: 0, size: 200,
});

describe('file dataset table polling', () => {
  it.each(['QUEUED', 'PARSING'] satisfies FileDatasetParseStatus[])(
    'polls every two seconds while a table is %s',
    (status) => {
      expect(fileDatasetTablePollingInterval(page('READY', status)))
        .toBe(FILE_DATASET_TABLE_POLL_INTERVAL_MS);
    },
  );

  it('stops when there is no active parsing task', () => {
    expect(fileDatasetTablePollingInterval(page())).toBe(false);
    expect(fileDatasetTablePollingInterval(page('READY', 'SCHEMA_READY'))).toBe(false);
    expect(fileDatasetTablePollingInterval(undefined)).toBe(false);
  });

  it('polls files while an archive-backed file is preparing', () => {
    expect(fileDatasetFilePollingInterval(filePage('PREPARING'))).toBe(FILE_DATASET_TABLE_POLL_INTERVAL_MS);
    expect(fileDatasetFilePollingInterval(filePage('READY'))).toBe(false);
  });
});
