import type { TaskRun, TaskRunStatus } from './task';

const ACTIVE_STATUSES = new Set<TaskRunStatus>([
  'QUEUED',
  'RUNNING',
  'CANCEL_REQUESTED',
  'STOP_REQUESTED',
]);

export const isActiveTaskRun = (run: TaskRun): boolean => ACTIVE_STATUSES.has(run.status);

export const formatTaskRunDateTime = (value: string | null): string => {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'short',
    timeStyle: 'medium',
    hour12: false,
  }).format(date);
};

export const formatTaskRunDuration = (run: TaskRun): string => {
  if (!run.startedAt || !run.endedAt) return '—';
  const milliseconds = Math.max(new Date(run.endedAt).getTime() - new Date(run.startedAt).getTime(), 0);
  if (!Number.isFinite(milliseconds)) return '—';
  if (milliseconds < 1_000) return `${milliseconds} ms`;
  if (milliseconds < 60_000) return `${(milliseconds / 1_000).toFixed(1)} 秒`;
  return `${Math.floor(milliseconds / 60_000)} 分 ${Math.round((milliseconds % 60_000) / 1_000)} 秒`;
};

export const safeTrackingUrl = (value: string | null): string | null => {
  if (!value) return null;
  try {
    const url = new URL(value);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : null;
  } catch {
    return null;
  }
};
