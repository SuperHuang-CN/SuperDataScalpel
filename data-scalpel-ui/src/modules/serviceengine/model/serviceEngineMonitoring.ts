export type JdbcPoolMonitorStatus = 'AVAILABLE' | 'NOT_LOADED' | 'UNSUPPORTED';

export interface JdbcPoolSummary {
  poolName: string | null;
  maximum: number | null;
  total: number | null;
  active: number | null;
  idle: number | null;
  waiting: number | null;
  utilizationPercent: number | null;
  acquisitionTimeoutCount: number | null;
  lastSaturationAt: string | null;
  executingConnections: number | null;
  idleInTransactionConnections: number | null;
  borrowedIdleConnections: number | null;
  longRunningQueryCount: number | null;
  longHeldConnectionCount: number | null;
}

export interface DataSourcePoolEntry {
  dataSourceId: string;
  status: JdbcPoolMonitorStatus;
  pool: JdbcPoolSummary | null;
}

export interface DataSourcePoolSummaries {
  engineCode: string;
  capturedAt: string;
  dataSources: DataSourcePoolEntry[];
}

export interface JdbcSqlConsumer {
  fingerprint: string | null;
  sqlPreview: string | null;
  operation: string | null;
  connectionCount: number | null;
  totalHeldMs: number | null;
  maxHeldMs: number | null;
  maxExecutingMs: number | null;
}

export interface JdbcActiveConnection {
  connectionId: string;
  state: string;
  borrowedAt: string | null;
  heldMs: number | null;
  threadName: string | null;
  transactionActive: boolean;
  fingerprint: string | null;
  sqlPreview: string | null;
  operation: string | null;
  sqlStartedAt: string | null;
  executingMs: number | null;
  lastSqlPreview: string | null;
  apiId: string | null;
  apiPath: string | null;
  executionId: string | null;
  longRunning: boolean;
  longHeld: boolean;
}

export interface JdbcRecentSql {
  fingerprint: string;
  sqlPreview: string | null;
  operation: string | null;
  executions: number | null;
  failures: number | null;
  totalDurationMs: number | null;
  maxDurationMs: number | null;
  lastExecutedAt: string | null;
}

export interface JdbcSaturationIncident {
  occurredAt: string;
  reason: string;
  requestedSqlPreview: string | null;
  requestedExecutionId: string | null;
  pool: JdbcPoolSummary | null;
  topConsumers: JdbcSqlConsumer[];
}

export interface DataSourcePoolMonitor extends DataSourcePoolEntry {
  engineCode: string;
  capturedAt: string;
  topConsumers: JdbcSqlConsumer[];
  activeConnections: JdbcActiveConnection[];
  recentSql: JdbcRecentSql[];
  incidents: JdbcSaturationIncident[];
}

export const poolUnavailableLabels: Record<Exclude<JdbcPoolMonitorStatus, 'AVAILABLE'>, string> = {
  NOT_LOADED: '运行时连接池未加载',
  UNSUPPORTED: '监控未启用或连接池不支持',
};

export const monitorNumber = (value: number | null | undefined): string => value == null ? '—' : value.toLocaleString('zh-CN');
export const monitorDuration = (value: number | null | undefined): string => (
  value == null ? '—' : value < 1000 ? `${value} ms` : `${(value / 1000).toFixed(2)} s`
);

export const monitorTime = (value: string | null | undefined): string => {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium', timeStyle: 'medium', hour12: false,
  }).format(date);
};
