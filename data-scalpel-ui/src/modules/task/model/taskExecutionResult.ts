import type {
  ModelQualityRuleSeverity,
  ModelQualityRuleType,
  PlatformTypeDefinition,
  ViolationMetric,
} from '../../model';
import type { UserJobMetricSnapshot, UserJobObservability } from './task';

export interface SnapshotSyncExecutionMetrics {
  kind: 'SNAPSHOT_SYNC';
  sourceRows: number;
  targetRows: number;
  insertedRows: number;
  updatedRows: number;
  deletedRows: number;
  unchangedRows: number;
  retainedTargetOnlyRows: number;
}

export type OutputWriteExecutionState = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'SKIPPED';

export interface OutputWriteExecutionResult {
  writeId: string;
  sourceTableName: string;
  targetDisplayName: string;
  state: OutputWriteExecutionState;
  affectedRows: number | null;
  errorCode: string | null;
}

export interface OutputWritesExecutionMetrics {
  kind: 'OUTPUT_WRITES';
  writes: OutputWriteExecutionResult[];
}

export type TaskExecutionNodeMetrics = SnapshotSyncExecutionMetrics | OutputWritesExecutionMetrics;

export interface TaskExecutionNodeResult {
  nodeId: string;
  nodeType: string;
  nodeName: string;
  state: 'SUCCESS' | 'FAILED';
  rowsWritten: number | null;
  metrics: TaskExecutionNodeMetrics | null;
}

export interface QualityViolationMetric {
  kind: 'VIOLATION';
  violationCount: number;
  violationPercent: number;
  toleranceMetric: ViolationMetric;
  toleranceValue: number;
}

export interface QualityRowCountMetric {
  kind: 'ROW_COUNT';
  actualRows: number;
  minimumRows: number;
}

export interface QualityFreshnessMetric {
  kind: 'FRESHNESS';
  maximumValue: string | null;
  actualDelayMinutes: number | null;
  maximumDelayMinutes: number;
}

export type QualityRuleMetric =
  | QualityViolationMetric
  | QualityRowCountMetric
  | QualityFreshnessMetric;

export type QualitySampleStatus = 'NOT_FAILED' | 'NOT_APPLICABLE' | 'DISABLED' | 'AVAILABLE';

export interface QualitySampleColumn {
  fieldId: string | null;
  code: string;
  name: string;
  type: PlatformTypeDefinition;
  primaryKey: boolean;
  diagnostic: boolean;
}

export interface QualitySampleResult {
  status: QualitySampleStatus;
  sampledRows: number | null;
  violationRows: number | null;
  truncated: boolean | null;
  sizeBytes: number | null;
  sha256: string | null;
  rowLocatable: boolean | null;
  columns: QualitySampleColumn[];
}

export interface QualityRuleExecutionResult {
  ruleId: string;
  ruleName: string;
  ruleType: ModelQualityRuleType;
  severity: ModelQualityRuleSeverity;
  state: 'PASSED' | 'FAILED';
  durationMs: number;
  metric: QualityRuleMetric;
  sample: QualitySampleResult | null;
}

export interface QualitySkippedRuleResult {
  id: string;
  name: string;
  type: ModelQualityRuleType;
  reasonCode: string;
  reason: string;
}

export interface ModelQualityExecutionResult {
  conclusion: 'PASSED' | 'FAILED' | null;
  totalRules: number | null;
  passedRules: number | null;
  failedRules: number | null;
  skippedRules: number | null;
  checkedRows: number | null;
  ruleResults: QualityRuleExecutionResult[];
  skippedRuleResults: QualitySkippedRuleResult[];
  technicalFailure: QualityRuleTechnicalFailure | null;
}

export interface QualityRuleTechnicalFailure {
  ruleId: string;
  ruleName: string;
  ruleType: ModelQualityRuleType;
  severity: ModelQualityRuleSeverity;
  durationMs: number;
  diagnosticId: string;
}

