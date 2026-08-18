import type {
  ModelQualityRuleSeverity,
  ModelQualityRuleType,
  ViolationMetric,
} from './modelQualityRule';

export type ModelQualityResultDetailStatus = 'AVAILABLE' | 'UNAVAILABLE' | 'INVALID' | 'NOT_AVAILABLE';
export type ModelQualityOverviewRuleState = 'PASSED' | 'FAILED' | 'SKIPPED';
export type ModelQualityOverviewSampleStatus = 'NOT_FAILED' | 'NOT_APPLICABLE' | 'DISABLED' | 'AVAILABLE';

export interface ModelQualityOverviewExecutionError {
  code: string;
  message: string;
  category: string;
  retryable: boolean;
  phase: string;
  diagnosticId: string;
}

export interface ModelQualityOverviewRecentRun {
  runId: string;
  taskId: string;
  taskName: string;
  triggerType: 'MANUAL' | 'SCHEDULED';
  status: 'QUEUED' | 'RUNNING' | 'CANCEL_REQUESTED' | 'STOP_REQUESTED' | 'STOPPED'
    | 'SUCCESS' | 'FAILED' | 'TIMED_OUT' | 'CANCELLED' | 'SKIPPED';
  queuedAt: string;
  startedAt: string | null;
  endedAt: string | null;
  message: string | null;
  executionError: ModelQualityOverviewExecutionError | null;
}

export interface ModelQualityOverviewEffectiveResult {
  runId: string;
  taskId: string;
  taskName: string;
  conclusion: 'PASSED' | 'FAILED';
  totalRules: number;
  passedRules: number;
  failedRules: number;
  skippedRules: number;
  checkedRows: number;
  ruleSnapshotAt: string;
  queuedAt: string;
  endedAt: string;
}

export interface ModelQualityOverviewViolationMetric {
  kind: 'VIOLATION';
  violationCount: number;
  violationPercent: number;
  toleranceMetric: ViolationMetric;
  toleranceValue: number;
  actualRows: null;
  minimumRows: null;
  maximumValue: null;
  actualDelayMinutes: null;
  maximumDelayMinutes: null;
}

export interface ModelQualityOverviewRowCountMetric {
  kind: 'ROW_COUNT';
  violationCount: null;
  violationPercent: null;
  toleranceMetric: null;
  toleranceValue: null;
  actualRows: number;
  minimumRows: number;
  maximumValue: null;
  actualDelayMinutes: null;
  maximumDelayMinutes: null;
}

export interface ModelQualityOverviewFreshnessMetric {
  kind: 'FRESHNESS';
  violationCount: null;
  violationPercent: null;
  toleranceMetric: null;
  toleranceValue: null;
  actualRows: null;
  minimumRows: null;
  maximumValue: string | null;
  actualDelayMinutes: number | null;
  maximumDelayMinutes: number;
}

export type ModelQualityOverviewMetric = ModelQualityOverviewViolationMetric
  | ModelQualityOverviewRowCountMetric
  | ModelQualityOverviewFreshnessMetric;

export interface ModelQualityOverviewSample {
  status: ModelQualityOverviewSampleStatus;
  sampledRows: number | null;
  violationRows: number | null;
  truncated: boolean | null;
  rowLocatable: boolean | null;
}

export interface ModelQualityOverviewRuleResult {
  ruleId: string;
  ruleName: string;
  ruleType: ModelQualityRuleType;
  severity: ModelQualityRuleSeverity | null;
  state: ModelQualityOverviewRuleState;
  durationMs: number | null;
  metric: ModelQualityOverviewMetric | null;
  skipCode: string | null;
  skipReason: string | null;
  sample: ModelQualityOverviewSample | null;
}

export interface ModelQualityOverview {
  modelId: string;
  latestRun: ModelQualityOverviewRecentRun | null;
  latestEffectiveResult: ModelQualityOverviewEffectiveResult | null;
  resultDetailStatus: ModelQualityResultDetailStatus;
  resultDetailMessage: string | null;
  ruleResults: ModelQualityOverviewRuleResult[];
}
