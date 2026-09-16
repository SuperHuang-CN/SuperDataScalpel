import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeJdbcIncrementalInput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.JdbcIncrementalInput>,
) => {
  const { tableName, incrementalTimeColumn, outputTableName, triggerIntervalSeconds } = data.configuration;
  if (!tableName || !incrementalTimeColumn || !outputTableName) return '请配置 JDBC 增量输入';
  const source = data.summary?.kind === 'JDBC' ? data.summary.dataSourceName : '未知数据源';
  return `${source} · ${tableName}.${incrementalTimeColumn} → ${outputTableName} · ${triggerIntervalSeconds ?? 60}s`;
};