export interface TaskExecutionResultArtifact {
  schemaVersion: SupportedTaskExecutionSchemaVersion;
  taskType: 'SPARK_CANVAS' | 'SPARK_MODEL_QUALITY' | 'SPARK_JAR' | 'SPARK_STREAMING_JAR';
  nodeResults: TaskExecutionNodeResult[];
  qualityResult: ModelQualityExecutionResult | null;
  userJobObservability: UserJobObservability | null;
}

type SupportedTaskExecutionSchemaVersion = 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11;

const qualityRuleTypes = new Set<ModelQualityRuleType>([
  'NOT_NULL', 'UNIQUE', 'VALUE_RANGE', 'STRING_LENGTH', 'DICTIONARY_MEMBERSHIP',
  'ROW_COUNT', 'FRESHNESS', 'GEOMETRY_VALID', 'GEOMETRY_NON_EMPTY', 'FORMAT_PATTERN',
  'CONDITIONAL_NOT_NULL', 'FIELD_COMPARISON', 'REFERENCE_EXISTS',
]);
const qualitySeverities = new Set<ModelQualityRuleSeverity>(['CRITICAL', 'MAJOR', 'MINOR']);
const qualitySampleStatuses = new Set<QualitySampleStatus>([
  'NOT_FAILED', 'NOT_APPLICABLE', 'DISABLED', 'AVAILABLE',
]);
const platformMetricKinds = new Map<string, UserJobMetricSnapshot['kind']>([
  ['datascalpel.model.write.attempts', 'COUNTER'],
  ['datascalpel.model.write.successes', 'COUNTER'],
  ['datascalpel.model.write.rows', 'COUNTER'],
  ['datascalpel.model.write.duration', 'TIMER'],
  ['datascalpel.jdbc.write.attempts', 'COUNTER'],
  ['datascalpel.jdbc.write.successes', 'COUNTER'],
  ['datascalpel.jdbc.write.rows', 'COUNTER'],
  ['datascalpel.jdbc.write.duration', 'TIMER'],
  ['datascalpel.streaming.registered_queries', 'GAUGE'],
]);

const isRecord = (value: unknown): value is Record<string, unknown> => (
  typeof value === 'object' && value !== null && !Array.isArray(value)
);

const nonNegativeInteger = (value: unknown): value is number => (
  typeof value === 'number' && Number.isSafeInteger(value) && value >= 0
);

const nonNegativeNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value) && value >= 0
);

