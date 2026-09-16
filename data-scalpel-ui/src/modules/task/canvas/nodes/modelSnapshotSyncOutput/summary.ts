import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";
import { snapshotDeleteSummary } from '../operationSummary';

export const summarizeModelSnapshotSyncOutput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.ModelSnapshotSyncOutput>,
) => {
  const { sourceTableName, targetModelId, keyColumns, deletePolicy } = data.configuration;
  if (!sourceTableName || !targetModelId || keyColumns.length === 0) {
    return '请选择目标模型并配置 Key';
  }
  const target = data.summary?.kind === 'MODEL'
    ? `${data.summary.modelName} · ${data.summary.modelCode}`
    : `模型 ${targetModelId}`;
  return `${sourceTableName} ⇄ ${target} · ${keyColumns.length} 个 Key · ${snapshotDeleteSummary(deletePolicy.action)}`;
};
