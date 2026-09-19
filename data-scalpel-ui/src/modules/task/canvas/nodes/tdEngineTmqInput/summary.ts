import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeTdEngineTmqInput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.TdEngineTmqInput>,
) => {
  const { dataSourceId, topicName, supertableName, outputTableName } = data.configuration;
  if (!dataSourceId || !topicName || !supertableName || !outputTableName) return '请配置 TDengine TMQ 输入';
  return data.summary?.kind === 'TDENGINE_TMQ'
    ? `${data.summary.dataSourceName} · ${topicName} · ${supertableName} → ${outputTableName}`
    : `${topicName} · ${supertableName} → ${outputTableName}`;
};
