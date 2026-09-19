import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeSpatialDensity = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialDensity>,
) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName || !configuration.outputTableName) {
    return '请选择投影 Point 表并设置密度输出';
  }
  return `${configuration.sourceTableName} → ${configuration.outputTableName}`
    + ` · ${configuration.weighting === 'KERNEL' ? 'Kernel' : 'Uniform'}`
    + ` · ${configuration.binShape === 'HEXAGON' ? '六边形' : '方格'}`
    + ` · ${configuration.fields.length + 1} 个密度字段`
    + `${configuration.temporalSlicing ? ' · 时间切片' : ''}`;
};
