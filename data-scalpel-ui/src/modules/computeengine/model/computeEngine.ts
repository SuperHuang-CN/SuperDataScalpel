export type ComputeBackendType = 'LOCAL_DOCKER' | 'YARN' | 'KUBERNETES';

export type ComputeEngineRegistrationState =
  | 'CREATED'
  | 'REGISTERING'
  | 'ACTIVE'
  | 'DRAINING'
  | 'INACTIVE'
  | 'DETACHED'
  | 'ERROR';

export type ComputeEngineHealthState = 'UNKNOWN' | 'UP' | 'DOWN';

export interface SparkExecutionResourceSpec {
  driverCores: number;
  driverMemoryMiB: number;
  executorInstances: number;
  executorCores: number;
  executorMemoryMiB: number;
}

export interface SparkExecutionResourcePolicy {
  defaults: SparkExecutionResourceSpec;
  maximums: SparkExecutionResourceSpec;
}

export const defaultSparkExecutionResourcePolicy = (backend: ComputeBackendType): SparkExecutionResourcePolicy => (
  backend === 'LOCAL_DOCKER'
    ? {
      defaults: { driverCores: 2, driverMemoryMiB: 4096, executorInstances: 1, executorCores: 1, executorMemoryMiB: 1024 },
      maximums: { driverCores: 8, driverMemoryMiB: 16384, executorInstances: 1, executorCores: 1, executorMemoryMiB: 1024 },
    }
    : {
      defaults: { driverCores: 1, driverMemoryMiB: 2048, executorInstances: 2, executorCores: 2, executorMemoryMiB: 2048 },
      maximums: { driverCores: 8, driverMemoryMiB: 16384, executorInstances: 20, executorCores: 8, executorMemoryMiB: 16384 },
    }
);