const parseUserJobObservability = (value: unknown): UserJobObservability | null => {
  if (value === null || value === undefined) return null;
  if (!isRecord(value) || typeof value.capturedAt !== 'string' || !Array.isArray(value.metrics)) {
    throw new Error('用户作业观测快照格式无效');
  }
  let status: UserJobObservability['status'] = null;
  if (value.status !== null && value.status !== undefined) {
    if (!isRecord(value.status) || typeof value.status.phase !== 'string'
      || !/^[A-Za-z][A-Za-z0-9._-]{0,99}$/.test(value.status.phase)
      || typeof value.status.message !== 'string' || value.status.message.length < 1
      || value.status.message.length > 1_000 || typeof value.status.updatedAt !== 'string') {
      throw new Error('用户作业状态格式无效');
    }
    status = {
      phase: value.status.phase,
      message: value.status.message,
      updatedAt: value.status.updatedAt,
    };
  }
  if (value.metrics.length > 100 + platformMetricKinds.size
    || status === null && value.metrics.length === 0) {
    throw new Error('用户作业指标数量无效');
  }
  let previous = '';
  let userMetricCount = 0;
  const metrics = value.metrics.map((item): UserJobMetricSnapshot => {
    if (!isRecord(item) || typeof item.name !== 'string'
      || !/^[A-Za-z][A-Za-z0-9._-]{0,99}$/.test(item.name)
      || item.name <= previous || item.kind !== 'COUNTER' && item.kind !== 'GAUGE' && item.kind !== 'TIMER') {
      throw new Error('用户作业指标格式无效');
    }
    previous = item.name;
    const metric: UserJobMetricSnapshot = {
      name: item.name,
      kind: item.kind,
      counterValue: item.counterValue as number | null,
      gaugeValue: item.gaugeValue as number | null,
      count: item.count as number | null,
      lastDurationMillis: item.lastDurationMillis as number | null,
      totalDurationMillis: item.totalDurationMillis as number | null,
      maxDurationMillis: item.maxDurationMillis as number | null,
    };
    if (metric.name.startsWith('datascalpel.')) {
      if (platformMetricKinds.get(metric.name) !== metric.kind) {
        throw new Error('平台保留指标名称或类型无效');
      }
    } else if (++userMetricCount > 100) {
      throw new Error('用户自定义指标数量无效');
    }
    if (metric.kind === 'COUNTER') {
      if (!nonNegativeInteger(metric.counterValue) || metric.gaugeValue !== null || metric.count !== null
        || metric.lastDurationMillis !== null || metric.totalDurationMillis !== null
        || metric.maxDurationMillis !== null) throw new Error('Counter 指标格式无效');
    } else if (metric.kind === 'GAUGE') {
      if (!nonNegativeNumber(Math.abs(metric.gaugeValue ?? Number.NaN)) || metric.counterValue !== null
        || metric.count !== null || metric.lastDurationMillis !== null || metric.totalDurationMillis !== null
        || metric.maxDurationMillis !== null) throw new Error('Gauge 指标格式无效');
    } else if (!nonNegativeInteger(metric.count) || metric.count < 1
      || !nonNegativeInteger(metric.lastDurationMillis) || !nonNegativeInteger(metric.totalDurationMillis)
      || !nonNegativeInteger(metric.maxDurationMillis) || metric.counterValue !== null
      || metric.gaugeValue !== null || metric.totalDurationMillis < metric.lastDurationMillis
      || metric.maxDurationMillis < metric.lastDurationMillis
      || metric.maxDurationMillis > metric.totalDurationMillis) {
      throw new Error('Timer 指标格式无效');
    }
    return metric;
  });
  return { status, metrics };
};

const parseSnapshotMetrics = (value: unknown): SnapshotSyncExecutionMetrics | null => {
  if (!isRecord(value) || value.kind !== 'SNAPSHOT_SYNC') return null;
  const fields = [
    'sourceRows',
    'targetRows',
    'insertedRows',
    'updatedRows',
    'deletedRows',
    'unchangedRows',
    'retainedTargetOnlyRows',
  ] as const;
  if (fields.some((field) => !nonNegativeInteger(value[field]))) {
    throw new Error('Snapshot Sync 执行指标格式无效');
  }
  const metrics: SnapshotSyncExecutionMetrics = {
    kind: 'SNAPSHOT_SYNC',
    sourceRows: value.sourceRows as number,
    targetRows: value.targetRows as number,
    insertedRows: value.insertedRows as number,
    updatedRows: value.updatedRows as number,
    deletedRows: value.deletedRows as number,
    unchangedRows: value.unchangedRows as number,
    retainedTargetOnlyRows: value.retainedTargetOnlyRows as number,
  };
  if (metrics.sourceRows !== metrics.insertedRows + metrics.updatedRows + metrics.unchangedRows
    || metrics.targetRows !== metrics.deletedRows + metrics.retainedTargetOnlyRows
      + metrics.updatedRows + metrics.unchangedRows) {
    throw new Error('Snapshot Sync 执行指标不满足行数恒等式');
  }
  return metrics;
};

