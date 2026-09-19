import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeSpatialNearest = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialNearest>,
) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName || !configuration.candidateTableName
    || !configuration.outputTableName) {
    return '请选择来源、候选 Geometry 和输出表';
  }
  return `${configuration.sourceTableName} → ${configuration.candidateTableName}`
    + ` · TOP ${configuration.nearestCount} · ${configuration.distanceMethod ?? '待选方法'}`
    + ` · ${configuration.matching != null && configuration.matching.semantics !== 'LEGACY_KNN' ? '真实距离' : '旧版 KNN'}`
    + ` → ${configuration.outputTableName}`;
};
