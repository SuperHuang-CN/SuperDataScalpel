import { CanvasNodeType } from '../../canvasTypes';
import {
  NodeBadge,
  NodeBadges,
  NodeContent,
  NodeEmpty,
  NodeFlow,
  NodePreviewList,
} from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';
import { spatialDistanceUnitLabels } from '../spatialUnits';

const temporalUnitLabels = {
  MILLISECONDS: '毫秒',
  SECONDS: '秒',
  MINUTES: '分钟',
  HOURS: '小时',
  DAYS: '天',
  WEEKS: '周',
  MONTHS: '月',
  YEARS: '年',
} as const;

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.TraceProximityEvents>) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName) {
    return <NodeEmpty>请选择带时间的 Point 轨迹观测表</NodeEmpty>;
  }
  const interestSummary = configuration.interestSource === 'TABLE'
    ? configuration.entitiesOfInterestTableName || '待选择起始表'
    : `${configuration.entitiesOfInterest.length} 个起始实体`;
  const spatialUnit = configuration.spatialSearchDistanceUnit
    ? spatialDistanceUnitLabels[configuration.spatialSearchDistanceUnit]
    : '';
  const temporalUnit = configuration.temporalSearchDistanceUnit
    ? temporalUnitLabels[configuration.temporalSearchDistanceUnit]
    : '';
  const previews = [{
    key: 'proximity',
    label: '邻近',
    value: configuration.distanceMethod === 'GEODESIC' ? '测地线' : '平面',
    meta: `空间 ${configuration.spatialSearchDistance ?? '?'} ${spatialUnit} · 时间 ${configuration.temporalSearchDistance ?? '?'} ${temporalUnit}`,
  }, {
    key: 'interest',
    label: '起始',
    value: interestSummary,
    meta: `最多传播 ${configuration.maxTraceDepth ?? '?'} 层`,
  }];
  return <NodeContent variant="spatial">
    <NodeFlow source={configuration.sourceTableName} operation="邻近传播"
      target={configuration.outputTableName || '待设置事件表'} />
    <NodePreviewList items={previews} total={previews.length} />
    <NodeBadges>
      <NodeBadge tone="spatial">首次接触</NodeBadge>
      <NodeBadge>{configuration.attributeMatchColumns.length > 0
        ? `${configuration.attributeMatchColumns.length} 个同值字段` : '无属性限制'}</NodeBadge>
      {configuration.includeTracks && <NodeBadge tone="info">含后续轨迹</NodeBadge>}
    </NodeBadges>
  </NodeContent>;
};

export const traceProximityEventsCanvasView: CanvasNodeCanvasView<
  typeof CanvasNodeType.TraceProximityEvents
> = {
  resolveSize: configuration => resolvedNodeSize({
    width: 392,
    maxHeight: 232,
    configured: Boolean(configuration.sourceTableName),
    listCount: 2,
  }),
  Body: body,
};