const parseOutputWritesMetrics = (value: unknown): OutputWritesExecutionMetrics | null => {
  if (!isRecord(value) || value.kind !== 'OUTPUT_WRITES') return null;
  if (!Array.isArray(value.writes) || value.writes.length === 0) {
    throw new Error('输出逐写入指标不能为空');
  }
  const writeIds = new Set<string>();
  let failed = false;
  const writes = value.writes.map((write, index): OutputWriteExecutionResult => {
    if (!isRecord(write) || typeof write.writeId !== 'string'
      || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(write.writeId)
      || writeIds.has(write.writeId.toLowerCase()) || typeof write.sourceTableName !== 'string'
      || write.sourceTableName.trim().length < 1 || write.sourceTableName.length > 256
      || typeof write.targetDisplayName !== 'string' || write.targetDisplayName.trim().length < 1
      || write.targetDisplayName.length > 1_024
      || write.state !== 'PENDING' && write.state !== 'RUNNING' && write.state !== 'SUCCESS'
        && write.state !== 'FAILED' && write.state !== 'SKIPPED'
      || write.affectedRows !== null && !nonNegativeInteger(write.affectedRows)
      || write.errorCode !== null && (typeof write.errorCode !== 'string'
        || !/^[A-Z][A-Z0-9_]{0,99}$/.test(write.errorCode))) {
      throw new Error(`输出写入指标 ${index + 1} 格式无效`);
    }
    writeIds.add(write.writeId.toLowerCase());
    if (write.state === 'PENDING' || write.state === 'RUNNING') {
      throw new Error('终态结果不能包含未完成写入');
    }
    if (write.state === 'SUCCESS') {
      if (failed || write.errorCode !== null) throw new Error('输出逐写入顺序无效');
    } else if (write.state === 'FAILED') {
      if (failed || write.affectedRows !== null || write.errorCode === null) {
        throw new Error('失败写入指标无效');
      }
      failed = true;
    } else if (!failed || write.affectedRows !== null || write.errorCode !== null) {
      throw new Error('跳过写入指标无效');
    }
    return write as unknown as OutputWriteExecutionResult;
  });
  return { kind: 'OUTPUT_WRITES', writes };
};

