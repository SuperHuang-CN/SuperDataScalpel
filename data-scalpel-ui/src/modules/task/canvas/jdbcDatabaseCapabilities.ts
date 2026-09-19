import type { DataSourceType } from '../../datasource';
import type { CanvasExecutionMode, JdbcWriteMode } from './canvasTypes';

const databaseLabels: Partial<Record<DataSourceType, string>> = {
  POSTGRESQL: 'PostgreSQL',
  HIGHGO: 'HighGo',
  MYSQL: 'MySQL',
  ORACLE: 'Oracle',
  SQL_SERVER: 'SQL Server',
  CLICKHOUSE: 'ClickHouse',
  DAMENG: '达梦',
  OPENGAUSS: 'openGauss',
  KINGBASE: '人大金仓',
  TDENGINE_WEBSOCKET: 'TDengine',
  TDENGINE_RESTFUL: 'TDengine',
};

const upsertDatabases = new Set<DataSourceType>([
  'POSTGRESQL', 'HIGHGO', 'MYSQL', 'OPENGAUSS', 'KINGBASE', 'DAMENG', 'ORACLE', 'SQL_SERVER',
]);

const tdEngineDatabases = new Set<DataSourceType>([
  'TDENGINE_WEBSOCKET',
  'TDENGINE_RESTFUL',
]);

export const jdbcWriteModeUnavailableReason = (
  databaseType: DataSourceType | undefined,
  writeMode: JdbcWriteMode,
  executionMode: CanvasExecutionMode,
): string | null => {
  if (writeMode === 'OVERWRITE' && executionMode === 'STREAMING') {
    return '实时模式不支持';
  }
  if (!databaseType) return null;
  const database = databaseLabels[databaseType] ?? databaseType;
  if (tdEngineDatabases.has(databaseType)) {
    return `${database} 不支持普通 JDBC 输出`;
  }
  if (writeMode === 'UPSERT' && !upsertDatabases.has(databaseType)) {
    return `${database} 暂不支持 UPSERT`;
  }
  return null;
};
