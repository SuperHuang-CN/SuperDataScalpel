import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";
import { h3SizeSummary } from "./h3";

export const summarizeSpatialBinAggregate = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialBinAggregate>,
) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName || !configuration.outputTableName) {
    return '请选择点表并设置格网输出';
  }
  return `${configuration.sourceTableName} → ${configuration.outputTableName}`
    + ` · ${configuration.binShape === 'H3' ? h3SizeSummary(configuration) : configuration.binShape === 'HEXAGON' ? '六边形' : '方格'}`
    + `${configuration.binShape === 'HEXAGON'
      ? configuration.binSizeSemantics === 'HEXAGON_FLAT_TO_FLAT' ? '（对边距离）' : '（旧版边长）' : ''}`
    + `${configuration.binShape !== 'H3' && configuration.planarGrid ? configuration.planarGrid.extent?.mode === 'EXPLICIT_BOUNDS' ? ' · 业务范围' : ' · 指定原点' : ''}`
    + ` · ${configuration.statistics.length} 个统计项`
    + `${configuration.groupSummary ? ' · 分组' : ''}`
    + `${configuration.temporalSlicing ? ' · 时间切片' : ''}`;
};
