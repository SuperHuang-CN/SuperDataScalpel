import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodeHeroMetric } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';
import { dwellFeatureMode, dwellOutputLabel } from './rangeOptions';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.TrackFindDwell>) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName) return <NodeEmpty>请选择轨迹事件表</NodeEmpty>;
  return <NodeContent variant="spatial">
    <NodeFlow source={configuration.sourceTableName} operation="驻留识别"
      target={configuration.outputTableName || '待设置'} />
    <NodeHeroMetric value={dwellOutputLabel(configuration)} label="驻留输出" />
    <NodeBadges>
      <NodeBadge tone="spatial">{configuration.dwellSemantics === 'REFERENCE_CENTER' ? '参考中心范围' : '相邻连段（旧版）'}</NodeBadge>
      <NodeBadge>{configuration.trackIdColumns.length} 个轨迹标识</NodeBadge>
      {configuration.dwellSemantics === 'REFERENCE_CENTER' && dwellFeatureMode(configuration.rangeOptions?.resultMode)
        ? <NodeBadge>保留原字段 + 驻留标记</NodeBadge>
        : <NodeBadge>{configuration.summaryStatistics.length} 项汇总</NodeBadge>}
    </NodeBadges>
  </NodeContent>;
};

export const trackFindDwellCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.TrackFindDwell> = {
  resolveSize: (configuration) => resolvedNodeSize({
    width: 352, maxHeight: 216, configured: Boolean(configuration.sourceTableName),
  }),
  Body: body,
};