const parseNodeResults = (
  value: unknown,
  schemaVersion: SupportedTaskExecutionSchemaVersion,
): TaskExecutionNodeResult[] => {
  if (!Array.isArray(value)) throw new Error('执行结果节点列表格式无效');
  const nodeIds = new Set<string>();
  return value.map((item, index): TaskExecutionNodeResult => {
    if (!isRecord(item)
      || typeof item.nodeId !== 'string'
      || item.nodeId.trim().length < 1
      || typeof item.nodeType !== 'string'
      || item.nodeType.trim().length < 1
      || typeof item.nodeName !== 'string'
      || item.nodeName.trim().length < 1
      || item.state !== 'SUCCESS' && item.state !== 'FAILED'
      || item.rowsWritten !== null && !nonNegativeInteger(item.rowsWritten)
      || nodeIds.has(item.nodeId.toLowerCase())) {
      throw new Error(`执行结果节点 ${index + 1} 格式无效`);
    }
    nodeIds.add(item.nodeId.toLowerCase());
    if (schemaVersion === 2 && item.metrics !== undefined && item.metrics !== null) {
      throw new Error('Result v2 不应包含节点结构化指标');
    }
    const hasMetrics = item.metrics !== undefined && item.metrics !== null;
    const snapshotMetrics = schemaVersion >= 3 ? parseSnapshotMetrics(item.metrics) : null;
    const outputMetrics = schemaVersion >= 7 ? parseOutputWritesMetrics(item.metrics) : null;
    const metrics = snapshotMetrics ?? outputMetrics;
    const snapshotNode = item.nodeType === 'JDBC_SNAPSHOT_SYNC_OUTPUT'
      || item.nodeType === 'MODEL_SNAPSHOT_SYNC_OUTPUT';
    if (schemaVersion >= 3 && hasMetrics && !metrics) {
      throw new Error(`执行结果节点 ${index + 1} 包含未知指标类型`);
    }
    if (item.state === 'FAILED' && metrics?.kind === 'SNAPSHOT_SYNC') {
      throw new Error(`执行结果节点 ${index + 1} 失败时不能包含 Snapshot Sync 指标`);
    }
    if (!snapshotNode && metrics?.kind === 'SNAPSHOT_SYNC') {
      throw new Error(`执行结果节点 ${index + 1} 不是 Snapshot Sync 节点`);
    }
    const ordinaryOutputNode = item.nodeType === 'JDBC_OUTPUT' || item.nodeType === 'MODEL_OUTPUT'
      || item.nodeType === 'FILE_OUTPUT' || item.nodeType === 'KAFKA_OUTPUT';
    if (metrics?.kind === 'OUTPUT_WRITES' && !ordinaryOutputNode) {
      throw new Error(`执行结果节点 ${index + 1} 不是普通 Output 节点`);
    }
    if (schemaVersion >= 3 && item.state === 'SUCCESS' && snapshotNode && !metrics) {
      throw new Error(`执行结果节点 ${index + 1} 缺少 Snapshot Sync 指标`);
    }
    if (metrics?.kind === 'SNAPSHOT_SYNC'
      && item.rowsWritten !== metrics.insertedRows + metrics.updatedRows + metrics.deletedRows) {
      throw new Error(`执行结果节点 ${index + 1} 的写入行数与指标不一致`);
    }
    if (metrics?.kind === 'OUTPUT_WRITES') {
      const failedWrite = metrics.writes.find((write) => write.state === 'FAILED');
      if (item.state === 'SUCCESS' && failedWrite || item.state === 'FAILED' && !failedWrite) {
        throw new Error(`执行结果节点 ${index + 1} 的状态与逐写入指标不一致`);
      }
      const successfulRows = metrics.writes.filter((write) => write.state === 'SUCCESS')
        .map((write) => write.affectedRows);
      const expectedRows = successfulRows.some((rows) => rows === null)
        ? null : successfulRows.reduce<number>((total, rows) => total + (rows ?? 0), 0);
      if (item.rowsWritten !== expectedRows) {
        throw new Error(`执行结果节点 ${index + 1} 的写入行数与逐写入指标不一致`);
      }
    }
    return {
      nodeId: item.nodeId,
      nodeType: item.nodeType,
      nodeName: item.nodeName,
      state: item.state,
      rowsWritten: item.rowsWritten as number | null,
      metrics,
    };
  });
};

const parseQualitySample = (value: unknown, required: boolean): QualitySampleResult | null => {
  if (value === undefined || value === null) {
    if (required) throw new Error('模型质检规则缺少样本状态');
    return null;
  }
  if (!isRecord(value) || typeof value.status !== 'string'
    || !qualitySampleStatuses.has(value.status as QualitySampleStatus)
    || !Array.isArray(value.columns)) {
    throw new Error('模型质检样本描述格式无效');
  }
  const columns = value.columns.map((column): QualitySampleColumn => {
    if (!isRecord(column) || column.fieldId !== null && typeof column.fieldId !== 'string'
      || typeof column.code !== 'string' || typeof column.name !== 'string'
      || !isRecord(column.type) || typeof column.type.type !== 'string'
      || typeof column.primaryKey !== 'boolean' || typeof column.diagnostic !== 'boolean') {
      throw new Error('模型质检样本字段格式无效');
    }
    return column as unknown as QualitySampleColumn;
  });
  const available = value.status === 'AVAILABLE';
  if (available && (!nonNegativeInteger(value.sampledRows) || value.sampledRows < 1
    || !nonNegativeInteger(value.violationRows) || value.violationRows < value.sampledRows
    || typeof value.truncated !== 'boolean' || !nonNegativeInteger(value.sizeBytes)
    || typeof value.sha256 !== 'string' || !/^[0-9a-f]{64}$/.test(value.sha256)
    || typeof value.rowLocatable !== 'boolean' || columns.length === 0)
    || !available && (value.sampledRows !== null || value.violationRows !== null
      || value.truncated !== null || value.sizeBytes !== null || value.sha256 !== null
      || value.rowLocatable !== null || columns.length > 0)) {
    throw new Error('模型质检样本状态字段无效');
  }
  return {
    status: value.status as QualitySampleStatus,
    sampledRows: value.sampledRows as number | null,
    violationRows: value.violationRows as number | null,
    truncated: value.truncated as boolean | null,
    sizeBytes: value.sizeBytes as number | null,
    sha256: value.sha256 as string | null,
    rowLocatable: value.rowLocatable as boolean | null,
    columns,
  };
};

