export type FileDatasetParseJobStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';
export type FileDatasetParseJobType = 'FILE_PREPARATION' | 'TABLE_SOURCE_VALIDATE';
import type { FileDatasetTableSourceLoadMode } from './fileDataset';

export interface FileDatasetParseJob {
  id: string;
  type: FileDatasetParseJobType;
  fileDatasetId: string;
  fileDatasetName: string | null;
  sourceFileId: string;
  sourceFileName: string | null;
  fileDatasetTableId: string | null;
  tableName: string | null;
  loadMode: FileDatasetTableSourceLoadMode | null;
  targetSourceId: string | null;
  sourceName: string | null;
  sourceKey: string | null;
  status: FileDatasetParseJobStatus;
  attemptCount: number;
  maxAttempts: number;
  availableAt: string;
  queuedAt: string;
  startedAt: string | null;
  completedAt: string | null;
  leaseOwner: string | null;
  leaseExpiresAt: string | null;
  lastHeartbeatAt: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface FileDatasetParseQueueSummary {
  queueEnabled: boolean;
  configuredWorkerConcurrency: number;
  historyRetentionDays: number;
  queuedCount: number;
  runnableQueuedCount: number;
  retryWaitingCount: number;
  runningCount: number;
  succeededCount: number;
  failedCount: number;
  cancelledCount: number;
  oldestQueuedAt: string | null;
  oldestRunningAt: string | null;
  generatedAt: string;
}

export interface FileDatasetParseJobFilters {
  status?: FileDatasetParseJobStatus;
}

export const fileDatasetParseJobStatusLabels: Record<FileDatasetParseJobStatus, string> = {
  QUEUED: '排队中',
  RUNNING: '运行中',
  SUCCEEDED: '成功',
  FAILED: '失败',
  CANCELLED: '已取消',
};

export const fileDatasetParseJobTypeLabels: Record<FileDatasetParseJobType, string> = {
  FILE_PREPARATION: '文件准备',
  TABLE_SOURCE_VALIDATE: '表来源校验',
};

export const fileDatasetParseJobLoadModeLabels: Record<FileDatasetTableSourceLoadMode, string> = {
  INITIAL: '初始装载',
  APPEND: '追加',
  REPLACE_ALL: '全量覆盖',
  REPLACE_SOURCE: '替换来源',
};

export const fileDatasetParseJobStatusOptions = Object.entries(fileDatasetParseJobStatusLabels)
  .map(([value, label]) => ({ value: value as FileDatasetParseJobStatus, label }));

export const buildFileDatasetParseJobSearch = (
  filters: FileDatasetParseJobFilters,
): string | undefined => filters.status ? `status:"${filters.status}"` : undefined;

export const FILE_DATASET_PARSE_JOB_MONITOR_INTERVAL_MS = 5_000;

export const fileDatasetParseJobMonitorInterval = (enabled: boolean): number | false => (
  enabled ? FILE_DATASET_PARSE_JOB_MONITOR_INTERVAL_MS : false
);
