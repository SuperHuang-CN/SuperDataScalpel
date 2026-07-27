import type { TaskRunExecutionMode, TaskRunStatus, TaskRunTriggerType } from './task';

export interface TaskRunFilters {
  status?: TaskRunStatus;
  triggerType?: TaskRunTriggerType;
  executionMode?: TaskRunExecutionMode;
}

export const buildTaskRunSearch = (filters: TaskRunFilters) => {
  const conditions = [
    filters.status ? `status:"${filters.status}"` : undefined,
    filters.triggerType ? `triggerType:"${filters.triggerType}"` : undefined,
    filters.executionMode ? `executionMode:"${filters.executionMode}"` : undefined,
  ].filter((condition): condition is string => Boolean(condition));
  return conditions.length ? conditions.join(' AND ') : undefined;
};
