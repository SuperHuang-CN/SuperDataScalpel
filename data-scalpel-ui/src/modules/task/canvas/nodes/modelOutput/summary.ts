import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeModelOutput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.ModelOutput>,
) => {
  const writes = data.configuration.writes ?? [];
  const first = writes[0];
  if (!first) return '请至少配置一条模型写入';
  const target = data.summary?.kind === 'MODEL'
    ? `${data.summary.modelName} · ${data.summary.modelCode}`
    : `模型 ${first.targetModelId || '待选择'}`;
  const preview = `${first.sourceTableName || '待选择来源'} → ${target} (${first.writeMode ?? '待设置'})`;
  return writes.length > 1 ? `${preview}，另 ${writes.length - 1} 条写入` : preview;
};
