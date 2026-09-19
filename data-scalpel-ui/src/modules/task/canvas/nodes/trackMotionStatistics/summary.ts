import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeTrackMotionStatistics = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.TrackMotionStatistics>,
) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName || !configuration.outputTableName) {
    return '请选择轨迹事件表并设置输出表';
  }
  const kinds = [...new Set(configuration.metrics.map((metric) => metric.kind))];
  if (configuration.motionSemantics === 'OBSERVATION_WINDOW') {
    return `${configuration.sourceTableName} → ${configuration.outputTableName} · 窗口 ${configuration.windowOptions?.observationCount ?? '待配置'} 个观测 · ${configuration.windowOptions?.statistics.length ?? 0} 项指标`;
  }
  return `${configuration.sourceTableName} → ${configuration.outputTableName}`
    + ` · 旧版偏移 ${configuration.historyPoints} 点 · ${kinds.join('/') || '待配置指标'}`;
};
