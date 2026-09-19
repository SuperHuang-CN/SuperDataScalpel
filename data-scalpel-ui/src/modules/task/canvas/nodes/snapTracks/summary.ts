import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeSnapTracks = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SnapTracks>,
) => {
  const configuration = data.configuration;
  if (!configuration.pointTableName || !configuration.lineTableName
    || !configuration.outputTableName) {
    return '请选择轨迹点表、道路网络和输出表';
  }
  return `${configuration.pointTableName} + ${configuration.lineTableName}`
    + ` → ${configuration.outputTableName}`
    + ` · ${configuration.distanceMethod === 'GEODESIC' ? '测地线' : '平面'}路网匹配`
    + ` · ${configuration.trackIdColumns.length} 个轨迹标识`
    + `${configuration.directionMatching ? ' · 启用方向' : ''}`
    + `${configuration.lineFields.length > 0
      ? ` · ${configuration.lineFields.length} 个道路属性` : ''}`;
};
