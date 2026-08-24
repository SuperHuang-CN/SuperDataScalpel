import { requestBlob, requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type {
  CreateDataTaskRequest,
  CanvasTaskDefinition,
  DataTask,
  LocalSqlDefinitionValidation,
  LocalSqlTaskDefinition,
  ModelQualityTaskDefinition,
  SparkJarTaskDefinition,
  StreamingCheckpointMode,
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
  UpdateSparkJarTaskDefinitionRequest,
  UpdateTaskStreamingConfigurationRequest,
} from '../model/task';
import type { CanvasDefinition } from '../canvas/canvasTypes';
import { parseTaskExecutionResultArtifact, type TaskExecutionResultArtifact } from '../model/taskExecutionResult';
import type { TaskFieldLineageGraph, TaskLineageGraph } from '../model/taskLineage';
import type { PlatformTypeDefinition } from '../../model';
import type { TaskCanvasProposalChangeSet } from '../model/taskAssistant';

const TASK_PATH = '/v1/tasks';
const ASSISTANT_CHANGE_SET_PATH = '/v1/assistant/change-sets';

export const fetchTaskCanvasProposal = (id: string): Promise<TaskCanvasProposalChangeSet> => (
  requestJson<TaskCanvasProposalChangeSet>(`${ASSISTANT_CHANGE_SET_PATH}/${id}`)
);

export const acceptTaskCanvasProposal = (
  id: string,
  taskId: string,
): Promise<TaskCanvasProposalChangeSet> => requestJson<TaskCanvasProposalChangeSet>(
  `${ASSISTANT_CHANGE_SET_PATH}/${id}/actions/accept-task-canvas`,
  { method: 'POST', body: JSON.stringify({ taskId }) },
);

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

export const fetchModelQualityTaskDefinition = (id: string): Promise<ModelQualityTaskDefinition> => (
  requestJson<ModelQualityTaskDefinition>(`${TASK_PATH}/${id}/model-quality-definition`)
);

export const fetchSparkJarTaskDefinition = (id: string): Promise<SparkJarTaskDefinition> => (
  requestJson<SparkJarTaskDefinition>(`${TASK_PATH}/${id}/spark-jar-definition`)
);

export const updateSparkJarTaskDefinition = (
  id: string,
  request: UpdateSparkJarTaskDefinitionRequest,
): Promise<SparkJarTaskDefinition> => requestJson<SparkJarTaskDefinition>(
  `${TASK_PATH}/${id}/actions/update-spark-jar-definition`,
  { method: 'POST', body: JSON.stringify(request) },
);

export const uploadSparkJar = (id: string, file: File): Promise<SparkJarTaskDefinition> => {
  const body = new FormData();
  body.append('file', file);
  return requestJson<SparkJarTaskDefinition>(
    `${TASK_PATH}/${id}/actions/upload-spark-jar`,
    { method: 'POST', body },
    120_000,
  );
};

export const downloadSparkJarTemplate = (id: string): Promise<Blob> => (
  requestBlob(`${TASK_PATH}/${id}/spark-jar-template`, {}, 60_000)
);

export const updateModelQualityTaskDefinition = (
  id: string,
  modelId: string,
  failureSampleLimit: number,
): Promise<ModelQualityTaskDefinition> => requestJson<ModelQualityTaskDefinition>(
  `${TASK_PATH}/${id}/actions/update-model-quality-definition`,
  { method: 'POST', body: JSON.stringify({ modelId, failureSampleLimit }) },
);

export const fetchTaskModelRelations = (id: string): Promise<TaskModelRelations> => (
  requestJson<TaskModelRelations>(`${TASK_PATH}/${id}/model-relations`)
);

export const fetchTaskTableLineage = (
  id: string,
  flowKey?: string,
): Promise<TaskLineageGraph> => {
  const query = new URLSearchParams();
  if (flowKey) query.set('flowKey', flowKey);
  const suffix = query.toString();
  return requestJson<TaskLineageGraph>(`${TASK_PATH}/${id}/lineage/table${suffix ? `?${suffix}` : ''}`);
};

export const fetchTaskFieldLineage = (
  id: string,
  flowKey?: string,
  outputFieldKey?: string,
): Promise<TaskLineageGraph> => {
  const query = new URLSearchParams();
  if (flowKey) query.set('flowKey', flowKey);
  if (outputFieldKey) query.set('outputFieldKey', outputFieldKey);
  const suffix = query.toString();
  return requestJson<TaskLineageGraph>(`${TASK_PATH}/${id}/lineage/fields${suffix ? `?${suffix}` : ''}`);
};

export const queryTaskFieldLineage = (
  id: string,
  flowKey: string | undefined,
  outputFieldKeys: string[] | null,
  signal?: AbortSignal,
): Promise<TaskFieldLineageGraph> => requestJson<TaskFieldLineageGraph>(
  `${TASK_PATH}/${id}/lineage/actions/query-fields`,
  { method: 'POST', body: JSON.stringify({ flowKey, outputFieldKeys }), signal },
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
  checkpointMode?: StreamingCheckpointMode,
): Promise<TaskStreamingStatus> => (
  requestJson<TaskStreamingStatus>(`${TASK_PATH}/${id}/actions/${command}`, {
    method: 'POST',
    body: command === 'start' && checkpointMode
      ? JSON.stringify({ checkpointMode })
      : undefined,
  }, 60_000)
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

export const forceTerminateTaskRun = (runId: string): Promise<TaskRun> => (
  requestJson<TaskRun>(`/v1/task-runs/${runId}/actions/force-terminate`, { method: 'POST' })
);

export type TaskRunArtifactKind = 'result' | 'log';

export const downloadTaskRunArtifact = (
  runId: string,
  kind: TaskRunArtifactKind,
): Promise<Blob> => requestBlob(`/v1/task-runs/${runId}/artifacts/${kind}`, {}, 60_000);

export const fetchTaskRunResultArtifact = async (
  runId: string,
): Promise<TaskExecutionResultArtifact> => {
  const blob = await downloadTaskRunArtifact(runId, 'result');
  const text = await blob.text();
  let value: unknown;
  try {
    value = JSON.parse(text);
  } catch {
    throw new Error('执行结果制品不是有效 JSON');
  }
  return parseTaskExecutionResultArtifact(value);
};

export interface QualityFailureSampleColumn {
  fieldId: string | null;
  code: string;
  name: string;
  type: PlatformTypeDefinition;
  primaryKey: boolean;
  diagnostic: boolean;
}

export interface QualityFailureSampleResponse {
  ruleId: string;
  ruleName: string;
  sampledRows: number;
  violationRows: number;
  truncated: boolean;
  rowLocatable: boolean;
  columns: QualityFailureSampleColumn[];
  rows: Array<Record<string, unknown>>;
}

export const fetchQualityFailureSamples = (
  runId: string,
  ruleId: string,
): Promise<QualityFailureSampleResponse> => requestJson<QualityFailureSampleResponse>(
  `/v1/task-runs/${runId}/quality-rules/${ruleId}/samples`, {}, 60_000,
);

export const downloadQualityFailureSamples = (
  runId: string,
  ruleId: string,
): Promise<Blob> => requestBlob(
  `/v1/task-runs/${runId}/quality-rules/${ruleId}/samples/download`, {}, 60_000,
);

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
