export type TaskType = 'LOCAL_SQL';

export type TaskStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED';

export type LocalSqlWriteMode = 'APPEND' | 'OVERWRITE';

export type TaskRunStatus = 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'TIMED_OUT';

export interface DataTask {
  id: string;
  code: string;
  name: string;
  directoryId: string | null;
  type: TaskType;
  status: TaskStatus;
  description: string | null;
  definitionConfigured: boolean;
  definitionVersion: number | null;
  outputModelId: string | null;
  outputModelName: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TaskModelReference {
  modelId: string;
  modelCode: string;
  modelName: string;
  schemaVersion: number;
}

export interface TaskDataSourceReference {
  id: string;
  code: string;
  name: string;
  type: string;
}

export interface LocalSqlTaskDefinition {
  taskId: string;
  configured: boolean;
  version: number;
  sql: string | null;
  inputs: TaskModelReference[];
  output: TaskModelReference | null;
  resolvedDataSource: TaskDataSourceReference | null;
  writeMode: LocalSqlWriteMode;
  timeoutSeconds: number;
  updatedAt: string | null;
}

export interface UpdateLocalSqlTaskDefinitionRequest {
  sql: string;
  inputModelIds: string[];
  outputModelId: string;
  writeMode: LocalSqlWriteMode;
  timeoutSeconds: number;
}

export interface CreateDataTaskRequest {
  code: string;
  name: string;
  directoryId?: string;
  description?: string;
}

export interface UpdateDataTaskRequest {
  name: string;
  directoryId?: string;
  description?: string;
}

export interface LocalSqlDefinitionValidationProblem {
  code: string;
  message: string;
  columnCode: string | null;
}

export interface LocalSqlDefinitionValidationColumn {
  ordinal: number;
  label: string;
  logicalType: string;
  nativeType: string | null;
  nullable: boolean;
  matchedOutputFieldCode: string | null;
}

export interface LocalSqlDefinitionValidation {
  valid: boolean;
  problems: LocalSqlDefinitionValidationProblem[];
  columns: LocalSqlDefinitionValidationColumn[];
  targetColumns: string[];
}

export interface TaskRun {
  id: string;
  taskId: string;
  definitionVersion: number;
  triggerType: 'MANUAL';
  status: TaskRunStatus;
  queuedAt: string;
  startedAt: string | null;
  endedAt: string | null;
  affectedRows: number | null;
  message: string | null;
  errorDetail: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TaskFilters {
  keyword?: string;
  status?: TaskStatus;
  directoryIds?: string[];
  uncategorized?: boolean;
}

export const taskStatusLabels: Record<TaskStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  DISABLED: '已停用',
};

export const taskStatusColors: Record<TaskStatus, string> = {
  DRAFT: 'default',
  PUBLISHED: 'success',
  DISABLED: 'warning',
};

export const taskRunStatusLabels: Record<TaskRunStatus, string> = {
  QUEUED: '排队中',
  RUNNING: '运行中',
  SUCCESS: '成功',
  FAILED: '失败',
  TIMED_OUT: '超时',
};

export const taskRunStatusColors: Record<TaskRunStatus, string> = {
  QUEUED: 'processing',
  RUNNING: 'processing',
  SUCCESS: 'success',
  FAILED: 'error',
  TIMED_OUT: 'warning',
};
