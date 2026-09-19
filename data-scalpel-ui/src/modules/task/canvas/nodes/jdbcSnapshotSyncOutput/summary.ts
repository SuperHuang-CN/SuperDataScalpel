import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";
import { snapshotDeleteSummary } from '../operationSummary';

export const summarizeJdbcSnapshotSyncOutput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.JdbcSnapshotSyncOutput>,
) => {
  const { sourceTableName, dataSourceId, targetTableName, keyColumns, deletePolicy } = data.configuration;
  if (!sourceTableName || !dataSourceId || !targetTableName || keyColumns.length === 0) {
    return '请选择同步目标并配置 Key';
  }
  const target = data.summary?.kind === 'JDBC'
    ? `${data.summary.dataSourceName} · ${data.summary.qualifiedTableName}`
    : `未知数据源 · ${targetTableName}`;
  return `${sourceTableName} ⇄ ${target} · ${keyColumns.length} 个 Key · ${snapshotDeleteSummary(deletePolicy.action)}`;
};
