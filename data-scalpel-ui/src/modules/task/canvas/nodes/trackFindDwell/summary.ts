import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";
import { dwellFeatureMode, dwellOutputLabel } from "./rangeOptions";

export const summarizeTrackFindDwell = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.TrackFindDwell>,
) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName || !configuration.outputTableName) {
    return '请选择轨迹事件表并设置驻留输出';
  }
  return `${configuration.sourceTableName} → ${configuration.outputTableName}`
    + ` · ${dwellOutputLabel(configuration)}`
    + (configuration.dwellSemantics === 'REFERENCE_CENTER' && dwellFeatureMode(configuration.rangeOptions?.resultMode)
      ? ' · 保留原字段并标记' : ` · ${configuration.summaryStatistics.length} 项汇总`);
};