export interface ComputeEngine {
  id: string;
  name: string;
  description: string | null;
  dispatcherBaseUrl: string;
  accessTokenConfigured: boolean;
  expectedBackendType: ComputeBackendType;
  reportedBackendType: ComputeBackendType | null;
  registrationState: ComputeEngineRegistrationState;
  healthState: ComputeEngineHealthState;
  commandTopic: string;
  runnerEventTopic: string;
  adminEventTopic: string;
  maxQueuedExecutions: number;
  maxConcurrentSubmissions: number;
  maxInFlightApplications: number;
  resourcePolicy: SparkExecutionResourcePolicy;
  dispatcherInstanceId: string | null;
  lastCheckAt: string | null;
  lastError: string | null;
  detachedAt: string | null;
  detachReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export type DispatcherExecutionScope = 'ACTIVE' | 'QUEUED' | 'RECENT';

export type DispatcherExecutionState =
  | 'QUEUED'
  | 'SUBMITTING'
  | 'SUBMITTED'
  | 'RUNNING'
  | 'CANCEL_REQUESTED'
  | 'SUCCESS'
  | 'FAILED'
  | 'TIMED_OUT'
  | 'CANCELLED'
  | 'STOPPED'
  | 'LOST';

export type DispatcherTaskType =
  | 'SPARK_CANVAS'
  | 'SPARK_STREAMING_CANVAS'
  | 'SPARK_MODEL_QUALITY'
  | 'SPARK_JAR'
  | 'SPARK_STREAMING_JAR';

export interface DispatcherRuntimeDependency {
  name: string;
  state: 'UP' | 'DOWN' | string;
  detail: string | null;
}

export interface DispatcherAdmissionCapacity {
  maxQueuedExecutions: number;
  maxConcurrentSubmissions: number;
  maxInFlightApplications: number;
  resourcePolicy: SparkExecutionResourcePolicy;
}

export interface DispatcherAdmissionUsage {
  queued: number;
  submitting: number;
  submitted: number;
  running: number;
  cancelRequested: number;
  inFlight: number;
}

export interface DispatcherResourceConfiguration {
  backendType: ComputeBackendType;
  image: string | null;
  containerCpuLimit: string | null;
  containerMemoryLimit: string | null;
  runnerJvmHeap: string | null;
  queue: string | null;
  namespace: string | null;
  driverMemory: string | null;
  executorMemory: string | null;
  executorCores: number | null;
  executorInstances: number | null;
}

export interface ComputeEngineRuntimeOverview {
  engineId: string | null;
  dispatcherInstanceId: string;
  backendType: ComputeBackendType;
  version: string;
  dispatcherRegistrationState: string;
  dependencies: DispatcherRuntimeDependency[];
  admissionCapacity: DispatcherAdmissionCapacity | null;
  admissionUsage: DispatcherAdmissionUsage;
  resourceConfiguration: DispatcherResourceConfiguration;
  collectedAt: string;
}

export interface ComputeEngineExecution {
  executionId: string;
  executionRunId: string;
  taskId: string;
  taskName: string | null;
  taskType: DispatcherTaskType;
  definitionVersion: number;
  dispatcherState: DispatcherExecutionState;
  taskRunId: string | null;
  taskRunStatus: string | null;
  triggerType: 'MANUAL' | 'SCHEDULED' | null;
  synchronized: boolean;
  backendExecutionId: string | null;
  trackingUrl: string | null;
  deadlineAt: string | null;
  queuedAt: string;
  submissionStartedAt: string | null;
  submittedAt: string | null;
  startedAt: string | null;
  endedAt: string | null;
  lastObservedAt: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  queuePosition: number | null;
}

export interface DispatcherCapabilities {
  cancellation: boolean;
  logCollection: boolean;
  restartReconciliation: boolean;
}

export interface DispatcherDependency {
  name: string;
  state: string;
  detail: string | null;
}

export interface ComputeEngineTestResult {
  dispatcherInstanceId: string;
  backendType: ComputeBackendType;
  dispatcherVersion: string;
  capabilities: DispatcherCapabilities;
  dependencies: DispatcherDependency[];
}

export interface ComputeEngineWriteRequest {
  name: string;
  description?: string;
  dispatcherBaseUrl: string;
  accessToken?: string;
  expectedBackendType: ComputeBackendType;
  commandTopic: string;
  runnerEventTopic: string;
  adminEventTopic: string;
  maxQueuedExecutions: number;
  maxConcurrentSubmissions: number;
  maxInFlightApplications: number;
  resourcePolicy: SparkExecutionResourcePolicy;
}

export type CreateComputeEngineRequest = ComputeEngineWriteRequest & { accessToken: string };
export type UpdateComputeEngineRequest = ComputeEngineWriteRequest;

export interface DetachComputeEngineRequest {
  confirmationName: string;
  reason: string;
}

export interface ComputeEngineFilters {
  keyword?: string;
  expectedBackendType?: ComputeBackendType;
  registrationState?: ComputeEngineRegistrationState;
  healthState?: ComputeEngineHealthState;
}

export const computeBackendTypeLabels: Record<ComputeBackendType, string> = {
  LOCAL_DOCKER: 'Local Docker',
  YARN: 'YARN Cluster',
  KUBERNETES: 'Kubernetes Cluster',
};

export const computeEngineRegistrationStateLabels: Record<ComputeEngineRegistrationState, string> = {
  CREATED: '待注册',
  REGISTERING: '注册中',
  ACTIVE: '已激活',
  DRAINING: '排空中',
  INACTIVE: '已停用',
  DETACHED: '离线解绑',
  ERROR: '异常',
};

export const computeEngineRegistrationStateColors: Record<ComputeEngineRegistrationState, string> = {
  CREATED: 'default',
  REGISTERING: 'processing',
  ACTIVE: 'success',
  DRAINING: 'warning',
  INACTIVE: 'default',
  DETACHED: 'warning',
  ERROR: 'error',
};

export const computeEngineHealthStateLabels: Record<ComputeEngineHealthState, string> = {
  UNKNOWN: '未检查',
  UP: '正常',
  DOWN: '不可用',
};

export const computeEngineHealthStateColors: Record<ComputeEngineHealthState, string> = {
  UNKNOWN: 'default',
  UP: 'success',
  DOWN: 'error',
};

export const dispatcherExecutionStateLabels: Record<DispatcherExecutionState, string> = {
  QUEUED: '排队中',
  SUBMITTING: '提交中',
  SUBMITTED: '已提交',
  RUNNING: '运行中',
  CANCEL_REQUESTED: '取消请求中',
  SUCCESS: '成功',
  FAILED: '失败',
  TIMED_OUT: '超时',
  CANCELLED: '已取消',
  STOPPED: '已停止',
  LOST: '已丢失',
};

export const dispatcherExecutionStateColors: Record<DispatcherExecutionState, string> = {
  QUEUED: 'default',
  SUBMITTING: 'processing',
  SUBMITTED: 'processing',
  RUNNING: 'success',
  CANCEL_REQUESTED: 'warning',
  SUCCESS: 'success',
  FAILED: 'error',
  TIMED_OUT: 'error',
  CANCELLED: 'default',
  STOPPED: 'default',
  LOST: 'error',
};

export const dispatcherTaskTypeLabels: Record<DispatcherTaskType, string> = {
  SPARK_CANVAS: 'Canvas 批处理',
  SPARK_STREAMING_CANVAS: 'Canvas 实时任务',
  SPARK_MODEL_QUALITY: '模型质检',
  SPARK_JAR: 'Spark JAR 批处理',
  SPARK_STREAMING_JAR: 'Spark JAR 实时任务',
};

export const isComputeEngineSelectable = (engine: ComputeEngine) => (
  engine.registrationState === 'ACTIVE' && engine.healthState !== 'DOWN'
);
