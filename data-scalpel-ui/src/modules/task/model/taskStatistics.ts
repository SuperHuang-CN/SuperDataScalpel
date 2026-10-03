import type { TaskType } from './task';
export interface TaskStatistics { collectedAt: string; total: number; types: { type: TaskType; count: number }[] }
