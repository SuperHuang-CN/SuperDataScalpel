import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.TrackMotionStatistics>) => {
  const configuration = data.configuration;
  const windowMode = configuration.motionSemantics === 'OBSERVATION_WINDOW';
  const metrics = windowMode ? (configuration.windowOptions?.statistics ?? []).map(s => ({
    metricId: s.statisticId, kind: s.kind, outputColumnName: s.outputColumnName,
  })) : configuration.metrics;
  if (!configuration.sourceTableName) return <NodeEmpty>请选择轨迹事件表</NodeEmpty>;
  return <NodeContent variant="spatial">
    <NodeFlow source={configuration.sourceTableName} operation={windowMode
      ? `窗口 ${configuration.windowOptions?.observationCount ?? '待设置'} 个观测` : `偏移 ${configuration.historyPoints} 点（旧版）`}
      target={configuration.outputTableName || '待设置'} />
    <NodePreviewList
      items={metrics.map((metric) => ({
        key: metric.metricId,
        label: metric.kind,
        value: '→',
        meta: metric.outputColumnName || '待设置字段',
      }))}
      empty="尚未配置运动指标"
      moreLabel={(remaining) => `另 ${remaining} 项指标`}
    />
    <NodeBadges>
      <NodeBadge tone="spatial">{configuration.distanceMethod === 'GEODESIC' ? '测地线' : '平面'}</NodeBadge>
      <NodeBadge>{metrics.length} 项指标</NodeBadge>
      <NodeBadge>{configuration.trackIdColumns.length} 个轨迹标识</NodeBadge>
    </NodeBadges>
  </NodeContent>;
};

export const trackMotionStatisticsCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.TrackMotionStatistics> = {
  resolveSize: (configuration) => resolvedNodeSize({
    width: 360,
    maxHeight: 224,
    configured: Boolean(configuration.sourceTableName),
    listCount: configuration.motionSemantics === 'OBSERVATION_WINDOW' ? configuration.windowOptions?.statistics.length ?? 0 : configuration.metrics.length,
  }),
  Body: body,
};
