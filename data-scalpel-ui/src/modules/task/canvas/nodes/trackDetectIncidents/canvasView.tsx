import { CanvasNodeType, type CanvasFilterCondition } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodeHeroMetric } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const conditionCount = (condition: CanvasFilterCondition | null): number => condition
  ? condition.kind === 'PREDICATE'
    ? 1
    : condition.children.reduce((sum, child) => sum + conditionCount(child), 0)
  : 0;

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.TrackDetectIncidents>) => {
  const configuration = data.configuration;
  const trackDistanceWindows = (configuration.conditionWindows ?? [])
    .filter(window => window.source === 'TRACK_DISTANCE').length;
  const trackSpeedWindows = (configuration.conditionWindows ?? [])
    .filter(window => window.source === 'TRACK_SPEED').length;
  const trackAccelerationWindows = (configuration.conditionWindows ?? [])
    .filter(window => window.source === 'TRACK_ACCELERATION').length;
  const pointCoordinateScalars = (configuration.conditionScalars ?? [])
    .filter(scalar => scalar.source === 'TRACK_POINT_X_AT' || scalar.source === 'TRACK_POINT_Y_AT').length;
  if (!configuration.sourceTableName) return <NodeEmpty>请选择轨迹事件表</NodeEmpty>;
  return <NodeContent variant="spatial">
    <NodeFlow source={configuration.sourceTableName} operation="事件检测"
      target={configuration.outputTableName || '待设置'} />
    <NodeHeroMetric value={conditionCount(configuration.startCondition)} label="开始条件" />
    <NodeBadges>
      <NodeBadge tone="spatial">{configuration.resultMode === 'ALL_EVENTS' ? '全部并标记' : '仅事件'}</NodeBadge>
      <NodeBadge>{configuration.endCondition ? `${conditionCount(configuration.endCondition)} 个结束条件`
        : configuration.incidentSemantics === 'CONDITION_LIFECYCLE' ? '条件不成立即结束' : '旧版：片段末结束'}</NodeBadge>
      <NodeBadge>{configuration.trackIdColumns.length} 个轨迹标识</NodeBadge>
      {(configuration.conditionWindows?.length ?? 0) > 0 && <NodeBadge>
        {configuration.conditionWindows?.length} 个窗口指标{configuration.incidentSemantics !== 'CONDITION_LIFECYCLE' ? '（未启用）' : ''}
      </NodeBadge>}
      {(configuration.conditionScalars?.length ?? 0) > 0 && <NodeBadge>
        {configuration.conditionScalars?.length} 个轨迹标量{configuration.incidentSemantics !== 'CONDITION_LIFECYCLE' ? '（未启用）' : ''}
      </NodeBadge>}
      {trackDistanceWindows > 0 && <NodeBadge tone="spatial">{trackDistanceWindows} 个轨迹距离窗口 · 米</NodeBadge>}
      {trackSpeedWindows > 0 && <NodeBadge tone="spatial">{trackSpeedWindows} 个轨迹速度窗口 · 米/秒</NodeBadge>}
      {trackAccelerationWindows > 0 && <NodeBadge tone="spatial">{trackAccelerationWindows} 个轨迹加速度窗口 · 米/秒²</NodeBadge>}
      {pointCoordinateScalars > 0 && <NodeBadge tone="spatial">{pointCoordinateScalars} 个 Point 坐标标量</NodeBadge>}
    </NodeBadges>
  </NodeContent>;
};

export const trackDetectIncidentsCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.TrackDetectIncidents> = {
  resolveSize: (configuration) => resolvedNodeSize({
    width: 360, maxHeight: 216, configured: Boolean(configuration.sourceTableName),
  }),
  Body: body,
};
