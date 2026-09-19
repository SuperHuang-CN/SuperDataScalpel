import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";
import { boundaryLabels, usesMethodPath, usesOrderedReconstruction, usesSplitExpression } from "./reconstruction";
import { usesAreaGeometry } from "./areaGeometry";

export const summarizeTrackReconstruct = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.TrackReconstruct>,
) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName || !configuration.outputTableName) {
    return '请选择轨迹事件表并设置输出表';
  }
  const boundaryCount = [
    configuration.boundaries.maximumTimeGap,
    configuration.boundaries.maximumDistanceGap,
    configuration.boundaries.fixedTimeBoundary,
  ].filter((value) => value != null).length;
  return `${configuration.sourceTableName} → ${configuration.outputTableName}`
    + ` · ${configuration.trackIdColumns.length} 个轨迹标识`
    + (usesAreaGeometry(configuration.reconstruction) ? ' · 面轨迹'
      : usesMethodPath(configuration.reconstruction) ? ' · 多部件路径' : '')
    + (usesAreaGeometry(configuration.reconstruction) && configuration.reconstruction?.areaGeometry?.bufferMode === 'EXPRESSION'
      ? ` · ${configuration.reconstruction.areaGeometry.windowBindings?.length ?? 0} 个缓冲窗口` : '')
    + ` · ${boundaryCount === 0 ? '不拆分' : `${boundaryCount} 项边界`}`
    + (usesOrderedReconstruction(configuration.reconstruction)
      ? ` · ${boundaryLabels[configuration.reconstruction?.splitBoundaryOption ?? 'GAP']}${usesSplitExpression(configuration.reconstruction) ? ' · 表达式拆分' : ''}` : ' · 旧版')
    + ` · ${configuration.summaryStatistics.length} 项汇总`;
};
