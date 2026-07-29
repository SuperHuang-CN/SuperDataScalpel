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
  dispatcherInstanceId: string | null;
  lastCheckAt: string | null;
  lastError: string | null;
  detachedAt: string | null;
  detachReason: string | null;
  createdAt: string;
  updatedAt: string;
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

export const isComputeEngineSelectable = (engine: ComputeEngine) => (
  engine.registrationState === 'ACTIVE' && engine.healthState !== 'DOWN'
);
