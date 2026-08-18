import type { CanvasDefinition } from '../canvas/canvasTypes';
import type { DataModelStatus, PhysicalTableMode } from '../../model';

export type TaskType =
  | 'LOCAL_SQL'
  | 'SPARK_CANVAS'
  | 'SPARK_STREAMING_CANVAS'
  | 'SPARK_MODEL_QUALITY'
  | 'SPARK_JAR'
  | 'SPARK_STREAMING_JAR';

export type TaskStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED';

export type LocalSqlWriteMode = 'APPEND' | 'OVERWRITE';

export type TaskRunStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'CANCEL_REQUESTED'
  | 'STOP_REQUESTED'
  | 'STOPPED'
  | 'SUCCESS'
  | 'FAILED'
  | 'TIMED_OUT'
  | 'CANCELLED'
  | 'SKIPPED';

export type TaskRunTriggerType = 'MANUAL' | 'SCHEDULED';

export type TaskRunExecutionMode = 'REAL' | 'SIMULATED';

export type ExecutionErrorCategory =
  | 'CONFIGURATION'
  | 'CONNECTION'
  | 'AUTHENTICATION'
  | 'PERMISSION'
  | 'SCHEMA'
  | 'CONSTRAINT'
  | 'TIMEOUT'
  | 'CANCELLED'
  | 'RESOURCE'
  | 'EXTERNAL_SYSTEM'
  | 'INTERNAL';

export type ExecutionFailurePhase =
  | 'PREPARE'
  | 'READ'
  | 'PROCESS'
  | 'WRITE'
  | 'DELIVERY'
  | 'DISPATCH';

export interface TaskRunExecutionError {
  code: string;
  message: string;
  category: ExecutionErrorCategory;
  retryable: boolean;
  nodeId: string | null;
  nodeType: string | null;
  nodeName: string | null;
  phase: ExecutionFailurePhase;
  sqlState: string | null;
  diagnosticId: string;
}

export type TaskScheduleStatus = 'ENABLED' | 'DISABLED';

export type TaskMisfirePolicy = 'FIRE_ONCE_NOW' | 'SKIP';

export type TaskOverlapPolicy = 'FORBID' | 'ALLOW';

