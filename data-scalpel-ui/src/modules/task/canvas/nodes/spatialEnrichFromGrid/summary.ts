import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeSpatialEnrichFromGrid = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialEnrichFromGrid>,
) => {
  const configuration = data.configuration;
  if (!configuration.pointTableName || !configuration.gridTableName || !configuration.outputTableName) {
    return '请选择 Point 表、多变量格网和输出表';
  }
  return `${configuration.pointTableName} + ${configuration.gridTableName}`
    + ` → ${configuration.outputTableName} · ${configuration.enrichFields.length} 个格网字段`;
};
