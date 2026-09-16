import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeTrackDetectIncidents = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.TrackDetectIncidents>,
) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName || !configuration.outputTableName) {
    return '请选择轨迹事件表并设置事件输出';
  }
  return `${configuration.sourceTableName} → ${configuration.outputTableName}`
    + ((configuration.conditionWindows?.length ?? 0) > 0 ? ` · ${configuration.conditionWindows?.length} 个窗口指标` : '')
    + ((configuration.conditionScalars?.length ?? 0) > 0 ? ` · ${configuration.conditionScalars?.length} 个轨迹标量` : '')
    + ((configuration.conditionWindows ?? []).some(window => window.source === 'TRACK_DISTANCE') ? ' · 含轨迹距离（米）' : '')
    + ((configuration.conditionWindows ?? []).some(window => window.source === 'TRACK_SPEED') ? ' · 含轨迹速度（米/秒）' : '')
    + ((configuration.conditionWindows ?? []).some(window => window.source === 'TRACK_ACCELERATION') ? ' · 含轨迹加速度（米/秒²）' : '')
    + ` · ${configuration.resultMode === 'ALL_EVENTS' ? '全部并标记' : '仅事件'}`
    + ` · ${configuration.endCondition ? '含结束条件'
      : configuration.incidentSemantics === 'CONDITION_LIFECYCLE' ? '条件不成立即结束' : '旧版：片段末结束'}`;
};
