import { CanvasNodeType } from '../../canvasTypes';
import {
  NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodeHeroMetric,
} from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SpatialHotSpots>) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName) return <NodeEmpty>请选择投影 Point 表</NodeEmpty>;
  return <NodeContent variant="spatial">
    <NodeFlow source={configuration.sourceTableName} operation="Gi* 热点"
      target={configuration.outputTableName || '待设置'} />
    <NodeHeroMetric value="-3…3" label="热点 / 冷点置信分级" />
    <NodeBadges>
      <NodeBadge tone="spatial">方格 {configuration.binSize} {configuration.binSizeUnit}</NodeBadge>
      <NodeBadge>邻域 {configuration.neighborhoodDistance} {configuration.neighborhoodDistanceUnit}</NodeBadge>
      <NodeBadge>{configuration.analysisSource === 'FIELD_SUM' ? '字段和' : '点数'}</NodeBadge>
      <NodeBadge tone={configuration.multipleTesting === 'FDR_BH' ? 'strong' : 'neutral'}>
        {configuration.multipleTesting === 'FDR_BH' ? 'FDR 校正' : '原始 p 值'}
      </NodeBadge>
      {configuration.temporalSlicing && <NodeBadge>时间切片</NodeBadge>}
    </NodeBadges>
  </NodeContent>;
};

export const spatialHotSpotsCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.SpatialHotSpots> = {
  resolveSize: (configuration) => resolvedNodeSize({
    width: 376,
    maxHeight: 224,
    configured: Boolean(configuration.sourceTableName),
    listCount: configuration.temporalSlicing ? 2 : 1,
  }),
  Body: body,
};
