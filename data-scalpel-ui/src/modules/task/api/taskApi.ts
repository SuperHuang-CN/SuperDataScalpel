import { requestBlob, requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type {
  CreateDataTaskRequest,
  CanvasTaskDefinition,
  DataTask,
  LocalSqlDefinitionValidation,
  LocalSqlTaskDefinition,
  ModelRelatedTask,
  ModelTaskRelationRole,
  TaskRun,
  TaskSchedule,
  TaskScheduleRequest,
  TaskStreamingConfiguration,
  TaskStreamingStatus,
  TaskModelRelations,
  UpdateDataTaskRequest,
  UpdateLocalSqlTaskDefinitionRequest,
  UpdateTaskStreamingConfigurationRequest,
} from '../model/task';
import type { CanvasDefinition } from '../canvas/canvasTypes';

const TASK_PATH = '/v1/tasks';

export const fetchTasks = async (request: SearchRequest): Promise<PageResponse<DataTask>> => {
  const query = toSearchParams(request).toString();
  return requestJson<PageResponse<DataTask>>(query ? `${TASK_PATH}?${query}` : TASK_PATH);
};

export const fetchTask = (id: string): Promise<DataTask> => requestJson<DataTask>(`${TASK_PATH}/${id}`);

export const fetchTaskDefinition = (id: string): Promise<LocalSqlTaskDefinition> => (
  requestJson<LocalSqlTaskDefinition>(`${TASK_PATH}/${id}/definition`)
);

export const fetchCanvasTaskDefinition = (id: string): Promise<CanvasTaskDefinition> => (
  requestJson<CanvasTaskDefinition>(`${TASK_PATH}/${id}/canvas-definition`)
);

export const fetchTaskModelRelations = (id: string): Promise<TaskModelRelations> => (
  requestJson<TaskModelRelations>(`${TASK_PATH}/${id}/model-relations`)
);

export const fetchModelRelatedTasks = async (
  modelId: string,
  role: ModelTaskRelationRole | undefined,
  request: SearchRequest,
): Promise<PageResponse<ModelRelatedTask>> => {
  const query = toSearchParams(request);
  if (role) query.set('role', role);
  const suffix = query.toString();
  const path = `/v1/models/${modelId}/related-tasks`;
  return requestJson<PageResponse<ModelRelatedTask>>(suffix ? `${path}?${suffix}` : path);
};

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

export const updateCanvasTaskDefinition = (
  id: string,
  definition: CanvasDefinition,
): Promise<CanvasTaskDefinition> => (
  requestJson<CanvasTaskDefinition>(`${TASK_PATH}/${id}/actions/update-canvas-definition`, {
    method: 'POST',
    body: JSON.stringify({ definition }),
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

export const fetchTaskStreamingConfiguration = (id: string): Promise<TaskStreamingConfiguration> => (
  requestJson<TaskStreamingConfiguration>(`${TASK_PATH}/${id}/streaming-configuration`)
);

export const updateTaskStreamingConfiguration = (
  id: string,
  request: UpdateTaskStreamingConfigurationRequest,
): Promise<TaskStreamingConfiguration> => (
  requestJson<TaskStreamingConfiguration>(`${TASK_PATH}/${id}/actions/update-streaming-configuration`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
);

export const fetchTaskStreamingStatus = (id: string): Promise<TaskStreamingStatus> => (
  requestJson<TaskStreamingStatus>(`${TASK_PATH}/${id}/streaming-status`)
);

export type TaskStreamingCommand = 'start' | 'stop';

export const executeTaskStreamingCommand = (
  id: string,
  command: TaskStreamingCommand,
): Promise<TaskStreamingStatus> => (
  requestJson<TaskStreamingStatus>(`${TASK_PATH}/${id}/actions/${command}`, { method: 'POST' }, 60_000)
);

export const fetchTaskRuns = async (id: string, request: SearchRequest): Promise<PageResponse<TaskRun>> => {
  const query = toSearchParams(request).toString();
  const path = `${TASK_PATH}/${id}/runs`;
  return requestJson<PageResponse<TaskRun>>(query ? `${path}?${query}` : path);
};

export const fetchTaskRun = (runId: string): Promise<TaskRun> => requestJson<TaskRun>(`/v1/task-runs/${runId}`);

export const cancelTaskRun = (runId: string): Promise<TaskRun> => (
  requestJson<TaskRun>(`/v1/task-runs/${runId}/actions/cancel`, { method: 'POST' })
);

export type TaskRunArtifactKind = 'result' | 'log';

export const downloadTaskRunArtifact = (
  runId: string,
  kind: TaskRunArtifactKind,
): Promise<Blob> => requestBlob(`/v1/task-runs/${runId}/artifacts/${kind}`, {}, 60_000);

export const fetchTaskSchedules = (taskId: string): Promise<TaskSchedule[]> => (
  requestJson<TaskSchedule[]>(`${TASK_PATH}/${taskId}/schedules`)
);

export const createTaskSchedule = (taskId: string, request: TaskScheduleRequest): Promise<TaskSchedule> => (
  requestJson<TaskSchedule>(`${TASK_PATH}/${taskId}/schedules`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
);

export const updateTaskSchedule = (scheduleId: string, request: TaskScheduleRequest): Promise<TaskSchedule> => (
  requestJson<TaskSchedule>(`/v1/task-schedules/${scheduleId}/actions/update`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
);

export type TaskScheduleCommand = 'enable' | 'disable';

export const executeTaskScheduleCommand = (
  scheduleId: string,
  command: TaskScheduleCommand,
): Promise<TaskSchedule> => (
  requestJson<TaskSchedule>(`/v1/task-schedules/${scheduleId}/actions/${command}`, { method: 'POST' })
);

export const deleteTaskSchedule = (scheduleId: string): Promise<void> => (
  requestJson<void>(`/v1/task-schedules/${scheduleId}/actions/delete`, { method: 'POST' })
);
