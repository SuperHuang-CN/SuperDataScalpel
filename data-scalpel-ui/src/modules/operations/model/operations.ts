import type { TaskRunStatus, TaskType } from '../../task';
import type { ComputeBackendType, ComputeEngineRuntimeOverview } from '../../computeengine';

export type AlertRuleType = 'RUN_FAILED' | 'QUALITY_FAILED' | 'QUEUE_TOO_LONG' | 'RUN_TOO_LONG' | 'ENGINE_UNREACHABLE' | 'ENGINE_NOT_READY';
export type AlertSeverity = 'WARNING' | 'CRITICAL';
export type AlertStatus = 'OPEN' | 'ACKNOWLEDGED' | 'CLOSED';
export type AlertEventType = 'TRIGGERED' | 'RECOVERED' | 'TEST';
export type DeliveryStatus = 'PENDING' | 'SENDING' | 'SENT' | 'FAILED' | 'SUPPRESSED';
export type RunMode = 'REAL' | 'SIMULATED' | 'TRIAL';
export interface RuntimeOverview {
  from: string; to: string; collectedAt: string;
  tasks: { current: Partial<Record<TaskRunStatus, number>>; completed: Partial<Record<TaskRunStatus, number>>;
    qualityFailed: number; successRate: number | null; trend: { from: string; to: string; status: TaskRunStatus; count: number }[] } | null;
  engines: { total: number; active: number; unreachable: number; notReady: number; unknown: number } | null;
  openAlerts: number; pendingSignals: number | null; failedDeliveries: number | null;
}
export interface RuntimeRun {
  id: string; taskId: string; taskName: string; directoryId: string | null; sourceExists: boolean;
  parentRunId?: string | null; workflowNodeId?: string | null;
  taskType: TaskType; computeEngineId: string | null; engineName: string | null; streamingDeploymentId: string | null;
  status: TaskRunStatus; executionMode: RunMode; triggerType: 'MANUAL' | 'SCHEDULED' | 'WORKFLOW' | 'WORKFLOW';
  queuedAt: string; startedAt: string | null; endedAt: string | null; qualityConclusion: 'PASSED' | 'FAILED' | null;
  qualityFailedRules: number | null; errorCode: string | null; diagnosticId: string | null;
}
export interface RuntimeRunFilters {
  taskName?: string; taskType?: TaskType; status?: TaskRunStatus | 'FAILED_OR_TIMED_OUT'; computeEngineId?: string; directoryId?: string;
  batchOnly?: boolean; qualityConclusion?: 'PASSED' | 'FAILED';
  triggerType?: 'MANUAL' | 'SCHEDULED' | 'WORKFLOW' | 'WORKFLOW'; activeOnly?: boolean; mode?: RunMode; from?: string; to?: string;
  timeField?: 'queuedAt' | 'endedAt';
}
export interface RuntimeStreamingQuery {
  id: string; name: string; state: string; batchId: number | null; inputRows: number | null;
  inputRowsPerSecond: number | null; processedRowsPerSecond: number | null; batchDurationMillis: number | null; lastProgressAt: string | null;
}
export interface RuntimeStreaming {
  id: string; taskId: string; taskName: string; taskType: TaskType | null; sourceExists: boolean;
  currentRunId: string | null; computeEngineId: string | null; engineName: string | null;
  actualState: 'STARTING' | 'RUNNING' | 'STOPPING' | 'STOPPED' | 'FAILED'; desiredState: string;
  startedAt: string | null; stoppedAt: string | null; lastProgressAt: string | null; progressStale: boolean;
  sourceKind: string | null; cursorLagMillis: number | null; pollTime: string | null; queries: RuntimeStreamingQuery[]; lastErrorAt: string | null; errorSummary: string | null;
}
export interface RuntimeEngine {
  id: string; name: string; backendType: ComputeBackendType; registrationState: string;
  observationState: 'UNKNOWN' | 'REACHABLE' | 'UNREACHABLE'; dependenciesReady: boolean | null; stale: boolean;
  attemptedAt: string | null; observedAt: string | null; lastHealthyAt: string | null; summary: string;
  snapshot: ComputeEngineRuntimeOverview | null;
}
export interface AlertIncident {
  id: string; ruleType: AlertRuleType; severity: AlertSeverity; subjectId: string; subjectName: string;
  runId: string | null; engineId: string | null; sourceExists: boolean; status: AlertStatus;
  conditionState: 'TRIGGERED' | 'CLEARED' | 'UNKNOWN'; summary: string; errorCode: string | null; diagnosticId: string | null;
  occurredAt: string; detectedAt: string; lastObservedAt: string | null; closedAt: string | null;
  closeReason: string | null; silencedUntil: string | null;
  notifications: { inApp: number; pending: number; sent: number; failed: number; suppressed: number };
}
export interface AlertAction { id: string; action: string; actorName: string; reason: string | null; untilAt: string | null; createdAt: string }
export interface AlertDelivery {
  id: string; incidentId: string | null; channelId: string; channelName: string; eventType: AlertEventType;
  status: DeliveryStatus; attempts: number; httpStatus: number | null; durationMillis: number | null; lastError: string | null;
  nextAttemptAt: string | null; sentAt: string | null; createdAt: string;
}
export interface AlertRuleWrite {
  ruleType: AlertRuleType; subjectId: string | null; enabled: boolean; severity: AlertSeverity;
  thresholdSeconds: number; cooldownSeconds: number; userIds: string[]; channelIds: string[];
}
export interface AlertRule extends AlertRuleWrite { id: string; subjectName: string; configurationVersion: number }
export interface AlertChannel {
  id: string; name: string; url: string; enabled: boolean; configurationVersion: number;
  bearerTokenConfigured: boolean; hmacSecretConfigured: boolean;
}
export interface AlertChannelWrite {
  name: string; url: string; enabled: boolean; bearerToken?: string; hmacSecret?: string; clearBearerToken?: boolean; clearHmacSecret?: boolean;
}
export interface AlertRecipient { id: string; username: string; displayName: string }
export interface InAppNotification { id: string; incidentId: string; ruleType: AlertRuleType; eventType: AlertEventType; summary: string; readAt: string | null; createdAt: string }
export const ruleLabels: Record<AlertRuleType, string> = {
  RUN_FAILED: '运行失败', QUALITY_FAILED: '质检不通过', QUEUE_TOO_LONG: '排队过久', RUN_TOO_LONG: '执行过久',
  ENGINE_UNREACHABLE: '引擎不可达', ENGINE_NOT_READY: '引擎依赖未就绪',
};
export const statusLabels: Record<AlertStatus, string> = { OPEN: '待确认', ACKNOWLEDGED: '已确认', CLOSED: '已关闭' };
export const severityLabels: Record<AlertSeverity, string> = { WARNING: '警告', CRITICAL: '严重' };
export const deliveryLabels: Record<DeliveryStatus, string> = { PENDING: '待投递', SENDING: '投递中', SENT: '已送达', FAILED: '投递失败', SUPPRESSED: '已抑制' };
export const eventLabels: Record<AlertEventType, string> = { TRIGGERED: '异常触发', RECOVERED: '条件恢复', TEST: '渠道测试' };
export const isEngineRule = (type: AlertRuleType) => type === 'ENGINE_UNREACHABLE' || type === 'ENGINE_NOT_READY';
export const isContinuousRule = (type: AlertRuleType) => type !== 'RUN_FAILED' && type !== 'QUALITY_FAILED';
