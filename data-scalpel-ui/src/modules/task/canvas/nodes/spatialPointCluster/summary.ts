import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeSpatialPointCluster = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialPointCluster>,
) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName || !configuration.outputTableName) {
    return '请选择点表并设置聚类输出';
  }
  return `${configuration.sourceTableName} → ${configuration.outputTableName}`
    + ` · ${configuration.parameters.algorithm}`
    + ` · 最少 ${configuration.parameters.minimumFeatures} 个要素`
    + (configuration.parameters.algorithm === 'HDBSCAN' ? configuration.hdbscan ? ' · 4 项诊断' : ' · 诊断待配置' : '')
    + (configuration.parameters.algorithm === 'DBSCAN' && configuration.dbscan?.mode === 'LINEAR' ? ' · Linear 时空' : '');
};
