import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeTraceProximityEvents = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.TraceProximityEvents>,
) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName || !configuration.outputTableName) {
    return '请选择轨迹观测表并设置事件输出';
  }
  const interestCount = configuration.interestSource === 'ENTITY_IDS'
    ? configuration.entitiesOfInterest.length
    : configuration.entitiesOfInterestTableName ? 1 : 0;
  return `${configuration.sourceTableName} → ${configuration.outputTableName}`
    + ` · ${configuration.distanceMethod === 'GEODESIC' ? '测地' : '平面'}邻近传播`
    + ` · 最深 ${configuration.maxTraceDepth ?? '待设置'} 层`
    + ` · ${configuration.interestSource === 'TABLE'
      ? '起始表'
      : `${interestCount} 个起始实体`}`
    + `${configuration.attributeMatchColumns.length > 0
      ? ` · ${configuration.attributeMatchColumns.length} 个同值约束` : ''}`
    + `${configuration.includeTracks ? ' · 输出后续轨迹' : ''}`;
};