const parseQualityMetric = (value: unknown): QualityRuleMetric => {
  if (!isRecord(value)) throw new Error('质量规则指标格式无效');
  if (value.kind === 'VIOLATION') {
    if (!nonNegativeInteger(value.violationCount)
      || !nonNegativeNumber(value.violationPercent)
      || value.toleranceMetric !== 'COUNT' && value.toleranceMetric !== 'PERCENT'
      || !nonNegativeNumber(value.toleranceValue)) {
      throw new Error('质量异常指标格式无效');
    }
    return {
      kind: 'VIOLATION',
      violationCount: value.violationCount,
      violationPercent: value.violationPercent,
      toleranceMetric: value.toleranceMetric,
      toleranceValue: value.toleranceValue,
    };
  }
  if (value.kind === 'ROW_COUNT') {
    if (!nonNegativeInteger(value.actualRows) || !nonNegativeInteger(value.minimumRows)) {
      throw new Error('质量行数指标格式无效');
    }
    return { kind: 'ROW_COUNT', actualRows: value.actualRows, minimumRows: value.minimumRows };
  }
  if (value.kind === 'FRESHNESS') {
    if (value.maximumValue !== null && typeof value.maximumValue !== 'string'
      || value.actualDelayMinutes !== null && !nonNegativeInteger(value.actualDelayMinutes)
      || !nonNegativeInteger(value.maximumDelayMinutes)) {
      throw new Error('质量新鲜度指标格式无效');
    }
    return {
      kind: 'FRESHNESS',
      maximumValue: value.maximumValue as string | null,
      actualDelayMinutes: value.actualDelayMinutes as number | null,
      maximumDelayMinutes: value.maximumDelayMinutes,
    };
  }
  throw new Error('质量规则包含未知指标类型');
};

