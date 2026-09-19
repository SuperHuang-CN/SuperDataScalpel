import type { PageResponse } from '../../../shared/api/pageResponse';
import {
  isActiveFileDatasetParseStatus,
  type FileDatasetFile,
  type FileDatasetTable,
} from './fileDataset';

export const FILE_DATASET_TABLE_POLL_INTERVAL_MS = 2_000;

export const fileDatasetTablePollingInterval = (
  data: PageResponse<FileDatasetTable> | undefined,
): number | false => (
  data?.content.some((table) => (
    isActiveFileDatasetParseStatus(table.parseStatus) || Boolean(table.currentLoadJobId)
  ))
    ? FILE_DATASET_TABLE_POLL_INTERVAL_MS
    : false
);

export const fileDatasetFilePollingInterval = (
  data: PageResponse<FileDatasetFile> | undefined,
): number | false => (
  data?.content.some((file) => file.status === 'PREPARING')
    ? FILE_DATASET_TABLE_POLL_INTERVAL_MS
    : false
);
