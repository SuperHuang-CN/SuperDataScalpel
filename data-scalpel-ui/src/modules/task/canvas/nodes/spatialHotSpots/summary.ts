import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeSpatialHotSpots = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialHotSpots>,
) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName || !configuration.outputTableName) {
    return '请选择投影 Point 表并设置热点输出';
  }
  return `${configuration.sourceTableName} → ${configuration.outputTableName}`
    + ` · ${configuration.analysisSource === 'FIELD_SUM' ? '字段和' : '点数'}`
    + ' · Getis-Ord Gi*'
    + ` · ${configuration.multipleTesting === 'FDR_BH' ? 'FDR' : '原始显著性'}`
    + `${configuration.temporalSlicing ? ' · 时间切片' : ''}`;
};