const parseQualityResult = (
  value: unknown,
  schemaVersion: Exclude<SupportedTaskExecutionSchemaVersion, 2 | 3>,
): ModelQualityExecutionResult => {
  if (!isRecord(value)
    || !Array.isArray(value.ruleResults)
    || !Array.isArray(value.skippedRuleResults)) {
    throw new Error('模型质检结果汇总格式无效');
  }
  const ruleResults = value.ruleResults.map((item, index): QualityRuleExecutionResult => {
    if (!isRecord(item)
      || typeof item.ruleId !== 'string'
      || typeof item.ruleName !== 'string'
      || typeof item.ruleType !== 'string'
      || !qualityRuleTypes.has(item.ruleType as ModelQualityRuleType)
      || typeof item.severity !== 'string'
      || !qualitySeverities.has(item.severity as ModelQualityRuleSeverity)
      || item.state !== 'PASSED' && item.state !== 'FAILED'
      || !nonNegativeInteger(item.durationMs)) {
      throw new Error(`模型质检规则结果 ${index + 1} 格式无效`);
    }
    return {
      ruleId: item.ruleId,
      ruleName: item.ruleName,
      ruleType: item.ruleType as ModelQualityRuleType,
      severity: item.severity as ModelQualityRuleSeverity,
      state: item.state,
      durationMs: item.durationMs,
      metric: parseQualityMetric(item.metric),
      sample: parseQualitySample(item.sample, schemaVersion >= 5),
    };
  });
  const skippedRuleResults = value.skippedRuleResults.map((item, index): QualitySkippedRuleResult => {
    if (!isRecord(item)
      || typeof item.id !== 'string'
      || typeof item.name !== 'string'
      || typeof item.type !== 'string'
      || !qualityRuleTypes.has(item.type as ModelQualityRuleType)
      || typeof item.reasonCode !== 'string'
      || typeof item.reason !== 'string') {
      throw new Error(`模型质检跳过规则 ${index + 1} 格式无效`);
    }
    return {
      id: item.id,
      name: item.name,
      type: item.type as ModelQualityRuleType,
      reasonCode: item.reasonCode,
      reason: item.reason,
    };
  });
  if (value.conclusion === null) {
    if (value.totalRules !== null || value.passedRules !== null || value.failedRules !== null
      || value.skippedRules !== null || value.checkedRows !== null && !nonNegativeInteger(value.checkedRows)) {
      throw new Error('模型质检技术失败汇总格式无效');
    }
    const failure = value.technicalFailure;
    if (failure !== null && failure !== undefined && (!isRecord(failure)
      || typeof failure.ruleId !== 'string' || typeof failure.ruleName !== 'string'
      || typeof failure.ruleType !== 'string'
      || !qualityRuleTypes.has(failure.ruleType as ModelQualityRuleType)
      || typeof failure.severity !== 'string'
      || !qualitySeverities.has(failure.severity as ModelQualityRuleSeverity)
      || !nonNegativeInteger(failure.durationMs) || typeof failure.diagnosticId !== 'string')) {
      throw new Error('模型质检技术失败规则上下文无效');
    }
    if (ruleResults.length > 0 && failure == null) {
      throw new Error('模型质检已完成规则缺少失败规则上下文');
    }
    return {
      conclusion: null,
      totalRules: null,
      passedRules: null,
      failedRules: null,
      skippedRules: null,
      checkedRows: value.checkedRows as number | null,
      ruleResults,
      skippedRuleResults,
      technicalFailure: failure == null ? null : {
        ruleId: failure.ruleId as string,
        ruleName: failure.ruleName as string,
        ruleType: failure.ruleType as ModelQualityRuleType,
        severity: failure.severity as ModelQualityRuleSeverity,
        durationMs: failure.durationMs as number,
        diagnosticId: failure.diagnosticId as string,
      },
    };
  }
  if (value.conclusion !== 'PASSED' && value.conclusion !== 'FAILED'
    || !nonNegativeInteger(value.totalRules) || !nonNegativeInteger(value.passedRules)
    || !nonNegativeInteger(value.failedRules) || !nonNegativeInteger(value.skippedRules)
    || !nonNegativeInteger(value.checkedRows) || value.technicalFailure !== null
    && value.technicalFailure !== undefined) {
    throw new Error('模型质检成功汇总格式无效');
  }
  if (value.totalRules !== value.passedRules + value.failedRules + value.skippedRules
    || ruleResults.length !== value.passedRules + value.failedRules
    || skippedRuleResults.length !== value.skippedRules
    || value.conclusion === 'PASSED' && value.failedRules !== 0
    || value.conclusion === 'FAILED' && value.failedRules === 0) {
    throw new Error('模型质检结果不满足汇总恒等关系');
  }
  return {
    conclusion: value.conclusion,
    totalRules: value.totalRules,
    passedRules: value.passedRules,
    failedRules: value.failedRules,
    skippedRules: value.skippedRules,
    checkedRows: value.checkedRows,
    ruleResults,
    skippedRuleResults,
    technicalFailure: null,
  };
};

