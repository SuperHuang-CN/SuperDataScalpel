import { requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type {
  CreateDataTaskRequest,
  DataTask,
  LocalSqlDefinitionValidation,
  LocalSqlTaskDefinition,
  TaskRun,
  UpdateDataTaskRequest,
  UpdateLocalSqlTaskDefinitionRequest,
} from '../model/task';

const TASK_PATH = '/v1/tasks';

export const fetchTasks = async (request: SearchRequest): Promise<PageResponse<DataTask>> => {
  const query = toSearchParams(request).toString();
  return requestJson<PageResponse<DataTask>>(query ? `${TASK_PATH}?${query}` : TASK_PATH);
};

export const fetchTask = (id: string): Promise<DataTask> => requestJson<DataTask>(`${TASK_PATH}/${id}`);

export const fetchTaskDefinition = (id: string): Promise<LocalSqlTaskDefinition> => (
  requestJson<LocalSqlTaskDefinition>(`${TASK_PATH}/${id}/definition`)
);

export const createTask = (request: CreateDataTaskRequest): Promise<DataTask> => (
  requestJson<DataTask>(TASK_PATH, { method: 'POST', body: JSON.stringify(request) })
);

export const updateTask = (id: string, request: UpdateDataTaskRequest): Promise<DataTask> => (
  requestJson<DataTask>(`${TASK_PATH}/${id}/actions/update`, { method: 'POST', body: JSON.stringify(request) })
);

export const updateTaskDefinition = (
  id: string,
  request: UpdateLocalSqlTaskDefinitionRequest,
): Promise<LocalSqlTaskDefinition> => (
  requestJson<LocalSqlTaskDefinition>(`${TASK_PATH}/${id}/actions/update-definition`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
);

export const validateTaskDefinition = (id: string): Promise<LocalSqlDefinitionValidation> => (
  requestJson<LocalSqlDefinitionValidation>(`${TASK_PATH}/${id}/actions/validate-definition`, { method: 'POST' }, 60_000)
);

export type TaskCommand = 'publish' | 'disable' | 'enable';

export const executeTaskCommand = (id: string, command: TaskCommand): Promise<DataTask> => (
  requestJson<DataTask>(`${TASK_PATH}/${id}/actions/${command}`, { method: 'POST' }, 60_000)
);

export const deleteTask = (id: string): Promise<void> => (
  requestJson<void>(`${TASK_PATH}/${id}/actions/delete`, { method: 'POST' })
);

export const runTask = (id: string): Promise<TaskRun> => (
  requestJson<TaskRun>(`${TASK_PATH}/${id}/actions/run`, { method: 'POST' }, 60_000)
);

export const fetchTaskRuns = async (id: string, request: SearchRequest): Promise<PageResponse<TaskRun>> => {
  const query = toSearchParams(request).toString();
  const path = `${TASK_PATH}/${id}/runs`;
  return requestJson<PageResponse<TaskRun>>(query ? `${path}?${query}` : path);
};

export const fetchTaskRun = (runId: string): Promise<TaskRun> => requestJson<TaskRun>(`/v1/task-runs/${runId}`);