export interface DataTask {
  id: string;
  name: string;
  directoryId: string | null;
  type: TaskType;
  status: TaskStatus;
  description: string | null;
  computeEngineId: string | null;
  computeEngineName: string | null;
  definitionConfigured: boolean;
  definitionVersion: number | null;
  outputModelId: string | null;
  outputModelName: string | null;
  qualityTargetModelId?: string | null;
  qualityTargetModelName?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TaskModelReference {
  modelId: string;
  modelCode: string;
  modelName: string;
  schemaVersion: number;
}

export type ModelTaskRelationRole = 'INPUT' | 'OUTPUT';

export type ModelTaskReferenceType =
  | 'LOCAL_SQL_INPUT'
  | 'LOCAL_SQL_OUTPUT'
  | 'CANVAS_NODE'
  | 'MODEL_QUALITY_TARGET'
  | 'SPARK_JAR_RESOURCE_BINDING'
  | 'CURRENT_LINEAGE';

export interface TaskModelReferenceLocation {
  role: ModelTaskRelationRole;
  referenceType: ModelTaskReferenceType;
  ordinal: number | null;
  nodeId: string | null;
  nodeName: string | null;
}

export interface ModelRelatedTask {
  taskId: string;
  taskName: string;
  taskType: TaskType;
  taskStatus: TaskStatus;
  definitionVersion: number;
  roles: ModelTaskRelationRole[];
  locations: TaskModelReferenceLocation[];
  updatedAt: string;
}

export interface TaskRelatedModel {
  modelId: string;
  modelCode: string;
  modelName: string;
  modelStatus: DataModelStatus;
  physicalTableMode: PhysicalTableMode;
  schemaVersion: number;
  roles: ModelTaskRelationRole[];
  locations: TaskModelReferenceLocation[];
}

export interface TaskModelRelations {
  taskId: string;
  configured: boolean;
  definitionVersion: number | null;
  models: TaskRelatedModel[];
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

export interface CanvasTaskDefinition {
  taskId: string;
  configured: boolean;
  version: number;
  loadStatus: 'UNCONFIGURED' | 'LOADED' | 'INCOMPATIBLE';
  schemaVersion: number;
  schemaMinorVersion: number;
  definition: CanvasDefinition | null;
  message: string | null;
  updatedAt: string | null;
}

export interface ModelQualityTaskDefinitionSkippedRule {
  ruleId: string;
  ruleName: string;
  reason: string;
}

export interface ModelQualityTaskDefinition {
  taskId: string;
  configured: boolean;
  version: number | null;
  targetModel: TaskModelReference | null;
  failureSampleLimit: number;
  executableRuleCount: number;
  skippedRuleCount: number;
  skippedRules: ModelQualityTaskDefinitionSkippedRule[];
  updatedAt: string | null;
}

export type SparkJarJobMode = 'BATCH' | 'STREAMING';

export type SparkJarResourceType = 'MODEL' | 'JDBC_DATA_SOURCE' | 'KAFKA_TOPIC';

export type SparkJarResourceAccessMode = 'READ' | 'WRITE' | 'READ_WRITE';

export interface SparkJarDefinitionEntry {
  name: string;
  value: string;
}

export interface SparkJarResourceBinding {
  bindingName: string;
  resourceType: SparkJarResourceType;
  resourceId: string;
  resourceName: string | null;
  topicName: string | null;
  accessMode: SparkJarResourceAccessMode;
}

export interface SparkJarArtifact {
  fileName: string;
  sha256: string;
  sizeBytes: number;
  jobClass: string;
  jobApiVersion: number;
  jobMode: SparkJarJobMode;
}

export interface SparkJarTaskDefinition {
  taskId: string;
  configured: boolean;
  definitionVersion: number;
  jobMode: SparkJarJobMode;
  jar: SparkJarArtifact | null;
  parameters: SparkJarDefinitionEntry[];
  sparkConf: SparkJarDefinitionEntry[];
  resourceBindings: SparkJarResourceBinding[];
  timeoutSeconds: number;
  updatedAt: string | null;
}

export interface UpdateSparkJarTaskDefinitionRequest {
  parameters: SparkJarDefinitionEntry[];
  sparkConf: SparkJarDefinitionEntry[];
  resourceBindings: Array<Omit<SparkJarResourceBinding, 'resourceName'>>;
  timeoutSeconds: number;
}

export interface UpdateLocalSqlTaskDefinitionRequest {
  sql: string;
  inputModelIds: string[];
  outputModelId: string;
  writeMode: LocalSqlWriteMode;
  timeoutSeconds: number;
}

export interface CreateDataTaskRequest {
  name: string;
  type: TaskType;
  directoryId?: string;
  description?: string;
  computeEngineId?: string;
}

export interface UpdateDataTaskRequest {
  name: string;
  directoryId?: string;
  description?: string;
  computeEngineId?: string;
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
  lineageCoverage: 'MODEL_ONLY' | 'FIELD_PARTIAL' | 'FIELD_COMPLETE';
  lineageAnalysisStatus: 'COMPLETE' | 'PARTIAL' | 'UNAVAILABLE';
  lineageWarnings: LocalSqlLineageWarning[];
}

export interface LocalSqlLineageWarning {
  code: string;
  message: string;
  outputOrdinal: number | null;
}

export interface TaskRun {
  id: string;
  taskId: string;
  scheduleId: string | null;
  streamingDeploymentId: string | null;
  taskType: TaskType;
  externalExecutionId: string | null;
  computeEngineId: string | null;
  backendApplicationId: string | null;
  trackingUrl: string | null;
  attempt: number | null;
  definitionVersion: number;
  triggerType: TaskRunTriggerType;
  executionMode: TaskRunExecutionMode;
  status: TaskRunStatus;
  scheduledFireAt: string | null;
  queuedAt: string;
  startedAt: string | null;
  endedAt: string | null;
  deadlineAt: string | null;
  affectedRows: number | null;
  userJarFileName: string | null;
  userJarSha256: string | null;
  userJarSizeBytes: number | null;
  qualityConclusion?: 'PASSED' | 'FAILED' | null;
  qualityTotalRules?: number | null;
  qualityPassedRules?: number | null;
  qualityFailedRules?: number | null;
  qualitySkippedRules?: number | null;
  qualityCheckedRows?: number | null;
  userJobObservability?: UserJobObservability | null;
  message: string | null;
  errorDetail: string | null;
  executionError: TaskRunExecutionError | null;
  createdAt: string;
  updatedAt: string;
}

export type UserJobMetricKind = 'COUNTER' | 'GAUGE' | 'TIMER';

export interface UserJobStatus {
  phase: string;
  message: string;
  updatedAt: string;
}

export interface UserJobMetricSnapshot {
  name: string;
  kind: UserJobMetricKind;
  counterValue: number | null;
  gaugeValue: number | null;
  count: number | null;
  lastDurationMillis: number | null;
  totalDurationMillis: number | null;
  maxDurationMillis: number | null;
}

export interface UserJobObservability {
  status: UserJobStatus | null;
  metrics: UserJobMetricSnapshot[];
}

export type StreamingDeploymentDesiredState = 'RUNNING' | 'STOPPED';

export type StreamingDeploymentActualState =
  | 'STARTING'
  | 'RUNNING'
  | 'STOPPING'
  | 'STOPPED'
  | 'FAILED';

export type StreamingQueryState =
  | 'STARTING'
  | 'RUNNING'
  | 'STOPPING'
  | 'STOPPED'
  | 'FAILED';

export type StreamingSinkType = 'KAFKA' | 'JDBC' | 'CUSTOM';

export type StreamingCheckpointMode = 'CONTINUE' | 'FRESH';

export interface TaskStreamingConfiguration {
  taskId: string;
  triggerIntervalSeconds: number;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateTaskStreamingConfigurationRequest {
  triggerIntervalSeconds: number;
}

export interface TaskStreamingQuery {
  id: string;
  outputNodeId: string;
  outputNodeName: string;
  sinkType: StreamingSinkType;
  checkpointKey: string;
  state: StreamingQueryState;
  latestBatchId: number | null;
  latestInputRows: number | null;
  inputRowsPerSecond: number | null;
  processedRowsPerSecond: number | null;
  batchDurationMillis: number | null;
  lastProgressAt: string | null;
  lastErrorAt: string | null;
  lastError: string | null;
}

export interface TaskStreamingDeployment {
  id: string;
  definitionVersion: number;
  computeEngineId: string;
  currentRunId: string | null;
  checkpointKeyPrefix: string;
  checkpointGeneration: number;
  checkpointStartMode: StreamingCheckpointMode;
  checkpointSourceDeploymentId: string | null;
  desiredState: StreamingDeploymentDesiredState;
  actualState: StreamingDeploymentActualState;
  applicationId: string | null;
  trackingUrl: string | null;
  attempt: number | null;
  startedAt: string | null;
  stopRequestedAt: string | null;
  stoppedAt: string | null;
  lastProgressAt: string | null;
  lastErrorAt: string | null;
  lastError: string | null;
  sourceNodeId: string | null;
  sourceSignature: string | null;
  committedOffset: string | null;
  windowStart: string | null;
  windowEnd: string | null;
  rowCount: number | null;
  pollDurationMillis: number | null;
  pollTime: string | null;
  cursorLagMillis: number | null;
  userJobObservability?: UserJobObservability | null;
  queries: TaskStreamingQuery[];
}

export interface TaskStreamingStatus {
  taskId: string;
  deployment: TaskStreamingDeployment | null;
}

export interface TaskSchedule {
  id: string;
  taskId: string;
  name: string;
  cronExpression: string;
  zoneId: string;
  status: TaskScheduleStatus;
  misfirePolicy: TaskMisfirePolicy;
  overlapPolicy: TaskOverlapPolicy;
  nextFireAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TaskScheduleRequest {
  name: string;
  cronExpression: string;
  zoneId: string;
  misfirePolicy: TaskMisfirePolicy;
  overlapPolicy: TaskOverlapPolicy;
}

export interface TaskFilters {
  keyword?: string;
  status?: TaskStatus;
  type?: TaskType;
  directoryIds?: string[];
  uncategorized?: boolean;
}

export const taskTypeLabels: Record<TaskType, string> = {
  LOCAL_SQL: '本地 SQL',
  SPARK_CANVAS: 'Spark 编排',
  SPARK_STREAMING_CANVAS: 'Spark 实时编排',
  SPARK_MODEL_QUALITY: 'Spark 模型质检',
  SPARK_JAR: 'Spark JAR',
  SPARK_STREAMING_JAR: 'Spark 实时 JAR',
};

export const taskTypeColors: Record<TaskType, string> = {
  LOCAL_SQL: 'blue',
  SPARK_CANVAS: 'purple',
  SPARK_STREAMING_CANVAS: 'magenta',
  SPARK_MODEL_QUALITY: 'cyan',
  SPARK_JAR: 'geekblue',
  SPARK_STREAMING_JAR: 'orange',
};

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
  CANCEL_REQUESTED: '取消中',
  STOP_REQUESTED: '停止中',
  STOPPED: '已停止',
  SUCCESS: '成功',
  FAILED: '失败',
  TIMED_OUT: '超时',
  CANCELLED: '已取消',
  SKIPPED: '已跳过',
};

export const taskRunStatusColors: Record<TaskRunStatus, string> = {
  QUEUED: 'processing',
  RUNNING: 'processing',
  CANCEL_REQUESTED: 'warning',
  STOP_REQUESTED: 'warning',
  STOPPED: 'default',
  SUCCESS: 'success',
  FAILED: 'error',
  TIMED_OUT: 'warning',
  CANCELLED: 'default',
  SKIPPED: 'default',
};

export const taskRunTriggerTypeLabels: Record<TaskRunTriggerType, string> = {
  MANUAL: '手动',
  SCHEDULED: '定时',
};

export const taskRunExecutionModeLabels: Record<TaskRunExecutionMode, string> = {
  REAL: '真实执行',
  SIMULATED: '模拟执行',
};

export const executionErrorCategoryLabels: Record<ExecutionErrorCategory, string> = {
  CONFIGURATION: '配置',
  CONNECTION: '连接',
  AUTHENTICATION: '认证',
  PERMISSION: '权限',
  SCHEMA: '结构',
  CONSTRAINT: '约束',
  TIMEOUT: '超时',
  CANCELLED: '取消',
  RESOURCE: '资源',
  EXTERNAL_SYSTEM: '外部系统',
  INTERNAL: '内部错误',
};

export const executionFailurePhaseLabels: Record<ExecutionFailurePhase, string> = {
  PREPARE: '准备',
  READ: '读取',
  PROCESS: '处理',
  WRITE: '写入',
  DELIVERY: '结果投递',
  DISPATCH: '调度',
};

export const taskScheduleStatusLabels: Record<TaskScheduleStatus, string> = {
  ENABLED: '已启用',
  DISABLED: '已停用',
};

export const taskMisfirePolicyLabels: Record<TaskMisfirePolicy, string> = {
  FIRE_ONCE_NOW: '立即补触发一次',
  SKIP: '跳过错过批次',
};

export const taskOverlapPolicyLabels: Record<TaskOverlapPolicy, string> = {
  FORBID: '禁止重叠',
  ALLOW: '允许重叠',
};

export const streamingDeploymentStateLabels: Record<StreamingDeploymentActualState, string> = {
  STARTING: '启动中',
  RUNNING: '运行中',
  STOPPING: '停止中',
  STOPPED: '已停止',
  FAILED: '失败',
};

export const streamingDeploymentStateColors: Record<StreamingDeploymentActualState, string> = {
  STARTING: 'processing',
  RUNNING: 'success',
  STOPPING: 'warning',
  STOPPED: 'default',
  FAILED: 'error',
};

export const streamingQueryStateLabels: Record<StreamingQueryState, string> = {
  STARTING: '启动中',
  RUNNING: '运行中',
  STOPPING: '停止中',
  STOPPED: '已停止',
  FAILED: '失败',
};

export const streamingQueryStateColors: Record<StreamingQueryState, string> = {
  STARTING: 'processing',
  RUNNING: 'success',
  STOPPING: 'warning',
  STOPPED: 'default',
  FAILED: 'error',
};

export const streamingSinkTypeLabels: Record<StreamingSinkType, string> = {
  KAFKA: 'Kafka',
  JDBC: 'JDBC',
  CUSTOM: '自定义',
};