export const parseTaskExecutionResultArtifact = (value: unknown): TaskExecutionResultArtifact => {
  if (!isRecord(value) || value.schemaVersion !== 2 && value.schemaVersion !== 3
    && value.schemaVersion !== 4 && value.schemaVersion !== 5
    && value.schemaVersion !== 6 && value.schemaVersion !== 7 && value.schemaVersion !== 8
    && value.schemaVersion !== 9 && value.schemaVersion !== 10 && value.schemaVersion !== 11) {
    throw new Error('执行结果制品版本或结构不受支持');
  }
  const schemaVersion = value.schemaVersion;
  if (schemaVersion < 4) {
    if (value.userJobObservability !== null && value.userJobObservability !== undefined) {
      throw new Error('Result v2～v5 不能包含用户作业观测载荷');
    }
    return {
      schemaVersion,
      taskType: 'SPARK_CANVAS',
      nodeResults: parseNodeResults(value.nodeResults, schemaVersion),
      qualityResult: null,
      userJobObservability: null,
    };
  }
  if (schemaVersion !== 4 && schemaVersion !== 5
    && schemaVersion !== 6 && schemaVersion !== 7 && schemaVersion !== 8
    && schemaVersion !== 9 && schemaVersion !== 10 && schemaVersion !== 11) {
    throw new Error('模型质检结果版本无效');
  }
  if (value.taskType === 'SPARK_MODEL_QUALITY') {
    if (schemaVersion >= 6 && value.userJobObservability !== null
      && value.userJobObservability !== undefined) {
      throw new Error('模型质检结果不能包含用户作业观测载荷');
    }
    if (value.state !== 'SUCCESS' && value.state !== 'FAILED'
      && value.state !== 'TIMED_OUT' && value.state !== 'CANCELLED') {
      throw new Error('模型质检结果终态无效');
    }
    if (!Array.isArray(value.nodeResults) || value.nodeResults.length !== 0
      || value.qualityResult == null) {
      throw new Error('模型质检结果载荷与任务类型不一致');
    }
    const qualityResult = parseQualityResult(value.qualityResult, schemaVersion);
    if (value.state === 'SUCCESS' && qualityResult.conclusion === null
      || value.state !== 'SUCCESS' && qualityResult.conclusion !== null) {
      throw new Error('模型质检终态与质量结果不一致');
    }
    return {
      schemaVersion,
      taskType: 'SPARK_MODEL_QUALITY',
      nodeResults: [],
      qualityResult,
      userJobObservability: null,
    };
  }
  if (value.taskType === 'SPARK_JAR' || value.taskType === 'SPARK_STREAMING_JAR') {
    if (!Array.isArray(value.nodeResults) || value.nodeResults.length !== 0
      || value.qualityResult !== null && value.qualityResult !== undefined
      || schemaVersion < 6 && value.userJobObservability !== null
      && value.userJobObservability !== undefined
      || value.taskType === 'SPARK_STREAMING_JAR' && schemaVersion < 11) {
      throw new Error('Spark JAR 结果载荷与任务类型不一致');
    }
    if (value.taskType === 'SPARK_JAR'
      && schemaVersion >= 8 && value.state === 'SUCCESS' && !isRecord(value.lineage)) {
      throw new Error('Spark JAR v8 成功结果缺少运行血缘证据');
    }
    return {
      schemaVersion,
      taskType: value.taskType,
      nodeResults: [],
      qualityResult: null,
      userJobObservability: schemaVersion >= 6
        ? parseUserJobObservability(value.userJobObservability)
        : null,
    };
  }
  if (value.taskType !== 'SPARK_CANVAS' || value.qualityResult !== null && value.qualityResult !== undefined) {
    throw new Error('Canvas 结果载荷与任务类型不一致');
  }
  if (schemaVersion >= 6 && value.userJobObservability !== null
    && value.userJobObservability !== undefined) {
    throw new Error('Canvas结果不能包含用户作业观测载荷');
  }
  return {
    schemaVersion,
    taskType: 'SPARK_CANVAS',
    nodeResults: parseNodeResults(value.nodeResults, schemaVersion),
    qualityResult: null,
    userJobObservability: null,
  };
};
